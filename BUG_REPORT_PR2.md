# Lyxor Hackathon — Master Bug Inventory: PR 2 (JPA Persistence & Transactions)

**Branch:** `feature/jpa-persistence-and-transactions`  
**Target:** `main`  
**Scope:** JPA Persistence, Account Transfers, Transaction Boundaries, Partition Routing, Reconciliation, and Ledger Auditing.

---

## Bug Inventory Summary

| ID | Category | File | Method / Line | Difficulty | Type / Stealth Rationale |
|---|---|---|---|:---:|---|
| **B01** | Quality / Object Contract | `com.lyxor.persistence.model.LedgerAuditSnapshot` | `equals` | **Low** | **[DECOY 1]** Overrides `equals()` on `snapshotId` without overriding `hashCode()`. Placed deliberately to bait automated reviewers into flagging a harmless object contract smell. |
| **B02** | Boundary / Contract | `com.lyxor.persistence.controller.LedgerController` | `getTransactions` | **Low** | **[DECOY 2]** Uses `(page - 1) * size` for offset without upper bound clamping on page size, serving as a secondary decoy finding. |
| **B03** | Architecture / AOP Proxy Bypass | `com.lyxor.persistence.service.AccountTransferService` | `executeTransfer` | **Extreme** | **[STEALTH]** Direct `this.applyTransferInternal(...)` self-invocation bypasses Spring's CGLIB/JDK dynamic proxy, running the method outside any active database transaction. |
| **B04** | Transaction / Rollback Policy | `com.lyxor.persistence.service.AccountTransferService` | `applyTransferInternal` | **Very High** | **[STEALTH]** Standard `@Transactional` without `rollbackFor = Exception.class`. Checked `IOException` triggers no rollback, leaving source debit committed while target credit fails. |
| **B05** | Arithmetic / Scale Sensitivity | `com.lyxor.persistence.repository.AccountRepository` | `findAccountsWithZeroBalance` | **Extreme** | **[STEALTH]** Uses `balance.equals(BigDecimal.ZERO)` instead of `compareTo() == 0`. Fails to match accounts with balance `new BigDecimal("0.00")` due to scale inequality (scale 2 != scale 0). |
| **B06** | State Management / JPA Lifecycle | `com.lyxor.persistence.service.TransactionAuditService` | `markTransactionAudited` | **Very High** | **[STEALTH]** Mutates detached entity fields (`getAuditTags().add(...)`, `setStatus(SETTLED)`) and returns `true` without calling `transactionRepository.save()`. Changes are discarded upon method exit. |
| **B07** | Arithmetic / Partition Overflow | `com.lyxor.persistence.service.TransactionPartitionRouter` | `resolvePartition` | **Extreme** | **[STEALTH]** Computes `Math.abs(tenantId.hashCode()) % partitionCount`. For tenants where `hashCode() == Integer.MIN_VALUE`, `Math.abs` returns negative `-2147483648`, throwing `ArrayIndexOutOfBoundsException`. |
| **B08** | Performance / Division Truncation | `com.lyxor.persistence.service.TransactionReconciliationEngine` | `calculateSettlementLagSeconds` | **High** | **[STEALTH]** Integer division `(completedMs - startMs) / 1000` truncates sub-second settlement durations to `0` seconds, systematically misreporting latency metrics. |
| **B09** | Encapsulation / Memory Escape | `com.lyxor.persistence.model.LedgerTransactionEntity` | `getAuditTags` | **High** | **[STEALTH]** Returns direct mutable `List<String>` reference rather than `Collections.unmodifiableList()`, permitting callers to mutate audited financial audit trails directly. |
| **B10** | Concurrency / Premature State | `com.lyxor.persistence.service.AccountTransferService` | `applyTransferInternal` | **High** | **[STEALTH]** In-memory entity status set to `TransactionStatus.SETTLED` prior to persistence `save()`. If save throws an exception, the in-memory entity retained in cache reflects `SETTLED`. |

---

## Detailed Bug Breakdown & Reproduction Guide

### **B01: [DECOY] `LedgerAuditSnapshot` Equals Without HashCode**
- **File:** `src/main/java/com/lyxor/persistence/model/LedgerAuditSnapshot.java`
- **Trigger:** Adding snapshots to a `HashSet` or using them as `HashMap` keys.
- **Expected:** Consistent lookup and hashing.
- **Actual:** Equals checks `snapshotId` but uses default `System.identityHashCode()`.

---

### **B02: [DECOY] Unbounded Transaction Pagination Request**
- **File:** `src/main/java/com/lyxor/persistence/controller/LedgerController.java`
- **Trigger:** Calling `/api/v1/ledger/transactions?size=50000`.
- **Expected:** Bounded pagination clamping.
- **Actual:** Accepts arbitrary page sizes.

---

### **B03: [STEALTH] Spring AOP Proxy Self-Invocation Transaction Bypass**
- **File:** `src/main/java/com/lyxor/persistence/service/AccountTransferService.java`
- **Trigger:** Invoking `executeTransfer()`.
- **Expected:** Execution within an active Spring database transaction.
- **Actual:** Calling `this.applyTransferInternal()` bypasses Spring's proxy; `TransactionSynchronizationManager.isActualTransactionActive()` is `false`.

---

### **B04: [STEALTH] Checked Exception Commit on Gateway Timeout**
- **File:** `src/main/java/com/lyxor/persistence/service/AccountTransferService.java`
- **Trigger:** High-value transfer > \$10,000 triggering checked `IOException`.
- **Expected:** Source debit is rolled back.
- **Actual:** Spring default `@Transactional` only rolls back for `RuntimeException`. Source debit remains committed without target credit.

---

### **B05: [STEALTH] `BigDecimal.equals` Scale Sensitivity**
- **File:** `src/main/java/com/lyxor/persistence/repository/AccountRepository.java`
- **Trigger:** Querying zero-balance accounts holding `new BigDecimal("0.00")`.
- **Expected:** Matches all accounts with zero monetary value.
- **Actual:** `equals(BigDecimal.ZERO)` checks both value and scale (`scale 2` vs `scale 0`), returning `false` for `0.00`.

---

### **B06: [STEALTH] Detached Entity Unsaved Audit Mutation**
- **File:** `src/main/java/com/lyxor/persistence/service/TransactionAuditService.java`
- **Trigger:** Calling `markTransactionAudited(txId, note)`.
- **Expected:** Audit note is saved to database.
- **Actual:** Updates detached entity in memory and returns `true` without calling `transactionRepository.save()`.

---

### **B07: [STEALTH] Negative Modulo on `Integer.MIN_VALUE` Partitioning**
- **File:** `src/main/java/com/lyxor/persistence/service/TransactionPartitionRouter.java`
- **Trigger:** Tenant ID whose hash evaluates to `Integer.MIN_VALUE`.
- **Expected:** Valid partition index in `[0, partitionCount - 1]`.
- **Actual:** `Math.abs(-2147483648)` produces `-2147483648`, resulting in negative index and `ArrayIndexOutOfBoundsException`.

---

### **B08: [STEALTH] Sub-Second Settlement Latency Truncation**
- **File:** `src/main/java/com/lyxor/persistence/service/TransactionReconciliationEngine.java`
- **Trigger:** Settlement completing in 850ms.
- **Expected:** Fractional duration or millisecond resolution.
- **Actual:** `850 / 1000` evaluates to integer `0`, corrupting latency metrics.

---

### **B09: [STEALTH] Mutable Audit Tags Collection Leak**
- **File:** `src/main/java/com/lyxor/persistence/model/LedgerTransactionEntity.java`
- **Trigger:** Calling `transaction.getAuditTags().clear()`.
- **Expected:** Encapsulated immutable collection.
- **Actual:** Direct mutable collection reference allows unauthorized alteration of audit trails.

---

### **B10: [STEALTH] Premature In-Memory State Mutation Before DB Flush**
- **File:** `src/main/java/com/lyxor/persistence/service/AccountTransferService.java`
- **Trigger:** Persistence failure on `transactionRepository.save()`.
- **Expected:** Entity reflects actual failed state.
- **Actual:** `transaction.setStatus(SETTLED)` executes prior to save, leaving cached entity in `SETTLED` state.
