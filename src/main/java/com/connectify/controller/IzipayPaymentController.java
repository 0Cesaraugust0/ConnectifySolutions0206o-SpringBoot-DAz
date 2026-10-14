package com.connectify.controller;

import com.connectify.dto.IzipayCheckoutSession;
import com.connectify.config.IzipayWebProperties;
import com.connectify.entity.Purchase;
import com.connectify.service.CartService;
import com.connectify.service.IzipayPaymentService;
import com.connectify.service.PurchaseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Controller
@RequestMapping("/payments/izipay")
public class IzipayPaymentController {

    private final IzipayPaymentService izipayPaymentService;
    private final PurchaseService purchaseService;
    private final CartService cartService;
    private final IzipayWebProperties izipayWebProperties;

    public IzipayPaymentController(IzipayPaymentService izipayPaymentService,
                                   PurchaseService purchaseService,
                                   CartService cartService,
                                   IzipayWebProperties izipayWebProperties) {
        this.izipayPaymentService = izipayPaymentService;
        this.purchaseService = purchaseService;
        this.cartService = cartService;
        this.izipayWebProperties = izipayWebProperties;
    }

    @PostMapping("/success")
    public String success(@RequestParam Map<String, String> payload,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        if (izipayWebProperties.isEnabled()) {
            return paymentRejected("Usa el checkout Web Core para verificar tu pago.", redirectAttributes);
        }
        String krAnswer = payload.get("kr-answer");
        String krHash = payload.get("kr-hash");
        IzipayCheckoutSession checkoutSession = checkoutSession(session);

        if (checkoutSession == null) {
            return paymentRejected("No se encontró una orden de pago activa.", redirectAttributes);
        }
        if (!izipayPaymentService.verifyClientReturn(krAnswer, krHash)) {
            return paymentRejected("IZIPay devolvió una firma inválida. No se generaron tickets.", redirectAttributes);
        }
        if (!checkoutSession.orderId().equals(izipayPaymentService.orderId(krAnswer))) {
            return paymentRejected("La orden pagada no coincide con el checkout actual.", redirectAttributes);
        }
        if (!izipayPaymentService.isPaid(krAnswer)) {
            return paymentRejected("La transacción no quedó pagada. Estado: " + izipayPaymentService.orderStatus(krAnswer), redirectAttributes);
        }
        if (!sameAmount(checkoutSession.expectedTotal(), cartService.total(session))) {
            return paymentRejected("El carrito cambió después de preparar el pago. Revisa tu compra antes de volver a pagar.", redirectAttributes);
        }

        Purchase purchase = purchaseService.completePurchase(
                session,
                checkoutSession.firstName(),
                checkoutSession.lastName(),
                checkoutSession.dni(),
                checkoutSession.phone(),
                checkoutSession.email());
        clearIzipaySession(session);
        return "redirect:/cart/confirmation/" + purchase.getId();
    }

    @GetMapping("/success")
    public String successWithoutPayload(RedirectAttributes redirectAttributes) {
        return paymentRejected("IZIPay no envió los datos firmados del pago.", redirectAttributes);
    }

    @PostMapping("/error")
    public String error(@RequestParam Map<String, String> payload,
                        RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("paymentErrorMessage", "IZIPay no pudo completar la transacción.");
        return "redirect:/cart/checkout?paymentError=true";
    }

    @GetMapping("/error")
    public String errorGet(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("paymentErrorMessage", "IZIPay no pudo completar la transacción.");
        return "redirect:/cart/checkout?paymentError=true";
    }

    @PostMapping("/cancel")
    public String cancel(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("paymentErrorMessage", "Pago cancelado antes de generar tickets.");
        return "redirect:/cart/checkout?paymentCancelled=true";
    }

    @GetMapping("/cancel")
    public String cancelGet(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("paymentErrorMessage", "Pago cancelado antes de generar tickets.");
        return "redirect:/cart/checkout?paymentCancelled=true";
    }

    private String paymentRejected(String message, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("paymentErrorMessage", message);
        return "redirect:/cart/checkout?paymentError=true";
    }

    private IzipayCheckoutSession checkoutSession(HttpSession session) {
        Object value = session.getAttribute(CartController.IZIPAY_CHECKOUT_SESSION_KEY);
        if (value instanceof IzipayCheckoutSession izipayCheckoutSession) {
            return izipayCheckoutSession;
        }
        return null;
    }

    private void clearIzipaySession(HttpSession session) {
        session.removeAttribute(CartController.IZIPAY_CHECKOUT_SESSION_KEY);
        session.removeAttribute(CartController.IZIPAY_FORM_TOKEN_KEY);
        session.removeAttribute(CartController.IZIPAY_ORDER_ID_KEY);
    }

    private boolean sameAmount(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.setScale(2, RoundingMode.HALF_UP)
                .compareTo(actual.setScale(2, RoundingMode.HALF_UP)) == 0;
    }
}
