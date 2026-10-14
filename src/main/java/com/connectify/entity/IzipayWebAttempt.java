package com.connectify.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Durable purchase snapshot. Never stores card numbers or a customer's payment credentials. */
@Entity
@Table(name = "izipay_web_attempts", indexes = @Index(name = "ix_izipay_web_buyer", columnList = "buyerEmail,status"))
public class IzipayWebAttempt {
    @Id
    @Column(length = 50)
    private String orderNumber;
    @Column(nullable = false, unique = true, length = 40)
    private String transactionId;
    @Column(nullable = false)
    private String buyerEmail;
    @Column(nullable = false, length = 20)
    private String merchantCode;
    @Column(nullable = false, length = 3)
    private String currency = "PEN";
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal serviceFee;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String billingJson;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @Column(nullable = false, length = 64)
    private String verificationTokenHash;
    @Column(columnDefinition = "TEXT")
    private String authorization;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant verifiedAt;
    @Column(length = 100)
    private String gatewayReference;
    @Column(length = 64)
    private String signedPayloadHash;
    @Column(length = 255)
    private String reviewReason;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", unique = true)
    private Purchase purchase;

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getBuyerEmail() { return buyerEmail; }
    public void setBuyerEmail(String buyerEmail) { this.buyerEmail = buyerEmail; }
    public String getMerchantCode() { return merchantCode; }
    public void setMerchantCode(String merchantCode) { this.merchantCode = merchantCode; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public void setServiceFee(BigDecimal serviceFee) { this.serviceFee = serviceFee; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getBillingJson() { return billingJson; }
    public void setBillingJson(String billingJson) { this.billingJson = billingJson; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getVerificationTokenHash() { return verificationTokenHash; }
    public void setVerificationTokenHash(String verificationTokenHash) { this.verificationTokenHash = verificationTokenHash; }
    public String getAuthorization() { return authorization; }
    public void setAuthorization(String authorization) { this.authorization = authorization; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getGatewayReference() { return gatewayReference; }
    public void setGatewayReference(String gatewayReference) { this.gatewayReference = gatewayReference; }
    public String getSignedPayloadHash() { return signedPayloadHash; }
    public void setSignedPayloadHash(String signedPayloadHash) { this.signedPayloadHash = signedPayloadHash; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public Purchase getPurchase() { return purchase; }
    public void setPurchase(Purchase purchase) { this.purchase = purchase; }
}
