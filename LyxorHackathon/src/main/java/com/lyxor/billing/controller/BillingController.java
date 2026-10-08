package com.lyxor.billing.controller;

import com.lyxor.billing.model.InvoiceLineItem;
import com.lyxor.billing.model.InvoiceRecord;
import com.lyxor.billing.model.TaxJurisdiction;
import com.lyxor.billing.service.InvoiceCalculationEngine;
import com.lyxor.billing.service.PaymentGatewaySettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final InvoiceCalculationEngine calculationEngine;
    private final PaymentGatewaySettlementService settlementService;

    public BillingController() {
        this.calculationEngine = new InvoiceCalculationEngine();
        this.settlementService = new PaymentGatewaySettlementService();
    }

    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculateInvoice(
            @RequestParam String customerId,
            @RequestParam(defaultValue = "0.0") double discount,
            @RequestParam(defaultValue = "0.10") double taxRate) {

        InvoiceRecord invoice = new InvoiceRecord(UUID.randomUUID().toString(), customerId);
        invoice.addLineItem(new InvoiceLineItem("SKU-BASE", "Base Plan", 1, BigDecimal.valueOf(100)));

        InvoiceRecord calculated = calculationEngine.calculateInvoice(
                invoice,
                BigDecimal.valueOf(discount),
                new TaxJurisdiction("US", "CA", BigDecimal.valueOf(taxRate))
        );

        return ResponseEntity.ok(Map.of(
                "invoiceId", calculated.getInvoiceId(),
                "subtotal", calculated.getSubtotal(),
                "tax", calculated.getTaxAmount(),
                "total", calculated.getTotalAmount()
        ));
    }

    @PostMapping("/settle")
    public ResponseEntity<Map<String, Object>> settleInvoice(@RequestBody Map<String, String> body) {
        String customerId = body.getOrDefault("customerId", "c1");
        String orderId = body.getOrDefault("orderId", "o1");

        PaymentGatewaySettlementService.SettlementStatus status =
                settlementService.settleTransaction(customerId, orderId, false);

        return ResponseEntity.ok(Map.of(
                "status", status.name(),
                "idempotencyKey", settlementService.generateIdempotencyKey(customerId, orderId)
        ));
    }
}
