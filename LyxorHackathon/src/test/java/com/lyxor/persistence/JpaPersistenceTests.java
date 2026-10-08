package com.lyxor.persistence;

import com.lyxor.persistence.controller.LedgerController;
import com.lyxor.persistence.model.AccountEntity;
import com.lyxor.persistence.model.LedgerTransactionEntity;
import com.lyxor.persistence.model.TransactionStatus;
import com.lyxor.persistence.repository.AccountRepository;
import com.lyxor.persistence.repository.LedgerTransactionRepository;
import com.lyxor.persistence.service.AccountTransferService;
import com.lyxor.persistence.service.MultiTenantIsolationService;
import com.lyxor.persistence.service.TransactionAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JpaPersistenceTests {

    private AccountRepository accountRepository;
    private LedgerTransactionRepository transactionRepository;
    private AccountTransferService transferService;
    private MultiTenantIsolationService tenantService;
    private TransactionAuditService auditService;
    private LedgerController ledgerController;

    @BeforeEach
    void setUp() {
        accountRepository = new AccountRepository();
        transactionRepository = new LedgerTransactionRepository();
        transferService = new AccountTransferService(accountRepository, transactionRepository);
        tenantService = new MultiTenantIsolationService(accountRepository);
        auditService = new TransactionAuditService(transactionRepository);
        ledgerController = new LedgerController(transactionRepository);
    }

    @Test
    void testAccountPersistenceAndRetrieval() {
        AccountEntity account = new AccountEntity("ACC-100", "TENANT-A", "USD", BigDecimal.valueOf(5000));
        accountRepository.save(account);

        assertTrue(accountRepository.findById("ACC-100").isPresent());
        assertEquals(BigDecimal.valueOf(5000), accountRepository.findById("ACC-100").get().getBalance());
    }

    @Test
    void testSuccessfulAccountTransfer() throws IOException {
        accountRepository.save(new AccountEntity("SRC-1", "TENANT-A", "USD", BigDecimal.valueOf(1000)));
        accountRepository.save(new AccountEntity("DST-1", "TENANT-A", "USD", BigDecimal.valueOf(200)));

        LedgerTransactionEntity tx = transferService.executeTransfer("TENANT-A", "SRC-1", "DST-1", BigDecimal.valueOf(300));
        assertNotNull(tx);
        assertEquals(TransactionStatus.SETTLED, tx.getStatus());
        assertEquals(BigDecimal.valueOf(700), accountRepository.findById("SRC-1").get().getBalance());
        assertEquals(BigDecimal.valueOf(500), accountRepository.findById("DST-1").get().getBalance());
    }

    @Test
    void testTenantIsolationQuery() {
        accountRepository.save(new AccountEntity("ACC-T1", "TENANT-1", "USD", BigDecimal.valueOf(100)));
        accountRepository.save(new AccountEntity("ACC-T2", "TENANT-2", "USD", BigDecimal.valueOf(200)));

        List<AccountEntity> t1Accounts = tenantService.findAccountsForTenant("TENANT-1");
        assertEquals(1, t1Accounts.size());
    }

    @Test
    void testTransactionAuditService() {
        LedgerTransactionEntity tx = new LedgerTransactionEntity("TX-1", "T1", "A1", "A2", BigDecimal.valueOf(50));
        transactionRepository.save(tx);

        assertTrue(auditService.markTransactionAudited("TX-1", "Verified by compliance"));
    }

    @Test
    void testLedgerControllerPagination() {
        transactionRepository.save(new LedgerTransactionEntity("TX-A", "T1", "A1", "A2", BigDecimal.valueOf(10)));
        ResponseEntity<List<LedgerTransactionEntity>> response = ledgerController.getTransactions(0, 10);
        assertEquals(200, response.getStatusCode().value());
    }
}
