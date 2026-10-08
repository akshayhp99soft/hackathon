package com.lyxor.billing;

import com.lyxor.billing.controller.BillingController;
import com.lyxor.billing.model.InvoiceLineItem;
import com.lyxor.billing.model.InvoiceRecord;
import com.lyxor.billing.model.TaxJurisdiction;
import com.lyxor.billing.service.CurrencyExchangeRateService;
import com.lyxor.billing.service.DoubleEntryLedgerService;
import com.lyxor.billing.service.InvoiceCalculationEngine;
import com.lyxor.billing.service.PaymentGatewaySettlementService;
import com.lyxor.persistence.model.AccountEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BillingEngineTests {

    @Test
    void testInvoiceCalculationHappyPath() {
        InvoiceCalculationEngine engine = new InvoiceCalculationEngine();
        InvoiceRecord invoice = new InvoiceRecord("INV-100", "CUST-1");
        invoice.addLineItem(new InvoiceLineItem("SKU-1", "Product 1", 2, BigDecimal.valueOf(50)));

        InvoiceRecord result = engine.calculateInvoice(
                invoice,
                BigDecimal.valueOf(0.10), // 10% discount
                new TaxJurisdiction("US", "CA", BigDecimal.valueOf(0.08)) // 8% tax
        );

        assertEquals(BigDecimal.valueOf(100), result.getSubtotal());
        assertEquals(BigDecimal.valueOf(10.00).setScale(2), result.getDiscountAmount());
        assertNotNull(result.getTotalAmount());
    }

    @Test
    void testDoubleEntryLedgerSplitPayment() {
        DoubleEntryLedgerService ledger = new DoubleEntryLedgerService();
        List<BigDecimal> portions = ledger.splitPaymentAcrossAccounts(BigDecimal.valueOf(100), 4);

        assertEquals(4, portions.size());
        assertEquals(BigDecimal.valueOf(25.00).setScale(2), portions.get(0));
    }

    @Test
    void testAccountReconciliation() {
        DoubleEntryLedgerService ledger = new DoubleEntryLedgerService();
        AccountEntity acc1 = new AccountEntity("A1", "T1", "USD", BigDecimal.valueOf(500));
        AccountEntity acc2 = new AccountEntity("A2", "T1", "USD", BigDecimal.valueOf(500));

        assertTrue(ledger.reconcileAccounts(acc1, acc2));
    }

    @Test
    void testCurrencyExchangeConversion() {
        CurrencyExchangeRateService fxService = new CurrencyExchangeRateService();
        fxService.setRate("USD_EUR", 0.92);

        BigDecimal converted = fxService.convert(BigDecimal.valueOf(100), "USD_EUR");
        assertEquals(BigDecimal.valueOf(92.00).setScale(2), converted);
        assertTrue(fxService.isRateWithinVolatilityLimit(0.92, 0.90, 0.05));
    }

    @Test
    void testPaymentGatewaySettlement() {
        PaymentGatewaySettlementService gateway = new PaymentGatewaySettlementService();
        PaymentGatewaySettlementService.SettlementStatus status =
                gateway.settleTransaction("cust1", "ord1", false);

        assertEquals(PaymentGatewaySettlementService.SettlementStatus.SUCCESS, status);
    }

    @Test
    void testBillingControllerEndpoints() {
        BillingController controller = new BillingController();
        ResponseEntity<Map<String, Object>> calcResp = controller.calculateInvoice("cust1", 0.05, 0.08);
        assertEquals(200, calcResp.getStatusCode().value());

        ResponseEntity<Map<String, Object>> settleResp = controller.settleInvoice(Map.of("customerId", "c1", "orderId", "o1"));
        assertEquals(200, settleResp.getStatusCode().value());
    }
}
