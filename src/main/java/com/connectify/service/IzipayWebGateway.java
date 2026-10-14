package com.connectify.service;

import com.connectify.config.IzipayWebProperties;
import com.connectify.entity.IzipayWebAttempt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class IzipayWebGateway {
    private final IzipayWebProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public IzipayWebGateway(IzipayWebProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public String authorize(IzipayWebAttempt attempt) {
        String host = "production".equals(properties.getEnvironment())
                ? "https://api-pw.izipay.pe" : "https://sandbox-api-pw.izipay.pe";
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "requestSource", "ECOMMERCE", "merchantCode", attempt.getMerchantCode(),
                    "orderNumber", attempt.getOrderNumber(), "publicKey", properties.getApiKey(),
                    "amount", attempt.getAmount().toPlainString()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/security/v1/Token/Generate"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                    .header("transactionId", attempt.getTransactionId())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 100000)
                throw new IllegalStateException("IZIPay no pudo preparar el pago. Inténtalo nuevamente.");
            JsonNode json = mapper.readTree(response.body());
            String token = json.path("response").path("token").asText();
            if (!"00".equals(json.path("code").asText()) || token.isBlank())
                throw new IllegalStateException("IZIPay no autorizó el inicio del pago. Revisa la configuración del comercio.");
            return token;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La conexión con IZIPay fue interrumpida.");
        } catch (IllegalStateException exception) { throw exception; }
        catch (Exception exception) {
            // Never expose API keys, provider bodies or customer data in an exception/log.
            throw new IllegalStateException("No se pudo conectar con IZIPay. Aún no se ha iniciado un cobro.");
        }
    }
}
