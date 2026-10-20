package com.connectify.service;

import com.connectify.config.IzipayWebProperties;
import com.connectify.dto.CartItem;
import com.connectify.dto.IzipayWebBuyer;
import com.connectify.dto.IzipayWebLine;
import com.connectify.entity.*;
import com.connectify.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class IzipayWebCheckoutService {
    private final IzipayWebProperties properties;
    private final IzipayWebAttemptRepository attempts;
    private final UserAccountRepository users;
    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final PurchaseRepository purchases;
    private final IzipayWebGateway gateway;
    private final IzipayWebSignature signatures;
    private final IzipayWebPaymentValidator validator;
    private final ObjectMapper mapper;

    public IzipayWebCheckoutService(IzipayWebProperties properties, IzipayWebAttemptRepository attempts,
                                    UserAccountRepository users, EventRepository events,
                                    TicketTypeRepository ticketTypes, PurchaseRepository purchases,
                                    IzipayWebGateway gateway, IzipayWebSignature signatures,
                                    IzipayWebPaymentValidator validator, ObjectMapper mapper) {
        this.properties = properties; this.attempts = attempts; this.users = users; this.events = events;
        this.ticketTypes = ticketTypes; this.purchases = purchases; this.gateway = gateway;
        this.signatures = signatures; this.validator = validator; this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> start(String email, IzipayWebBuyer buyer, List<CartItem> cart, String requestedMethod) {
        ready();
        String paymentMethod = normalizePaymentMethod(requestedMethod);
        // Serialize parallel start requests for the same customer, including separate browser tabs.
        UserAccount user = users.findLockedByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Cuenta no encontrada."));
        if (user.getRole() != Role.CLIENT || !user.isActive())
            throw new AccessDeniedException("Se requiere una cuenta Cliente activa.");
        Map<String, String> billing = buyer.billing(user.getEmail());
        if ("PAGO_PUSH".equals(paymentMethod) && !billing.get("phoneNumber").matches("[0-9]{9}"))
            throw new IllegalArgumentException("Para Plin, ingresa un teléfono de 9 dígitos.");
        List<IzipayWebLine> lines = snapshot(cart);
        String snapshotJson = json(lines);
        String billingJson = json(billing);
        String fingerprint = IzipayWebSignature.sha256(snapshotJson + "\n" + billingJson);
        List<IzipayWebAttempt> pending = attempts.findByBuyerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(email, "PENDING");
        if (!pending.isEmpty()) {
            IzipayWebAttempt previous = pending.get(0);
            previous.setStatus("ABANDONED");
            previous.setReviewReason("Orden reemplazada por el cliente al retomar el formulario de pago.");
        }
        if (!attempts.findByBuyerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(email, "REVIEW").isEmpty())
            throw new IllegalStateException("Tienes un pago confirmado pendiente de revisión. Contacta a soporte antes de volver a pagar.");

        IzipayWebAttempt attempt = new IzipayWebAttempt();
        attempt.setOrderNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 15).toUpperCase(Locale.ROOT));
        attempt.setTransactionId(UUID.randomUUID().toString().replace("-", ""));
        attempt.setBuyerEmail(user.getEmail());
        attempt.setMerchantCode(properties.getMerchantCode());
        attempt.setPaymentMethod(paymentMethod);
        attempt.setSnapshotJson(snapshotJson);
        attempt.setBillingJson(billingJson);
        attempt.setFingerprint(fingerprint);
        BigDecimal subtotal = money(lines.stream().map(IzipayWebLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal fee = money(subtotal.multiply(new BigDecimal("0.05")));
        attempt.setSubtotal(subtotal);
        attempt.setServiceFee(fee);
        attempt.setAmount(subtotal.add(fee));
        if (attempt.getAmount().signum() <= 0 || attempt.getAmount().compareTo(new BigDecimal("999999.99")) > 0)
            throw new IllegalArgumentException("El total debe ser mayor que cero y menor a S/ 1 000 000 para pagar con IZIPay.");
        attempt.setVerificationTokenHash(IzipayWebSignature.sha256(signatures.verificationToken(attempt.getOrderNumber(), attempt.getBuyerEmail())));
        // No charge is created by this API: it only authorizes the SDK for this immutable order.
        attempt.setAuthorization(gateway.authorize(attempt));
        attempts.save(attempt);
        user.setFullName(billing.get("firstName") + " " + billing.get("lastName"));
        user.setDni(billing.get("document"));
        user.setPhone(billing.get("phoneNumber"));
        return checkoutConfig(attempt);
    }

    @Transactional
    public Map<String, Object> verify(String email, String orderNumber, String verificationToken, JsonNode result) {
        ready();
        IzipayWebAttempt attempt = locked(orderNumber);
        own(attempt, email);
        if (!signatures.sameHash(verificationToken, attempt.getVerificationTokenHash()))
            throw new AccessDeniedException("No se pudo validar esta sesión de pago. Vuelve al checkout para consultar la orden.");
        IzipayWebSignature.VerifiedPayload verified = signatures.verify(result);
        return finalizePayment(attempt, verified);
    }

    @Transactional
    public Map<String, Object> notification(JsonNode result) {
        ready();
        IzipayWebSignature.VerifiedPayload verified = signatures.verify(result);
        IzipayWebAttempt attempt = locked(validator.orderNumber(verified.data()));
        return finalizePayment(attempt, verified);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(String email, String orderNumber) {
        IzipayWebAttempt attempt = attempts.findById(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Orden de pago no encontrada."));
        own(attempt, email);
        return response(attempt);
    }

    @Transactional(readOnly = true)
    public List<IzipayWebLine> purchasedLines(String email, String orderNumber) {
        IzipayWebAttempt attempt = attempts.findById(orderNumber).orElseThrow(() -> new IllegalArgumentException("Orden no encontrada."));
        own(attempt, email);
        return "COMPLETED".equals(attempt.getStatus()) ? readLines(attempt.getSnapshotJson()) : List.of();
    }

    private Map<String, Object> finalizePayment(IzipayWebAttempt attempt, IzipayWebSignature.VerifiedPayload verified) {
        boolean paid = validator.approved(verified.data(), attempt);
        // Lock + purchase relation keep both browser/IPN retries atomic and idempotent.
        if ("COMPLETED".equals(attempt.getStatus()) || "REVIEW".equals(attempt.getStatus())) return response(attempt);
        if ("ABANDONED".equals(attempt.getStatus())) {
            if (paid) {
                attempt.setStatus("REVIEW");
                attempt.setVerifiedAt(Instant.now());
                attempt.setSignedPayloadHash(verified.hash());
                attempt.setGatewayReference(validator.reference(verified.data()));
                attempt.setReviewReason("IZIPay confirmó una orden que el cliente había reemplazado. Revisar antes de emitir o reembolsar.");
            }
            return response(attempt);
        }
        if (!paid) {
            if (validator.definitivelyDeclined(verified.data())) attempt.setStatus("FAILED");
            attempt.setSignedPayloadHash(verified.hash());
            attempt.setVerifiedAt(Instant.now());
            return response(attempt);
        }
        attempt.setVerifiedAt(Instant.now());
        attempt.setSignedPayloadHash(verified.hash());
        attempt.setGatewayReference(validator.reference(verified.data()));
        List<IzipayWebLine> lines = readLines(attempt.getSnapshotJson());
        Map<String, String> billing = readBilling(attempt.getBillingJson());
        Map<Long, Event> lockedEvents = new LinkedHashMap<>();
        Map<Long, TicketType> lockedTypes = new LinkedHashMap<>();
        try {
            loadInventory(lines, lockedEvents, lockedTypes);
            checkInventory(lines, lockedEvents, lockedTypes);
        } catch (IllegalArgumentException exception) {
            // The money is already authorized: retain a durable reconciliation record instead of silently losing it.
            attempt.setStatus("REVIEW");
            attempt.setReviewReason("Pago verificado; inventario cambiado. Requiere revisión del comercio.");
            return response(attempt);
        }
        Purchase purchase = new Purchase();
        purchase.setBuyerFirstName(billing.get("firstName"));
        purchase.setBuyerLastName(billing.get("lastName"));
        purchase.setBuyerDni(billing.get("document"));
        purchase.setBuyerPhone(billing.get("phoneNumber"));
        purchase.setBuyerEmail(attempt.getBuyerEmail());
        purchase.setSubtotal(attempt.getSubtotal());
        purchase.setServiceFee(attempt.getServiceFee());
        purchase.setTotal(attempt.getAmount());
        purchase.setStatus(PurchaseStatus.COMPLETED);
        for (IzipayWebLine line : lines) {
            Event event = lockedEvents.get(line.eventId());
            for (int count = 0; count < line.quantity(); count++) {
                Ticket ticket = new Ticket();
                ticket.setEvent(event);
                ticket.setAttendeeName(billing.get("firstName") + " " + billing.get("lastName"));
                ticket.setAttendeeEmail(attempt.getBuyerEmail());
                ticket.setCode("CNF-" + event.getId() + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
                purchase.addTicket(ticket);
            }
            event.setSold((event.getSold() == null ? 0 : event.getSold()) + line.quantity());
            if (line.ticketTypeId() != null) {
                TicketType type = lockedTypes.get(line.ticketTypeId());
                type.setQuantitySold((type.getQuantitySold() == null ? 0 : type.getQuantitySold()) + line.quantity());
            }
        }
        attempt.setPurchase(purchases.save(purchase));
        attempt.setStatus("COMPLETED");
        return response(attempt);
    }

    private List<IzipayWebLine> snapshot(List<CartItem> cart) {
        if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("Agrega entradas al carrito antes de pagar.");
        if (cart.size() > 50) throw new IllegalArgumentException("El carrito contiene demasiados eventos.");
        List<IzipayWebLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CartItem item : cart) {
            if (item.getEventId() == null || item.getQuantity() < 1 || item.getQuantity() > 100)
                throw new IllegalArgumentException("Revisa la cantidad de entradas del carrito (máximo 100 por tipo).");
            String key = item.getEventId() + ":" + item.getTicketTypeId();
            if (!seen.add(key)) throw new IllegalArgumentException("El carrito contiene entradas duplicadas.");
            Event event = events.findById(item.getEventId()).orElseThrow(() -> new IllegalArgumentException("Evento no disponible."));
            BigDecimal price = event.getPrice();
            String ticketName = "Entrada general";
            if (item.getTicketTypeId() != null) {
                TicketType type = ticketTypes.findById(item.getTicketTypeId())
                        .orElseThrow(() -> new IllegalArgumentException("Tipo de entrada no disponible."));
                if (type.getEvent() == null || !event.getId().equals(type.getEvent().getId()))
                    throw new IllegalArgumentException("La entrada no pertenece al evento.");
                price = type.getPrice(); ticketName = type.getName();
            }
            if (price == null || price.signum() < 0 || item.getUnitPrice() == null || price.compareTo(item.getUnitPrice()) != 0)
                throw new IllegalArgumentException("El precio de una entrada cambió. Vuelve a agregarla al carrito.");
            lines.add(new IzipayWebLine(event.getId(), item.getTicketTypeId(), event.getTitle(), ticketName, item.getQuantity(), money(price)));
        }
        lines.sort(Comparator.comparing(IzipayWebLine::eventId).thenComparing(IzipayWebLine::ticketTypeId, Comparator.nullsFirst(Comparator.naturalOrder())));
        Map<Long, Event> eventMap = new LinkedHashMap<>();
        Map<Long, TicketType> typeMap = new LinkedHashMap<>();
        loadInventory(lines, eventMap, typeMap);
        checkInventory(lines, eventMap, typeMap);
        return List.copyOf(lines);
    }

    private void loadInventory(List<IzipayWebLine> lines, Map<Long, Event> eventMap, Map<Long, TicketType> typeMap) {
        lines.stream().map(IzipayWebLine::eventId).distinct().sorted().forEach(id -> eventMap.put(id,
                events.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Evento no disponible."))));
        lines.stream().map(IzipayWebLine::ticketTypeId).filter(Objects::nonNull).distinct().sorted().forEach(id -> typeMap.put(id,
                ticketTypes.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Entrada no disponible."))));
    }

    private void checkInventory(List<IzipayWebLine> lines, Map<Long, Event> eventMap, Map<Long, TicketType> typeMap) {
        Map<Long, Integer> quantities = new HashMap<>();
        for (IzipayWebLine line : lines) {
            Event event = eventMap.get(line.eventId());
            DemonstrationEventPolicy.requirePurchasable(event);
            if (event.getStatus() != EventStatus.PUBLISHED || event.getEventDate() == null || event.getEventDate().isBefore(LocalDateTime.now()))
                throw new IllegalArgumentException("El evento ya no está disponible para venta.");
            quantities.merge(line.eventId(), line.quantity(), Integer::sum);
            if (line.ticketTypeId() != null) {
                TicketType type = typeMap.get(line.ticketTypeId());
                if (!type.isActive() || type.getRemaining() < line.quantity()
                        || (type.getSalesStart() != null && type.getSalesStart().isAfter(LocalDateTime.now()))
                        || (type.getSalesEnd() != null && type.getSalesEnd().isBefore(LocalDateTime.now())))
                    throw new IllegalArgumentException("No hay suficientes entradas disponibles para este tipo.");
            }
        }
        quantities.forEach((id, quantity) -> {
            if (eventMap.get(id).getAvailableTickets() < quantity)
                throw new IllegalArgumentException("No hay suficientes entradas disponibles para el evento.");
        });
    }

    private Map<String, Object> checkoutConfig(IzipayWebAttempt attempt) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("transactionId", attempt.getTransactionId());
        config.put("action", "pay");
        config.put("merchantCode", attempt.getMerchantCode());
        config.put("order", Map.of("orderNumber", attempt.getOrderNumber(), "currency", attempt.getCurrency(),
                "amount", attempt.getAmount().toPlainString(), "processType", "AT",
                "payMethod", attempt.getPaymentMethod(),
                "merchantBuyerId", IzipayWebSignature.sha256(attempt.getBuyerEmail()).substring(0, 32),
                "dateTimeTransaction", Long.toString(attempt.getCreatedAt().toEpochMilli() * 1000)));
        config.put("billing", readBilling(attempt.getBillingJson()));
        config.put("render", Map.of("typeForm", "pop-up"));
        if (properties.getIpnUrl() != null && !properties.getIpnUrl().isBlank()) {
            if (!properties.getIpnUrl().startsWith("https://"))
                throw new IllegalStateException("La notificación de IZIPay requiere una dirección pública HTTPS.");
            config.put("urlIPN", properties.getIpnUrl());
        }
        return Map.of("config", config, "authorization", attempt.getAuthorization(), "keyRSA", properties.getPublicKey(),
                "orderNumber", attempt.getOrderNumber(),
                "paymentMethod", attempt.getPaymentMethod(),
                "verificationToken", signatures.verificationToken(attempt.getOrderNumber(), attempt.getBuyerEmail()));
    }

    private Map<String, Object> response(IzipayWebAttempt attempt) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderNumber", attempt.getOrderNumber());
        response.put("status", attempt.getStatus());
        if (attempt.getPaymentMethod() != null) response.put("paymentMethod", attempt.getPaymentMethod());
        if ("COMPLETED".equals(attempt.getStatus()) && attempt.getPurchase() != null)
            response.put("redirectUrl", "/cart/confirmation/" + attempt.getPurchase().getId());
        else if ("REVIEW".equals(attempt.getStatus()))
            response.put("message", "El pago fue confirmado y necesita revisión de disponibilidad. No vuelvas a pagar; contacta a soporte con la orden " + attempt.getOrderNumber() + ".");
        else if ("FAILED".equals(attempt.getStatus())) response.put("message", "IZIPay no aprobó el pago. Puedes volver a intentarlo.");
        else if ("ABANDONED".equals(attempt.getStatus())) response.put("message", "Esta orden fue reemplazada por un nuevo intento de pago.");
        else response.put("message", "El pago está pendiente de confirmación de IZIPay.");
        return response;
    }

    private IzipayWebAttempt locked(String orderNumber) {
        if (orderNumber == null || !orderNumber.matches("[A-Z0-9]{15}")) throw new IllegalArgumentException("Orden inválida.");
        return attempts.findLocked(orderNumber).orElseThrow(() -> new IllegalArgumentException("Orden de pago no encontrada."));
    }
    private void own(IzipayWebAttempt attempt, String email) {
        if (email == null || !attempt.getBuyerEmail().equalsIgnoreCase(email))
            throw new AccessDeniedException("Esta orden pertenece a otra cuenta.");
    }
    private String normalizePaymentMethod(String requestedMethod) {
        String value = requestedMethod == null ? "" : requestedMethod.trim().toUpperCase(Locale.ROOT);
        if (!properties.methods().contains(value))
            throw new IllegalArgumentException("El medio de pago seleccionado no está habilitado.");
        return value;
    }
    private void ready() { if (!properties.isReady()) throw new IllegalStateException("IZIPay aún no está configurado para recibir pagos."); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("No se pudo preparar la orden de pago."); }
    }
    private List<IzipayWebLine> readLines(String value) {
        try { return mapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception exception) { throw new IllegalStateException("No se pudo recuperar la orden de pago."); }
    }
    private Map<String, String> readBilling(String value) {
        try { return mapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception exception) { throw new IllegalStateException("No se pudo recuperar el comprador."); }
    }

}
