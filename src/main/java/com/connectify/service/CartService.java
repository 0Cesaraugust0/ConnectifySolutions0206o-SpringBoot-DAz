package com.connectify.service;

import com.connectify.dto.CartItem;
import com.connectify.dto.IzipayWebLine;
import com.connectify.entity.Event;
import com.connectify.entity.ShoppingCart;
import com.connectify.entity.ShoppingCartConsumption;
import com.connectify.entity.ShoppingCartItem;
import com.connectify.entity.TicketType;
import com.connectify.entity.UserAccount;
import com.connectify.repository.EventRepository;
import com.connectify.repository.ShoppingCartConsumptionRepository;
import com.connectify.repository.ShoppingCartRepository;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class CartService {
    private static final String CART_KEY = "CONNECTIFY_CART";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ShoppingCartRepository carts;
    private final ShoppingCartConsumptionRepository consumptions;
    private final UserAccountRepository users;
    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;

    public CartService(ShoppingCartRepository carts,
                       ShoppingCartConsumptionRepository consumptions,
                       UserAccountRepository users,
                       EventRepository events,
                       TicketTypeRepository ticketTypes) {
        this.carts = carts;
        this.consumptions = consumptions;
        this.users = users;
        this.events = events;
        this.ticketTypes = ticketTypes;
    }

    @Transactional(readOnly = true)
    public List<CartItem> getItems(String email) {
        return carts.findByBuyer_EmailIgnoreCase(normalize(email)).map(this::toItems).orElseGet(List::of);
    }

    /** Compatibility for existing callers; authenticated clients always use their durable cart. */
    @Transactional(readOnly = true)
    public List<CartItem> getItems(HttpSession session) {
        return currentEmail().map(this::getItems).orElseGet(() -> sessionItems(session));
    }

    @Transactional
    public void addEvent(HttpSession session, Event event, int quantity) {
        addEvent(session, event, null, quantity);
    }

    @Transactional
    public void addEvent(HttpSession session, Event event, TicketType ticketType, int quantity) {
        Optional<String> email = currentEmail();
        if (email.isPresent()) addEvent(email.get(), event, ticketType, quantity);
        else addSessionEvent(session, event, ticketType, quantity);
    }

    @Transactional
    public void addEvent(String email, Event event, TicketType ticketType, int quantity) {
        ShoppingCart cart = cartForUpdate(email);
        Event managedEvent = events.findById(event.getId())
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        TicketType managedType = ticketType == null ? null : ticketTypes.findById(ticketType.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tipo de entrada no encontrado"));
        int safeQuantity = Math.max(quantity, 1);
        ShoppingCartItem existing = cart.getItems().stream()
                .filter(item -> Objects.equals(item.getEvent().getId(), managedEvent.getId())
                        && Objects.equals(id(item.getTicketType()), id(managedType)))
                .findFirst().orElse(null);
        BigDecimal price = managedType != null && managedType.getPrice() != null
                ? managedType.getPrice() : managedEvent.getPrice() != null ? managedEvent.getPrice() : BigDecimal.ZERO;
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + safeQuantity);
            existing.setUnitPrice(price);
        } else {
            ShoppingCartItem item = new ShoppingCartItem();
            item.setEvent(managedEvent);
            item.setTicketType(managedType);
            item.setQuantity(safeQuantity);
            item.setUnitPrice(price);
            cart.addItem(item);
        }
        cart.touch();
        carts.save(cart);
    }

    @Transactional
    public void removeEvent(HttpSession session, Long eventId) {
        Optional<String> email = currentEmail();
        if (email.isPresent()) removeEvent(email.get(), eventId);
        else sessionItems(session).removeIf(item -> item.getEventId().equals(eventId));
    }

    @Transactional
    public void removeEvent(String email, Long eventId) {
        carts.findLockedByBuyerEmail(normalize(email)).ifPresent(cart -> {
            cart.getItems().removeIf(item -> Objects.equals(item.getEvent().getId(), eventId));
            cart.touch();
        });
    }

    @Transactional
    public void clear(HttpSession session) {
        Optional<String> email = currentEmail();
        if (email.isPresent()) clear(email.get());
        else sessionItems(session).clear();
    }

    @Transactional
    public void clear(String email) {
        carts.findLockedByBuyerEmail(normalize(email)).ifPresent(cart -> {
            cart.getItems().clear();
            cart.touch();
        });
    }

    /** Applies a paid order exactly once, even if browser callback and IPN are repeated. */
    @Transactional
    public void removePurchased(String email, String orderNumber, List<IzipayWebLine> paidLines) {
        String normalizedEmail = normalize(email);
        ShoppingCart cart = carts.findLockedByBuyerEmail(normalizedEmail).orElse(null);
        if (consumptions.existsById(orderNumber)) return;
        if (cart != null) {
            for (IzipayWebLine paid : paidLines) {
                ShoppingCartItem item = cart.getItems().stream()
                        .filter(candidate -> Objects.equals(candidate.getEvent().getId(), paid.eventId())
                                && Objects.equals(id(candidate.getTicketType()), paid.ticketTypeId()))
                        .findFirst().orElse(null);
                if (item == null) continue;
                int remaining = item.getQuantity() - paid.quantity();
                if (remaining <= 0) cart.getItems().remove(item);
                else item.setQuantity(remaining);
            }
            cart.touch();
        }
        consumptions.save(new ShoppingCartConsumption(orderNumber, normalizedEmail));
    }

    @Transactional(readOnly = true)
    public int countItems(HttpSession session) { return getItems(session).stream().mapToInt(CartItem::getQuantity).sum(); }
    @Transactional(readOnly = true)
    public BigDecimal subtotal(HttpSession session) { return subtotal(getItems(session)); }
    @Transactional(readOnly = true)
    public BigDecimal serviceFee(HttpSession session) { return fee(subtotal(session)); }
    @Transactional(readOnly = true)
    public BigDecimal total(HttpSession session) { return subtotal(session).add(serviceFee(session)); }
    @Transactional(readOnly = true)
    public BigDecimal subtotal(String email) { return subtotal(getItems(email)); }
    @Transactional(readOnly = true)
    public BigDecimal serviceFee(String email) { return fee(subtotal(email)); }
    @Transactional(readOnly = true)
    public BigDecimal total(String email) { return subtotal(email).add(serviceFee(email)); }

    private ShoppingCart cartForUpdate(String email) {
        String normalized = normalize(email);
        ShoppingCart existing = carts.findLockedByBuyerEmail(normalized).orElse(null);
        if (existing != null) return existing;
        UserAccount buyer = users.findLockedByEmail(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta Cliente no encontrada."));
        ShoppingCart created = new ShoppingCart();
        created.setBuyer(buyer);
        return carts.save(created);
    }

    private List<CartItem> toItems(ShoppingCart cart) {
        return cart.getItems().stream().sorted(Comparator.comparing(ShoppingCartItem::getAddedAt)).map(item -> {
            Event event = item.getEvent();
            TicketType type = item.getTicketType();
            String date = event.getEventDate() == null ? "Por definir" : event.getEventDate().format(FORMATTER);
            return new CartItem(event.getId(), id(type), type == null ? "Entrada general" : type.getName(),
                    event.getTitle(), date, event.getLocation(), item.getQuantity(), item.getUnitPrice());
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<CartItem> sessionItems(HttpSession session) {
        Object value = session.getAttribute(CART_KEY);
        if (value instanceof List<?>) return (List<CartItem>) value;
        List<CartItem> items = new ArrayList<>();
        session.setAttribute(CART_KEY, items);
        return items;
    }

    private void addSessionEvent(HttpSession session, Event event, TicketType type, int quantity) {
        List<CartItem> items = sessionItems(session);
        Long typeId = id(type);
        for (CartItem item : items) {
            if (Objects.equals(item.getEventId(), event.getId()) && Objects.equals(item.getTicketTypeId(), typeId)) {
                item.setQuantity(item.getQuantity() + Math.max(quantity, 1));
                return;
            }
        }
        BigDecimal price = type != null && type.getPrice() != null ? type.getPrice()
                : event.getPrice() != null ? event.getPrice() : BigDecimal.ZERO;
        String date = event.getEventDate() == null ? "Por definir" : event.getEventDate().format(FORMATTER);
        items.add(new CartItem(event.getId(), typeId, type == null ? "Entrada general" : type.getName(),
                event.getTitle(), date, event.getLocation(), Math.max(quantity, 1), price));
    }

    private Optional<String> currentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) return Optional.empty();
        return Optional.of(normalize(authentication.getName()));
    }

    private BigDecimal subtotal(List<CartItem> items) {
        return items.stream().map(CartItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal fee(BigDecimal subtotal) { return subtotal.multiply(new BigDecimal("0.05")); }
    private String normalize(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Cuenta Cliente no encontrada.");
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
    private Long id(TicketType type) { return type == null ? null : type.getId(); }
}
