package com.connectify.service;

import com.connectify.config.IzipayWebProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class IzipayWebSignature {
    private final IzipayWebProperties properties;
    private final ObjectMapper mapper;

    public IzipayWebSignature(IzipayWebProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public VerifiedPayload verify(JsonNode result) {
        if (result == null) throw new IllegalArgumentException("No se recibió la respuesta del pago.");
        JsonNode envelope = result.has("payloadHttp") ? result : result.path("response");
        JsonNode raw = envelope.path("payloadHttp");
        JsonNode signed = envelope.path("signature");
        if (!raw.isTextual() || !signed.isTextual() || raw.textValue().length() > 65536)
            throw new IllegalArgumentException("IZIPay no devolvió una respuesta firmada. La compra sigue pendiente de verificación.");
        try {
            // Official Web Core signature: exact payloadHttp UTF-8 bytes, HMAC-SHA256, Base64.
            byte[] received = Base64.getDecoder().decode(signed.textValue());
            if (!MessageDigest.isEqual(hmac(raw.textValue()), received))
                throw new IllegalArgumentException("La firma del pago no es válida.");
            JsonNode payload = mapper.readTree(raw.textValue());
            if (payload == null || !payload.isObject()) throw new IllegalArgumentException("Respuesta de pago inválida.");
            return new VerifiedPayload(payload, sha256(raw.textValue()));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("No se pudo verificar la respuesta del pago.");
        }
    }

    public String verificationToken(String orderNumber, String email) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac("connectify:verify:" + orderNumber + ":" + email));
    }

    public boolean sameHash(String token, String expectedHash) {
        return token != null && expectedHash != null && MessageDigest.isEqual(
                sha256(token).getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] hmac(String value) {
        try {
            if (properties.getHashKey() == null || properties.getHashKey().isBlank())
                throw new IllegalStateException("Falta configurar la firma de IZIPay.");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getHashKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalStateException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("No se pudo verificar la firma de IZIPay."); }
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public record VerifiedPayload(JsonNode data, String hash) { }
}
