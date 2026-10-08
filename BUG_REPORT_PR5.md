# Lyxor Hackathon — Master Bug Inventory: PR 5 (Billing & Financial Ledger)

**Branch:** `feature/billing-and-financial-ledger`  
**Target:** `main`  
**Scope:** Invoicing Engine, Tax Calculation, Double-Entry Ledger, Currency Exchange (FX), and Payment Gateway Settlement.

---

## Bug Inventory Summary

| ID | Category | File | Method / Line | Difficulty | Why It May Be Missed by Lyxor |
|---|---|---|---|---|---|
| **B01** | Calculation / Financial | `com.lyxor.billing.service.DoubleEntryLedgerService` | `splitPaymentAcrossAccounts` | **Very High** | Splits payments via `divide(count, 2, HALF_UP)` individually. The sum of allocated rounded cents fails to match the original total payment, violating the fundamental double-entry ledger balance invariant. |
| **B02** | Business Rule / Calculation | `com.lyxor.billing.service.InvoiceCalculationEngine` | `calculateInvoice` | **Very High** | Multiplies `taxRate` against raw pre-discount `rawSubtotal` instead of `(rawSubtotal - discount)`. Invoices with customer discounts overcharge sales tax. |
| **B03** | Concurrency / Deadlock | `com.lyxor.billing.service.DoubleEntryLedgerService` | `reconcileAccounts` | **High** | Acquires account locks in caller argument order (`synchronized(accountA) { synchronized(accountB) }`). Concurrent two-way reconciliation triggers circular wait deadlock. |
| **B04** | Type / Precision | `com.lyxor.billing.service.CurrencyExchangeRateService` | `convert` | **Extreme** | Constructs `new BigDecimal(double)` instead of `BigDecimal.valueOf(double)`. Binary IEEE-754 floating-point representation introduces microscopic rounding noise into financial currency conversions. |
| **B05** | Security / API Contract | `com.lyxor.billing.service.PaymentGatewaySettlementService` | `generateIdempotencyKey` | **Very High** | Uses simple string concatenation `customerId + orderId` without delimiter. Idempotency keys collide (e.g. `cust1` + `23` vs `cust12` + `3`), rejecting valid transactions as duplicate charges. |
| **B06** | Collection / Equality | `com.lyxor.billing.model.InvoiceLineItem` | `equals` | **High** | Overrides `equals()` based on `sku` but omits `hashCode()`. Adding duplicate items to a set in `InvoiceRecord` fails deduplication and doubles invoice charges. |
| **B07** | Boundary / Edge Case | `com.lyxor.billing.service.CurrencyExchangeRateService` | `isRateWithinVolatilityLimit` | **High** | Uses strict inequality `diff < maxVolatilityThreshold`. When exchange rate drift hits the exact threshold, boundary evaluation permits excessive volatility. |
| **B08** | Data Integrity / Architecture | `com.lyxor.billing.model.InvoiceRecord` | `getLineItems` | **Medium** | Returns direct mutable `List<InvoiceLineItem>` reference instead of `Collections.unmodifiableList()`. Consumers modifying list pollute historical generated invoices. |
| **B09** | Error Handling / Transaction | `com.lyxor.billing.service.PaymentGatewaySettlementService` | `settleTransaction` | **Very High** | Catches `GatewayTimeoutException`, swallows it, and returns `SettlementStatus.SUCCESS`, erroneously marking unconfirmed timeout transactions as settled. |
| **B10** | Validation / Edge Case | `com.lyxor.billing.service.InvoiceCalculationEngine` | `calculateInvoice` | **High** | Checks `item.getQuantity() == 0` but permits negative quantities (`quantity < 0`), allowing malicious inputs to inject negative line items and reduce invoice totals. |

---

## Detailed Bug Breakdown & Reproduction Guide

### **B01: Double-Entry Split Payment Rounding Cent Imbalance**
- **File:** `src/main/java/com/lyxor/billing/service/DoubleEntryLedgerService.java`
- **Trigger:** Splitting \$100 across 3 accounts (`splitPaymentAcrossAccounts(100, 3)`).
- **Expected:** The sum of allocated portions equals exact total payment (\$100.00).
- **Actual:** Each portion evaluates to \$33.33. The sum is \$99.99, losing 1 cent and violating ledger invariants.
- **Reproduction:** Call `splitPaymentAcrossAccounts(100, 3)`; sum of elements is `99.99` instead of `100.00`.
- **Classification:** Static Detection: *Low* | Financial Calculation: *Very High*

---

### **B02: Pre-Discount Sales Tax Base Calculation Error**
- **File:** `src/main/java/com/lyxor/billing/service/InvoiceCalculationEngine.java`
- **Trigger:** Calculating invoice total when both customer discount and tax jurisdiction are present.
- **Expected:** Tax applies to taxable subtotal: `(subtotal - discount) * taxRate`.
- **Actual:** `calculatedTax = rawSubtotal.multiply(taxRate)`, overcharging tax by applying tax on undiscounted amounts.
- **Reproduction:** Subtotal \$100, discount \$50, tax 10%. Expected tax: \$5.00. Actual tax: \$10.00.
- **Classification:** Static Detection: *Low* | Business Rule: *Very High*

---

### **B03: Arbitrary Monitor Lock Ordering in Account Reconciliation**
- **File:** `src/main/java/com/lyxor/billing/service/DoubleEntryLedgerService.java`
- **Trigger:** Two worker threads reconciling accounts A and B concurrently in opposite directions.
- **Expected:** Accounts are sorted by deterministic key (e.g. `accountId`) before monitor acquisition.
- **Actual:** Thread 1 locks A then B; Thread 2 locks B then A, causing a circular wait deadlock.
- **Reproduction:** Launch Thread 1 running `reconcileAccounts(accA, accB)` and Thread 2 running `reconcileAccounts(accB, accA)`.
- **Classification:** Static Detection: *Low* | Concurrency / Deadlock: *High*

---

### **B04: Binary Floating-Point Inaccuracy in Currency Conversion**
- **File:** `src/main/java/com/lyxor/billing/service/CurrencyExchangeRateService.java`
- **Trigger:** Converting currency with a floating-point exchange rate (e.g. `1.10`).
- **Expected:** Exact decimal arithmetic via `BigDecimal.valueOf(1.10)`.
- **Actual:** `new BigDecimal(double)` evaluates to `1.10000000000000008881784...`, injecting precision artifacts into financial ledger calculations.
- **Reproduction:** Convert \$1,000,000 at rate `1.10`; examine unscaled value of `rateDecimal`.
- **Classification:** Static Detection: *Low* | Type / Precision: *Extreme*

---

### **B05: Delimiter-Less Idempotency Key Concatenation Collision**
- **File:** `src/main/java/com/lyxor/billing/service/PaymentGatewaySettlementService.java`
- **Trigger:** Processing customer `"cust1"` with order `"23"` followed by customer `"cust12"` with order `"3"`.
- **Expected:** Distinct composite keys generated for distinct transactions.
- **Actual:** Both produce string `"cust123"`. The second transaction is rejected or skips processing.
- **Reproduction:** Generate keys for `("cust1", "23")` and `("cust12", "3")`; both evaluate to `"cust123"`.
- **Classification:** Static Detection: *Low* | API Security: *Very High*

---

### **B06: Missing HashCode in Invoice Line Item Deduplication**
- **File:** `src/main/java/com/lyxor/billing/model/InvoiceLineItem.java`
- **Trigger:** Adding separate `InvoiceLineItem` instances with identical SKUs into a `HashSet`.
- **Expected:** Set prevents duplicate SKU line items.
- **Actual:** `InvoiceLineItem` defines `equals()` but lacks `hashCode()`. Objects hash to different buckets and duplicate.
- **Reproduction:** Create two `new InvoiceLineItem("SKU-1", ...)` objects and add to set; set size is 2.
- **Classification:** Static Detection: *Low* | Collection Contract: *High*

---

### **B07: Volatility Tolerance Strict Inequality Risk Leak**
- **File:** `src/main/java/com/lyxor/billing/service/CurrencyExchangeRateService.java`
- **Trigger:** Exchange rate change is exactly at the max allowed risk threshold.
- **Expected:** Threshold breaches reject or flag volatility risk.
- **Actual:** `diff < maxVolatilityThreshold` permits boundary deviations without risk warnings.
- **Reproduction:** `currentRate = 1.05`, `baselineRate = 1.00`, `maxVolatility = 0.05`.
- **Classification:** Static Detection: *Low* | Boundary: *High*

---

### **B08: Leaked Mutable Line Items Collection in InvoiceRecord**
- **File:** `src/main/java/com/lyxor/billing/model/InvoiceRecord.java`
- **Trigger:** Client code calling `invoice.getLineItems().clear()`.
- **Expected:** Invoiced items are read-only after creation.
- **Actual:** Direct mutable collection reference is returned and altered in-memory.
- **Reproduction:** Retrieve invoice, call `getLineItems().add(...)`. Line items change on signed invoice.
- **Classification:** Static Detection: *Low* | Data Integrity: *Medium*

---

### **B09: Swallowed Payment Gateway Timeout Exception**
- **File:** `src/main/java/com/lyxor/billing/service/PaymentGatewaySettlementService.java`
- **Trigger:** Payment gateway throws timeout / socket exception.
- **Expected:** Transaction enters `PENDING` or throws error requiring retry.
- **Actual:** Catch block swallows timeout and records `SettlementStatus.SUCCESS`, allowing unpaid invoices to settle.
- **Reproduction:** Call `settleTransaction("c1", "o1", true)` (simulates timeout); returns `SUCCESS`.
- **Classification:** Static Detection: *Low* | Error Handling / Transaction: *Very High*

---

### **B10: Negative Quantity Line Item Validation Bypass**
- **File:** `src/main/java/com/lyxor/billing/service/InvoiceCalculationEngine.java`
- **Trigger:** Submitting an invoice with negative quantity (`quantity = -5`).
- **Expected:** Rejected or excluded from subtotal.
- **Actual:** Checks `quantity == 0` but allows negative quantities, subtracting amount from total invoice.
- **Reproduction:** Add item with `quantity = -2` and `price = $50` to invoice; subtotal decreases by \$100.
- **Classification:** Static Detection: *Low* | Validation: *High*
