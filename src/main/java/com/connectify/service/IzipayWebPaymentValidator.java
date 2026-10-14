package com.connectify.service;

import com.connectify.entity.IzipayWebAttempt;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class IzipayWebPaymentValidator {
    /** Only call with the JSON obtained from a verified payloadHttp, never the unsigned envelope. */
    public boolean approved(JsonNode signed, IzipayWebAttempt attempt) {
        requireEqual(attempt.getTransactionId(), signed.path("transactionId").asText(), "transacción");
        JsonNode response = signed.path("response");
        requireEqual(attempt.getMerchantCode(), response.path("merchant").path("merchantCode").asText(), "comercio");
        JsonNode orders = response.path("order");
        if (!orders.isArray() || orders.size() != 1)
            throw new IllegalArgumentException("La respuesta de IZIPay no contiene una única orden válida.");
        JsonNode order = orders.get(0);
        requireEqual(attempt.getOrderNumber(), order.path("orderNumber").asText(), "orden");
        requireEqual(attempt.getCurrency(), order.path("currency").asText(), "moneda");
        try {
            if (attempt.getAmount().compareTo(new BigDecimal(order.path("amount").asText())) != 0)
                throw new IllegalArgumentException("El importe firmado no coincide con la compra.");
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El importe firmado no es válido.");
        }
        if (!"00".equals(signed.path("code").asText())) return false;
        if (order.path("codeAuth").asText().isBlank())
            throw new IllegalArgumentException("IZIPay no confirmó la autorización del pago.");
        String state = order.path("stateMessage").asText();
        if (!state.isBlank() && !"Autorizado".equalsIgnoreCase(state))
            throw new IllegalArgumentException("El estado firmado de la orden no confirma un pago autorizado.");
        return true;
    }

    public boolean definitivelyDeclined(JsonNode signed) {
        // Communication/time-out codes are ambiguous and must not unlock a second charge.
        String code = signed.path("code").asText();
        JsonNode order = signed.path("response").path("order").get(0);
        return !"00".equals(code) && order.path("codeAuth").asText().isBlank()
                && ("RS".equals(code) || "A02".equals(code)
                || "Rechazado".equalsIgnoreCase(order.path("stateMessage").asText()));
    }

    public String orderNumber(JsonNode signed) {
        JsonNode order = signed.path("response").path("order");
        if (!order.isArray() || order.size() != 1)
            throw new IllegalArgumentException("No se pudo identificar la orden firmada.");
        return order.get(0).path("orderNumber").asText();
    }

    public String reference(JsonNode signed) {
        JsonNode order = signed.path("response").path("order").get(0);
        String value = order.path("uniqueId").asText();
        return value.isBlank() ? order.path("referenceNumber").asText() : value;
    }

    private void requireEqual(String expected, String actual, String field) {
        if (expected == null || !expected.equals(actual))
            throw new IllegalArgumentException("La " + field + " firmada no coincide con el intento de pago.");
    }
}
