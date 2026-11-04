package com.connectify.service;

import com.connectify.config.IzipayProperties;
import com.connectify.dto.IzipayPaymentRequest;
import com.connectify.dto.IzipayPaymentToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class IzipayPaymentService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final IzipayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IzipayPaymentService(IzipayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isReady() {
        return properties.isEnabled()
                && hasText(properties.getEndpoint())
                && hasText(properties.getUsername())
                && hasText(properties.getPassword())
                && hasText(properties.getPublicKey())
                && hasText(properties.getHmacSha256Key())
                && hasText(properties.getSmartFormScriptUrl());
    }

    public IzipayPaymentToken createPaymentToken(IzipayPaymentRequest paymentRequest) {
        if (!isReady()) {
            throw new IzipayPaymentException("IZIPay no tiene credenciales completas para generar el formToken.");
        }
        if (paymentRequest.amount() == null || paymentRequest.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IzipayPaymentException("El monto del carrito debe ser mayor que cero para pagar con IZIPay.");
        }

        try {
            Map<String, Object> payload = createPayload(paymentRequest);
            String body = objectMapper.writeValueAsString(payload);
            String basicAuth = Base64.getEncoder().encodeToString(
                    (properties.getUsername().trim() + ":" + properties.getPassword().trim()).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoint().trim()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + basicAuth)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IzipayPaymentException("IZIPay rechazó la creación del pago. HTTP " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String status = json.path("status").asText("");
            String formToken = json.path("answer").path("formToken").asText("");
            if (!"SUCCESS".equalsIgnoreCase(status) || !hasText(formToken)) {
                String errorMessage = json.path("answer").path("errorMessage").asText("No se pudo generar el formToken.");
                throw new IzipayPaymentException("IZIPay: " + errorMessage);
            }

            return new IzipayPaymentToken(paymentRequest.orderId(), formToken);
        } catch (IzipayPaymentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IzipayPaymentException("No se pudo conectar con IZIPay para preparar el pago.", exception);
        }
    }

    public boolean verifyClientReturn(String krAnswer, String krHash) {
        if (!hasText(krAnswer) || !hasText(krHash) || !hasText(properties.getHmacSha256Key())) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.getHmacSha256Key().trim().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            String calculatedHash = toHex(mac.doFinal(krAnswer.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    calculatedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                    krHash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean isPaid(String krAnswer) {
        return "PAID".equalsIgnoreCase(orderStatus(krAnswer));
    }

    public String orderStatus(String krAnswer) {
        try {
            return objectMapper.readTree(krAnswer).path("orderStatus").asText("");
        } catch (Exception exception) {
            return "";
        }
    }

    public String orderId(String krAnswer) {
        try {
            return objectMapper.readTree(krAnswer).path("orderDetails").path("orderId").asText("");
        } catch (Exception exception) {
            return "";
        }
    }

    private Map<String, Object> createPayload(IzipayPaymentRequest paymentRequest) {
        Map<String, Object> billingDetails = new LinkedHashMap<>();
        billingDetails.put("firstName", paymentRequest.firstName());
        billingDetails.put("lastName", paymentRequest.lastName());
        billingDetails.put("phoneNumber", paymentRequest.phone());
        billingDetails.put("identityType", "DNI");
        billingDetails.put("identityCode", paymentRequest.dni());
        billingDetails.put("country", "PE");
        billingDetails.put("city", "Lima");
        billingDetails.put("state", "Lima");
        billingDetails.put("zipCode", "15000");

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("email", paymentRequest.email());
        customer.put("billingDetails", billingDetails);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountInMinorUnits(paymentRequest.amount()));
        payload.put("currency", paymentRequest.currency());
        payload.put("orderId", paymentRequest.orderId());
        payload.put("customer", customer);

        return payload;
    }

    private long amountInMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
