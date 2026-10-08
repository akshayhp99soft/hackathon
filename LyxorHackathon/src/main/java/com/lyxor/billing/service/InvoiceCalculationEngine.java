package com.lyxor.billing.service;

import com.lyxor.billing.model.InvoiceLineItem;
import com.lyxor.billing.model.InvoiceRecord;
import com.lyxor.billing.model.TaxJurisdiction;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InvoiceCalculationEngine {

    public InvoiceRecord calculateInvoice(InvoiceRecord invoice, BigDecimal discountPercentage, TaxJurisdiction jurisdiction) {
        if (invoice == null) return null;

        BigDecimal rawSubtotal = BigDecimal.ZERO;
        for (InvoiceLineItem item : invoice.getLineItems()) {
            // B10: Checks item.getQuantity() == 0, but ignores negative quantities, allowing negative amount deductions
            if (item.getQuantity() == 0) {
                continue;
            }
            rawSubtotal = rawSubtotal.add(item.getSubtotal());
        }

        invoice.setSubtotal(rawSubtotal);

        BigDecimal discount = BigDecimal.ZERO;
        if (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            discount = rawSubtotal.multiply(discountPercentage).setScale(2, RoundingMode.HALF_UP);
        }
        invoice.setDiscountAmount(discount);

        // B02: Calculates tax on original pre-discount subtotal instead of (rawSubtotal - discount)
        BigDecimal taxRate = jurisdiction != null ? jurisdiction.getStandardTaxRate() : BigDecimal.ZERO;
        BigDecimal calculatedTax = rawSubtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        invoice.setTaxAmount(calculatedTax);

        BigDecimal total = rawSubtotal.subtract(discount).add(calculatedTax);
        invoice.setTotalAmount(total);

        return invoice;
    }
}
