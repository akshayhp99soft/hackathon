# Lyxor Hackathon — Master Bug Inventory: PR 2 (JPA Persistence & Transactions)

**Branch:** `feature/jpa-persistence-and-transactions`  
**Target:** `main`  
**Scope:** JPA Persistence, Multi-Tenant Data Isolation, Distributed Transfers, Transaction Boundaries, and Ledger Controllers.

---

## Bug Inventory Summary

| ID | Category | File | Method / Line | Difficulty | Why It May Be Missed by Lyxor |
|---|---|---|---|---|---|
| **B01** | Architecture / Transaction | `com.lyxor.persistence.service.AccountTransferService` | `executeTransfer` | **Extreme** | Self-invokes `this.applyTransferInternal(...)` annotated with `@Transactional`. Spring AOP proxy is bypassed; no transaction is started. |
| **B02** | Transaction / Error Handling | `com.lyxor.persistence.service.AccountTransferService` | `applyTransferInternal` | **Very High** | `@Transactional` without `rollbackFor = Exception.class`. Checked exceptions (e.g. `IOException`) fail to trigger rollback, leaving half-settled debits committed. |
| **B03** | Security / Data Isolation | `com.lyxor.persistence.service.MultiTenantIsolationService` | `findAccountsForTenant` | **Very High** | When `tenantId` is null or blank, falls back to `accountRepository.findAll()`, leaking cross-tenant account balances across all tenants. |
| **B04** | Concurrency / Data Consistency | `com.lyxor.persistence.model.AccountEntity` | `credit` / `debit` | **High** | In-memory balance mutation lacks `@Version` optimistic locking check; concurrent credits trigger lost updates and overwrite balances. |
| **B05** | State Management / JPA | `com.lyxor.persistence.service.TransactionAuditService` | `markTransactionAudited` | **Very High** | Modifies detached entity fields (`getAuditTags().add(...)`, `setStatus(...)`) assuming automated JPA dirty checking without invoking `repository.save()`. Changes are silently discarded. |
| **B06** | Boundary / API Contract | `com.lyxor.persistence.controller.LedgerController` | `getTransactions` | **High** | Uses `offset = page * size`. When callers supply default 1-based page index (`page=1`), the entire first page of transactions is skipped. |
| **B07** | Database / Concurrency | `com.lyxor.persistence.service.AccountTransferService` | `applyTransferInternal` | **Very High** | Reads balance then executes settlement delay before debit under `READ_COMMITTED` without pessimistic lock (`PESSIMISTIC_WRITE`), permitting concurrent balance drain. |
| **B08** | Logical / Calculation | `com.lyxor.persistence.repository.AccountRepository` | `findAccountsWithZeroBalance` | **High** | Uses `BigDecimal.equals(BigDecimal.ZERO)` instead of `compareTo() == 0`. Values with non-zero scale like `new BigDecimal("0.00")` return `false` on equality. |
| **B09** | Data Integrity / Object | `com.lyxor.persistence.model.LedgerTransactionEntity` | `getAuditTags` | **Medium** | Returns direct mutable `List<String> auditTags` reference instead of `Collections.unmodifiableList()`. Consumers modifying tags pollute audited financial records. |
| **B10** | State Management / Recovery | `com.lyxor.persistence.service.AccountTransferService` | `applyTransferInternal` | **High** | Sets `transaction.setStatus(TransactionStatus.SETTLED)` in entity memory before saving to repository. If database save throws an exception, cached entity retains `SETTLED` state in memory. |

---

## Detailed Bug Breakdown & Reproduction Guide

### **B01: Self-Invocation `@Transactional` Proxy Bypass**
- **File:** `src/main/java/com/lyxor/persistence/service/AccountTransferService.java`
- **Trigger:** Calling `executeTransfer()`.
- **Expected:** Spring intercepts the call and wraps `applyTransferInternal` in an active database transaction.
- **Actual:** Direct `this.` method call bypasses Spring's CGLIB/JDK dynamic proxy; method runs with `TransactionStatus.isActualTransactionActive() == false`.
- **Reproduction:** Inject `AccountTransferService` in Spring context, invoke `executeTransfer()`, check `TransactionSynchronizationManager.isActualTransactionActive()` → `false`.
- **Classification:** Static Detection: *Low* | Cross-class / AOP: *Extreme*

---

### **B02: Unchecked vs Checked Exception Rollback Inconsistency**
- **File:** `src/main/java/com/lyxor/persistence/service/AccountTransferService.java`
- **Trigger:** Transfer amount > 10,000 triggering checked `IOException("High-value wire settlement gateway timeout")`.
- **Expected:** Spring rolls back the source account debit upon exception.
- **Actual:** Spring default `@Transactional` only rolls back for `RuntimeException` and `Error`. Checked `IOException` causes transaction to commit source debit without crediting target.
- **Reproduction:** Transfer 15,000; catch `IOException`; source account balance is debited but target account receives nothing.
- **Classification:** Static Detection: *Low* | Transaction: *Very High*

---

### **B03: Multi-Tenant Data Leak on Null/Blank Tenant Header**
- **File:** `src/main/java/com/lyxor/persistence/service/MultiTenantIsolationService.java`
- **Trigger:** Calling `findAccountsForTenant(null)` or `findAccountsForTenant("")`.
- **Expected:** Returns empty list or throws `IllegalArgumentException`.
- **Actual:** Executes `accountRepository.findAll()`, returning all accounts across all tenants.
- **Reproduction:** Call `findAccountsForTenant(null)`. Returns full database contents across all customer tenants.
- **Classification:** Static Detection: *Low* | Data Security: *Critical*

---

### **B04: Lost Updates in Account Balance Credit/Debit**
- **File:** `src/main/java/com/lyxor/persistence/model/AccountEntity.java`
- **Trigger:** Concurrent credit requests on the same account.
- **Expected:** Sequential or optimistic lock-protected updates ensure every credit amount is recorded.
- **Actual:** In-memory read-modify-write `this.balance = this.balance.add(amount)` loses updates under parallel threads.
- **Reproduction:** Run 10 parallel threads each crediting \$100 to an account with initial balance \$0. Resulting balance is < \$1000.
- **Classification:** Static Detection: *Low* | Concurrency: *High*

---

### **B05: Detached Entity Unsaved In-Memory Audit Modification**
- **File:** `src/main/java/com/lyxor/persistence/service/TransactionAuditService.java`
- **Trigger:** Calling `markTransactionAudited(txId, note)`.
- **Expected:** Audit note and `SETTLED` status are persisted to database.
- **Actual:** Modifies detached entity instance and returns `true` without calling `transactionRepository.save()`. Entity state is not updated in the persistent store.
- **Reproduction:** Call `markTransactionAudited(txId, note)`, reload transaction from repository; audit tags remain empty.
- **Classification:** Static Detection: *Low* | JPA State: *High*

---

### **B06: 1-Based Pagination Offset Skipping First Page**
- **File:** `src/main/java/com/lyxor/persistence/controller/LedgerController.java`
- **Trigger:** Calling `/api/v1/ledger/transactions?page=1&size=10`.
- **Expected:** Returns first page (items 0 to 9).
- **Actual:** Multiplies `offset = 1 * 10 = 10`, completely skipping items 0 to 9.
- **Reproduction:** Request page 1 with 15 records; only records 10 to 14 are returned.
- **Classification:** Static Detection: *Low* | API Contract: *High*

---

### **B07: Transactional Isolation Read Skew Under Concurrent Transfer**
- **File:** `src/main/java/com/lyxor/persistence/service/AccountTransferService.java`
- **Trigger:** Two parallel transfer requests against the same source account with exact funds.
- **Expected:** Second transfer fails balance validation.
- **Actual:** Both threads read positive balance before either commits debit, resulting in negative balance overdrafts.
- **Reproduction:** Balance = \$500. Submit two \$400 transfer requests simultaneously; both succeed, leaving balance at -\$300.
- **Classification:** Static Detection: *Low* | Concurrency / Database: *Very High*

---

### **B08: BigDecimal Zero-Balance Equality Check Scale Sensitivity**
- **File:** `src/main/java/com/lyxor/persistence/repository/AccountRepository.java`
- **Trigger:** Querying accounts with zero balance when accounts hold `new BigDecimal("0.00")`.
- **Expected:** `findAccountsWithZeroBalance()` matches all accounts whose numerical value is zero.
- **Actual:** `BigDecimal.equals(BigDecimal.ZERO)` checks scale (`scale 2` vs `scale 0`), returning `false` for `0.00`.
- **Reproduction:** Save account with balance `new BigDecimal("0.00")`. Call `findAccountsWithZeroBalance()`. Returns empty list.
- **Classification:** Static Detection: *Low* | Arithmetic: *High*

---

### **B09: Mutable Audit Tags Collection Exposure**
- **File:** `src/main/java/com/lyxor/persistence/model/LedgerTransactionEntity.java`
- **Trigger:** Client code calling `transaction.getAuditTags().clear()`.
- **Expected:** Audit tags cannot be mutated externally without repository authorization.
- **Actual:** Direct mutable collection reference is returned and altered in-memory.
- **Reproduction:** Retrieve transaction entity, call `getAuditTags().add("UNVERIFIED")`. Changes reflect across all references.
- **Classification:** Static Detection: *Low* | Data Integrity: *Medium*

---

### **B10: Premature Transaction Status Mutation Before Persistence Flush**
- **File:** `src/main/java/com/lyxor/persistence/service/AccountTransferService.java`
- **Trigger:** Transaction status updated in memory before repository save failure.
- **Expected:** Entity in memory reflects failed status or remains unchanged upon exception.
- **Actual:** `transaction.setStatus(TransactionStatus.SETTLED)` executes before repository save. If save fails, caller catches exception but entity retains `SETTLED` status in local cache.
- **Reproduction:** Cause repository save failure; inspect `transaction.getStatus()` → `SETTLED`.
- **Classification:** Static Detection: *Low* | Recovery: *High*
