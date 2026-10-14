package com.connectify.controller;

import com.connectify.dto.IzipayWebBuyer;
import com.connectify.service.CartService;
import com.connectify.service.IzipayWebCheckoutService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
public class IzipayWebController {
    public static final String REQUEST_TOKEN_KEY = "CONNECTIFY_IZIPAY_WEB_REQUEST_TOKEN";
    private static final String CONSUMED_KEY = "CONNECTIFY_IZIPAY_WEB_CONSUMED";
    private final IzipayWebCheckoutService checkout;
    private final CartService cart;

    public IzipayWebController(IzipayWebCheckoutService checkout, CartService cart) {
        this.checkout = checkout; this.cart = cart;
    }

    @PostMapping("/cart/checkout/izipay/session")
    public ResponseEntity<Map<String, Object>> start(@ModelAttribute IzipayWebBuyer buyer,
                                                    @RequestParam String checkoutToken,
                                                    @RequestParam String payMethod,
                                                    Authentication authentication, HttpSession session) {
        String expected = (String) session.getAttribute(REQUEST_TOKEN_KEY);
        if (expected == null || checkoutToken == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), checkoutToken.getBytes(StandardCharsets.UTF_8)))
            throw new AccessDeniedException("La sesión de checkout venció. Recarga la página antes de continuar.");
        String buyerEmail = email(authentication);
        return noStore(checkout.start(buyerEmail, buyer, List.copyOf(cart.getItems(buyerEmail)), payMethod));
    }

    @PostMapping("/cart/checkout/izipay/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Verification request,
                                                     Authentication authentication, HttpSession session) {
        Map<String, Object> response = checkout.verify(email(authentication), request.orderNumber(), request.verificationToken(), request.result());
        consumeIfCompleted(response, email(authentication), session);
        return noStore(response);
    }

    @GetMapping("/cart/checkout/izipay/status")
    public ResponseEntity<Map<String, Object>> status(@RequestParam String orderNumber,
                                                     Authentication authentication, HttpSession session) {
        Map<String, Object> response = checkout.status(email(authentication), orderNumber);
        consumeIfCompleted(response, email(authentication), session);
        return noStore(response);
    }

    @PostMapping("/payments/izipay/web/notification")
    public ResponseEntity<Map<String, Object>> notification(@RequestBody JsonNode result) {
        checkout.notification(result);
        return noStore(Map.of("received", true));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).header("Cache-Control", "no-store").body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> unavailable(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).header("Cache-Control", "no-store").body(Map.of("message", exception.getMessage()));
    }

    public static String requestToken(HttpSession session) {
        if (session.getAttribute(REQUEST_TOKEN_KEY) == null) session.setAttribute(REQUEST_TOKEN_KEY, UUID.randomUUID().toString());
        return (String) session.getAttribute(REQUEST_TOKEN_KEY);
    }

    private String email(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new AccessDeniedException("Inicia sesión para continuar.");
        return authentication.getName();
    }

    private ResponseEntity<Map<String, Object>> noStore(Map<String, Object> body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    @SuppressWarnings("unchecked")
    private void consumeIfCompleted(Map<String, Object> response, String email, HttpSession session) {
        if (!"COMPLETED".equals(response.get("status"))) return;
        synchronized (session) {
            Set<String> consumed = (Set<String>) session.getAttribute(CONSUMED_KEY);
            if (consumed == null) { consumed = new HashSet<>(); session.setAttribute(CONSUMED_KEY, consumed); }
            String order = response.get("orderNumber").toString();
            if (consumed.contains(order)) return;
            cart.removePurchased(email, order, checkout.purchasedLines(email, order));
            consumed.add(order);
        }
    }

    public record Verification(String orderNumber, String verificationToken, JsonNode result) { }
}
