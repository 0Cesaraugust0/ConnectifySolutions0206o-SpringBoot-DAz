package com.connectify.dto;

import java.math.BigDecimal;

public record IzipayWebLine(Long eventId, Long ticketTypeId, String title, String ticketTypeName,
                            int quantity, BigDecimal unitPrice) {
    public BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
