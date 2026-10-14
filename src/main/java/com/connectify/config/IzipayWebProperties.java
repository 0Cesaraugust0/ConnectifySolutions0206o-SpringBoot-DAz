package com.connectify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "izipay.web")
public class IzipayWebProperties {
    private boolean enabled = true;
    private String environment = "sandbox";
    private String merchantCode = "";
    private String apiKey = "";
    private String publicKey = "";
    private String hashKey = "";
    private String paymentMethods = "CARD,YAPE_CODE,PAGO_PUSH,QR";
    private String ipnUrl = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment == null ? "" : environment.toLowerCase(Locale.ROOT); }
    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getHashKey() { return hashKey; }
    public void setHashKey(String hashKey) { this.hashKey = hashKey; }
    public String getPaymentMethods() { return paymentMethods; }
    public void setPaymentMethods(String paymentMethods) { this.paymentMethods = paymentMethods; }
    public String getIpnUrl() { return ipnUrl; }
    public void setIpnUrl(String ipnUrl) { this.ipnUrl = ipnUrl; }

    public boolean isReady() {
        return enabled && Set.of("sandbox", "production").contains(environment)
                && present(merchantCode) && present(apiKey) && present(publicKey) && present(hashKey)
                && !methods().isEmpty();
    }

    public List<String> methods() {
        if (paymentMethods == null) return List.of();
        return Arrays.stream(paymentMethods.split(","))
                .map(String::trim).map(value -> value.toUpperCase(Locale.ROOT))
                .filter(Set.of("CARD", "YAPE_CODE", "PAGO_PUSH", "QR")::contains).distinct().toList();
    }

    public String scriptUrl() {
        return "production".equals(environment)
                ? "https://checkout.izipay.pe/payments/v1/js/index.js"
                : "https://sandbox-checkout.izipay.pe/payments/v1/js/index.js";
    }

    private boolean present(String value) { return value != null && !value.isBlank(); }
}
