package com.lyxor.billing.model;

import java.math.BigDecimal;
import java.util.Objects;

public class InvoiceLineItem {
    private final String sku;
    private final String description;
    private final int quantity;
    private final BigDecimal unitPrice;

    public InvoiceLineItem(String sku, String description, int quantity, BigDecimal unitPrice) {
        this.sku = Objects.requireNonNull(sku);
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceLineItem that = (InvoiceLineItem) o;
        return Objects.equals(sku, that.sku);
    }
}
