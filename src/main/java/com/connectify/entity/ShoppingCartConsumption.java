package com.connectify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Prevents a repeated payment callback from removing the same cart lines twice. */
@Entity
@Table(name = "shopping_cart_consumptions")
public class ShoppingCartConsumption {
    @Id
    @Column(length = 50)
    private String orderNumber;

    @Column(nullable = false)
    private String buyerEmail;

    @Column(nullable = false)
    private Instant appliedAt = Instant.now();

    public ShoppingCartConsumption() { }
    public ShoppingCartConsumption(String orderNumber, String buyerEmail) {
        this.orderNumber = orderNumber;
        this.buyerEmail = buyerEmail;
    }
    public String getOrderNumber() { return orderNumber; }
    public String getBuyerEmail() { return buyerEmail; }
    public Instant getAppliedAt() { return appliedAt; }
}
