package com.connectify.controller;

import com.connectify.config.IzipayProperties;
import com.connectify.config.IzipayWebProperties;
import com.connectify.dto.IzipayCheckoutSession;
import com.connectify.dto.IzipayPaymentRequest;
import com.connectify.dto.IzipayPaymentToken;
import com.connectify.entity.Event;
import com.connectify.entity.EventStatus;
import com.connectify.entity.Purchase;
import com.connectify.entity.TicketType;
import com.connectify.entity.UserAccount;
import com.connectify.repository.TicketTypeRepository;
import com.connectify.repository.UserAccountRepository;
import com.connectify.service.CartService;
import com.connectify.service.EventService;
import com.connectify.service.IzipayPaymentException;
import com.connectify.service.IzipayPaymentService;
import com.connectify.service.PurchaseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequestMapping("/cart")
public class CartController {

    public static final String IZIPAY_CHECKOUT_SESSION_KEY = "CONNECTIFY_IZIPAY_CHECKOUT";
    public static final String IZIPAY_FORM_TOKEN_KEY = "CONNECTIFY_IZIPAY_FORM_TOKEN";
    public static final String IZIPAY_ORDER_ID_KEY = "CONNECTIFY_IZIPAY_ORDER_ID";

    private final CartService cartService;
    private final EventService eventService;
    private final PurchaseService purchaseService;
    private final IzipayPaymentService izipayPaymentService;
    private final IzipayProperties izipayProperties;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserAccountRepository userAccountRepository;
    private final IzipayWebProperties izipayWebProperties;

    public CartController(CartService cartService,
                          EventService eventService,
                          PurchaseService purchaseService,
                          IzipayPaymentService izipayPaymentService,
                          IzipayProperties izipayProperties,
                          TicketTypeRepository ticketTypeRepository,
                          UserAccountRepository userAccountRepository,
                          IzipayWebProperties izipayWebProperties) {
        this.cartService = cartService;
        this.eventService = eventService;
        this.purchaseService = purchaseService;
        this.izipayPaymentService = izipayPaymentService;
        this.izipayProperties = izipayProperties;
        this.ticketTypeRepository = ticketTypeRepository;
        this.userAccountRepository = userAccountRepository;
        this.izipayWebProperties = izipayWebProperties;
    }

    @GetMapping
    public String cart(HttpSession session, Model model) {
        addCartAttributes(session, model);
        return "cart/index";
    }

    @PostMapping("/add/{eventId}")
    public String add(@PathVariable Long eventId,
                      @RequestParam(defaultValue = "1") int quantity,
                      @RequestParam(required = false) Long ticketTypeId,
                      HttpSession session) {
        return addToCart(eventId, quantity, ticketTypeId, session);
    }

    /**
     * Atajo de Marketplace para el prototipo: agrega una unidad de la variante elegida
     * y lleva al carrito. El checkout sigue siendo la confirmación final de compra.
     */
    @GetMapping("/quick-add/{eventId}")
    public String quickAdd(@PathVariable Long eventId,
                           @RequestParam Long ticketTypeId,
                           HttpSession session) {
        return addToCart(eventId, 1, ticketTypeId, session);
    }

    @PostMapping("/remove/{eventId}")
    public String remove(@PathVariable Long eventId, HttpSession session) {
        cartService.removeEvent(session, eventId);
        return "redirect:/cart";
    }

    @PostMapping("/clear")
    public String clear(HttpSession session) {
        cartService.clear(session);
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model, Authentication authentication) {
        addCartAttributes(session, model);
        UserAccount account = currentAccount(authentication);
        NameParts name = splitName(account.getFullName());
        model.addAttribute("buyerFirstName", name.firstName());
        model.addAttribute("buyerLastName", name.lastName());
        model.addAttribute("buyerEmail", account.getEmail());
        model.addAttribute("buyerDni", text(account.getDni()));
        model.addAttribute("buyerPhone", text(account.getPhone()));
        addIzipayAttributes(session, model);
        model.addAttribute("izipayWebEnabled", izipayWebProperties.isEnabled());
        model.addAttribute("izipayWebReady", izipayWebProperties.isReady());
        model.addAttribute("izipayWebScriptUrl", izipayWebProperties.scriptUrl());
        model.addAttribute("izipayWebMethods", String.join(",", izipayWebProperties.methods()));
        model.addAttribute("izipayWebMode", izipayWebProperties.getEnvironment().toUpperCase(java.util.Locale.ROOT));
        model.addAttribute("izipayWebRequestToken", IzipayWebController.requestToken(session));
        return "cart/checkout";
    }

    @PostMapping("/checkout/izipay/start")
    public String startIzipayCheckout(@RequestParam String firstName,
                                      @RequestParam String lastName,
                                      @RequestParam String dni,
                                      @RequestParam String phone,
                                      HttpSession session,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        if (izipayWebProperties.isEnabled()) {
            throw new AccessDeniedException("Usa el botón de pago IZIPay del checkout.");
        }
        UserAccount account = currentAccount(authentication);
        String normalizedFirstName = text(firstName);
        String normalizedLastName = text(lastName);
        String normalizedDni = text(dni);
        String normalizedPhone = text(phone);
        if (normalizedFirstName.isBlank() || normalizedLastName.isBlank()) {
            clearIzipaySession(session);
            return "redirect:/cart/checkout?profileError=true";
        }
        if (cartService.getItems(session).isEmpty()) {
            clearIzipaySession(session);
            return "redirect:/cart";
        }
        if (!izipayPaymentService.isReady()) {
            clearIzipaySession(session);
            return "redirect:/cart/checkout?paymentConfigError=true";
        }

        account.setFullName(normalizedFirstName + " " + normalizedLastName);
        account.setDni(normalizedDni);
        account.setPhone(normalizedPhone);
        userAccountRepository.save(account);

        BigDecimal total = cartService.total(session);
        String orderId = newOrderId();

        try {
            IzipayPaymentToken token = izipayPaymentService.createPaymentToken(new IzipayPaymentRequest(
                    orderId,
                    total,
                    izipayCurrency(),
                    normalizedFirstName,
                    normalizedLastName,
                    normalizedDni,
                    normalizedPhone,
                    account.getEmail()));

            session.setAttribute(IZIPAY_CHECKOUT_SESSION_KEY, new IzipayCheckoutSession(
                    orderId,
                    normalizedFirstName,
                    normalizedLastName,
                    normalizedDni,
                    normalizedPhone,
                    account.getEmail(),
                    total));
            session.setAttribute(IZIPAY_FORM_TOKEN_KEY, token.formToken());
            session.setAttribute(IZIPAY_ORDER_ID_KEY, token.orderId());
            return "redirect:/cart/checkout?paymentReady=true";
        } catch (IzipayPaymentException exception) {
            clearIzipaySession(session);
            redirectAttributes.addFlashAttribute("paymentErrorMessage", exception.getMessage());
            return "redirect:/cart/checkout?paymentTokenError=true";
        }
    }

    @PostMapping("/checkout/confirm")
    public String confirmCheckout(@RequestParam String firstName,
                                  @RequestParam String lastName,
                                  @RequestParam String dni,
                                  @RequestParam String phone,
                                  HttpSession session,
                                  Authentication authentication) {
        if (izipayWebProperties.isEnabled()) {
            throw new AccessDeniedException("La compra requiere un pago confirmado por IZIPay.");
        }
        UserAccount account = currentAccount(authentication);
        String normalizedFirstName = firstName == null ? "" : firstName.trim();
        String normalizedLastName = lastName == null ? "" : lastName.trim();
        if (normalizedFirstName.isBlank() || normalizedLastName.isBlank()) {
            return "redirect:/cart/checkout?profileError=true";
        }

        account.setFullName(normalizedFirstName + " " + normalizedLastName);
        account.setDni(text(dni));
        account.setPhone(text(phone));
        userAccountRepository.save(account);

        Purchase purchase = purchaseService.completePurchase(session,
                normalizedFirstName, normalizedLastName, text(dni), text(phone), account.getEmail());
        return "redirect:/cart/confirmation/" + purchase.getId();
    }

    @GetMapping("/confirmation/{purchaseId}")
    public String confirmation(@PathVariable Long purchaseId, Model model, Authentication authentication) {
        Purchase purchase = purchaseService.findById(purchaseId);
        UserAccount account = currentAccount(authentication);
        if (purchase.getBuyerEmail() == null || !purchase.getBuyerEmail().equalsIgnoreCase(account.getEmail())) {
            throw new AccessDeniedException("No puedes ver una compra de otra cuenta.");
        }
        model.addAttribute("purchase", purchase);
        return "cart/confirmation";
    }

    @ModelAttribute("izipayDefaultScriptUrl")
    public String izipayDefaultScriptUrl() {
        return "https://static.micuentaweb.pe/static/js/krypton-client/V4.0/stable/kr-payment-form.min.js";
    }

    private String addToCart(Long eventId, int quantity, Long ticketTypeId, HttpSession session) {
        Event event = eventService.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            return "redirect:/events";
        }

        TicketType ticketType = null;
        if (ticketTypeId != null) {
            ticketType = ticketTypeRepository.findById(ticketTypeId)
                    .orElseThrow(() -> new IllegalArgumentException("Tipo de entrada no encontrado"));
            if (!ticketType.isActive() || ticketType.getEvent() == null || !eventId.equals(ticketType.getEvent().getId())) {
                return "redirect:/events/" + eventId;
            }
        }
        cartService.addEvent(session, event, ticketType, Math.max(1, quantity));
        return "redirect:/cart";
    }

    private UserAccount currentAccount(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AccessDeniedException("Se requiere una cuenta Cliente.");
        }
        return userAccountRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Cuenta Cliente no encontrada."));
    }

    private NameParts splitName(String fullName) {
        String[] parts = text(fullName).trim().split("\\s+", 2);
        return new NameParts(parts.length > 0 ? parts[0] : "", parts.length > 1 ? parts[1] : "");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String newOrderId() {
        return "CNF-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String izipayCurrency() {
        String currency = text(izipayProperties.getCurrency());
        return currency.isBlank() ? "PEN" : currency;
    }

    private void clearIzipaySession(HttpSession session) {
        session.removeAttribute(IZIPAY_CHECKOUT_SESSION_KEY);
        session.removeAttribute(IZIPAY_FORM_TOKEN_KEY);
        session.removeAttribute(IZIPAY_ORDER_ID_KEY);
    }

    private void addCartAttributes(HttpSession session, Model model) {
        model.addAttribute("items", cartService.getItems(session));
        model.addAttribute("cartCount", cartService.countItems(session));
        model.addAttribute("subtotal", cartService.subtotal(session));
        model.addAttribute("serviceFee", cartService.serviceFee(session));
        model.addAttribute("total", cartService.total(session));
    }

    private void addIzipayAttributes(HttpSession session, Model model) {
        String scriptUrl = text(izipayProperties.getSmartFormScriptUrl());
        model.addAttribute("izipayEnabled", izipayPaymentService.isEnabled());
        model.addAttribute("izipayReady", izipayPaymentService.isReady());
        model.addAttribute("izipayPublicKey", text(izipayProperties.getPublicKey()));
        model.addAttribute("izipayScriptUrl", scriptUrl.isBlank() ? izipayDefaultScriptUrl() : scriptUrl);
        model.addAttribute("izipaySuccessUrl", text(izipayProperties.getSuccessUrl()));
        model.addAttribute("izipayErrorUrl", text(izipayProperties.getErrorUrl()));
        model.addAttribute("izipayCancelUrl", text(izipayProperties.getCancelUrl()));
        model.addAttribute("izipayFormToken", session.getAttribute(IZIPAY_FORM_TOKEN_KEY));
        model.addAttribute("izipayOrderId", session.getAttribute(IZIPAY_ORDER_ID_KEY));
    }

    private record NameParts(String firstName, String lastName) {
    }
}
