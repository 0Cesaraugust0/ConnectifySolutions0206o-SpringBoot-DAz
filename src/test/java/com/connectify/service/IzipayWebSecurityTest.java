package com.connectify.service;

import com.connectify.config.IzipayWebProperties;
import com.connectify.entity.IzipayWebAttempt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class IzipayWebSecurityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private IzipayWebSignature signatures;
    private final IzipayWebPaymentValidator validator = new IzipayWebPaymentValidator();
    private IzipayWebAttempt attempt;
    private static final String HASH_KEY = "local-fixture-key-ñ";

    @BeforeEach
    void setup() {
        IzipayWebProperties properties = new IzipayWebProperties();
        properties.setHashKey(HASH_KEY);
        signatures = new IzipayWebSignature(properties, mapper);
        attempt = new IzipayWebAttempt();
        attempt.setOrderNumber("0123456789ABCDE");
        attempt.setTransactionId("transaction123");
        attempt.setMerchantCode("1234567");
        attempt.setPaymentMethod("CARD");
        attempt.setAmount(new BigDecimal("105.00"));
    }

    @Test
    void verifiesLiteralUtf8PayloadAndIgnoresUnsignedEnvelope() throws Exception {
        String raw = " \n" + payload("105.00", "0123456789ABCDE").toString();
        var envelope = mapper.createObjectNode();
        envelope.put("payloadHttp", raw);
        envelope.put("signature", sign(raw));
        envelope.put("code", "999");
        assertThat(validator.approved(signatures.verify(envelope).data(), attempt)).isTrue();
        envelope.put("payloadHttp", raw.trim());
        assertThatThrownBy(() -> signatures.verify(envelope)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("firma");
    }

    @Test
    void rejectsUnsignedSuccessAndForgedDigest() throws Exception {
        assertThatThrownBy(() -> signatures.verify(payload("105.00", "0123456789ABCDE"))).isInstanceOf(IllegalArgumentException.class);
        JsonNode forged = mapper.valueToTree(Map.of("payloadHttp", payload("105.00", "0123456789ABCDE").toString(), "signature", Base64.getEncoder().encodeToString(new byte[32])));
        assertThatThrownBy(() -> signatures.verify(forged)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsDocumentedNestedSignatureEnvelope() throws Exception {
        String raw = payload("105.00", "0123456789ABCDE").toString();
        JsonNode nested = mapper.valueToTree(Map.of("response", Map.of("payloadHttp", raw, "signature", sign(raw))));
        assertThat(validator.approved(signatures.verify(nested).data(), attempt)).isTrue();
    }

    @Test
    void rejectsSignedAmountOrderCurrencyMerchantAndTransactionMismatches() throws Exception {
        assertThatThrownBy(() -> validator.approved(payload("1.00", "0123456789ABCDE"), attempt)).hasMessageContaining("importe");
        assertThatThrownBy(() -> validator.approved(payload("105.00", "ANOTHERORDER1234"), attempt)).hasMessageContaining("orden");
        for (String field : new String[]{"currency", "merchantCode", "transactionId"}) {
            String raw = payload("105.00", "0123456789ABCDE").toString();
            String original = switch (field) { case "currency" -> "PEN"; case "merchantCode" -> "1234567"; default -> "transaction123"; };
            JsonNode modified = mapper.readTree(raw.replace(original, "INVALID"));
            assertThatThrownBy(() -> validator.approved(modified, attempt)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void doesNotTreatDeclineOrMissingAuthorizationAsPaid() throws Exception {
        JsonNode declined = mapper.readTree(payload("105.00", "0123456789ABCDE").toString().replace("\"code\":\"00\"", "\"code\":\"05\""));
        assertThat(validator.approved(declined, attempt)).isFalse();
        JsonNode missing = mapper.readTree(payload("105.00", "0123456789ABCDE").toString().replace("AUTH123", ""));
        assertThatThrownBy(() -> validator.approved(missing, attempt)).hasMessageContaining("autorización");
        var annulled = payload("105.00", "0123456789ABCDE").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) annulled.path("response").path("order").get(0)).put("stateMessage", "Anulado");
        assertThatThrownBy(() -> validator.approved(annulled, attempt)).hasMessageContaining("estado");
    }

    @Test
    void verificationTokenIsBoundToOwnerAndOrder() {
        String token = signatures.verificationToken("order1", "one@example.test");
        assertThat(signatures.sameHash(token, IzipayWebSignature.sha256(token))).isTrue();
        assertThat(signatures.sameHash(signatures.verificationToken("order2", "one@example.test"), IzipayWebSignature.sha256(token))).isFalse();
        assertThat(signatures.sameHash(signatures.verificationToken("order1", "two@example.test"), IzipayWebSignature.sha256(token))).isFalse();
    }

    static JsonNode payload(String amount, String orderNumber) {
        return new ObjectMapper().valueToTree(Map.of("code", "00", "transactionId", "transaction123", "response", Map.of(
                "payMethod", "CARD",
                "merchant", Map.of("merchantCode", "1234567"), "order", new Object[]{Map.of("orderNumber", orderNumber,
                        "currency", "PEN", "amount", amount, "codeAuth", "AUTH123", "uniqueId", "unique123",
                        "payMethodAuthorization", "CARD")})));
    }

    private String sign(String raw) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HASH_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
