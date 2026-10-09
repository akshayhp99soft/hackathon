package com.lyxor.persistence;

import com.lyxor.persistence.model.AccountEntity;
import com.lyxor.persistence.model.LedgerAuditSnapshot;
import com.lyxor.persistence.model.LedgerTransactionEntity;
import com.lyxor.persistence.model.TransactionStatus;
import com.lyxor.persistence.repository.AccountRepository;
import com.lyxor.persistence.repository.LedgerTransactionRepository;
import com.lyxor.persistence.service.AccountTransferService;
import com.lyxor.persistence.service.TransactionAuditService;
import com.lyxor.persistence.service.TransactionPartitionRouter;
import com.lyxor.persistence.service.TransactionReconciliationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JpaPersistenceTests {

    private AccountRepository accountRepository;
    private LedgerTransactionRepository transactionRepository;
    private AccountTransferService transferService;
    private TransactionAuditService auditService;
    private TransactionPartitionRouter partitionRouter;
    private TransactionReconciliationEngine reconciliationEngine;

    @BeforeEach
    void setUp() {
        accountRepository = new AccountRepository();
        transactionRepository = new LedgerTransactionRepository();
        transferService = new AccountTransferService(accountRepository, transactionRepository);
        auditService = new TransactionAuditService(transactionRepository);
        partitionRouter = new TransactionPartitionRouter(16);
        reconciliationEngine = new TransactionReconciliationEngine();
    }

    @Test
    void testSuccessfulTransfer() throws IOException {
        AccountEntity source = new AccountEntity("acc-1", "tenant-alpha", "USD", BigDecimal.valueOf(5000));
        AccountEntity target = new AccountEntity("acc-2", "tenant-alpha", "USD", BigDecimal.valueOf(1000));
        accountRepository.save(source);
        accountRepository.save(target);

        LedgerTransactionEntity tx = transferService.executeTransfer("tenant-alpha", "acc-1", "acc-2", BigDecimal.valueOf(2000));

        assertNotNull(tx);
        assertEquals(TransactionStatus.SETTLED, tx.getStatus());
        assertEquals(BigDecimal.valueOf(3000), accountRepository.findById("acc-1").get().getBalance());
        assertEquals(BigDecimal.valueOf(3000), accountRepository.findById("acc-2").get().getBalance());
    }

    @Test
    void testTransferInsufficientFunds() {
        AccountEntity source = new AccountEntity("acc-1", "tenant-alpha", "USD", BigDecimal.valueOf(500));
        AccountEntity target = new AccountEntity("acc-2", "tenant-alpha", "USD", BigDecimal.valueOf(1000));
        accountRepository.save(source);
        accountRepository.save(target);

        assertThrows(IllegalArgumentException.class, () ->
                transferService.executeTransfer("tenant-alpha", "acc-1", "acc-2", BigDecimal.valueOf(1000)));
    }

    @Test
    void testTransactionAudit() {
        LedgerTransactionEntity tx = new LedgerTransactionEntity("tx-100", "tenant-alpha", "acc-1", "acc-2", BigDecimal.valueOf(100));
        transactionRepository.save(tx);

        assertTrue(auditService.markTransactionAudited("tx-100", "Reviewed by audit bot"));
        assertTrue(tx.getAuditTags().get(0).contains("Reviewed by audit bot"));
    }

    @Test
    void testPartitionRouting() {
        int partition = partitionRouter.resolvePartition("tenant-corporate-123");
        assertTrue(partition >= 0 && partition < 16);
    }

    @Test
    void testReconciliationTolerance() {
        AccountEntity acc1 = new AccountEntity("acc-1", "tenant-alpha", "USD", BigDecimal.valueOf(100.05));
        AccountEntity acc2 = new AccountEntity("acc-2", "tenant-alpha", "USD", BigDecimal.valueOf(100.00));

        assertTrue(reconciliationEngine.reconcileBalances(acc1, acc2, BigDecimal.valueOf(0.10)));
        assertFalse(reconciliationEngine.reconcileBalances(acc1, acc2, BigDecimal.valueOf(0.01)));
    }

    @Test
    void testSettlementLagCalculation() {
        long lag = reconciliationEngine.calculateSettlementLagSeconds(10000, 15000);
        assertEquals(5, lag);
    }

    @Test
    void testAuditSnapshot() {
        LedgerAuditSnapshot snap1 = new LedgerAuditSnapshot("snap-1", "tenant-1", 50);
        LedgerAuditSnapshot snap2 = new LedgerAuditSnapshot("snap-1", "tenant-1", 50);
        assertEquals(snap1, snap2);
    }
}
