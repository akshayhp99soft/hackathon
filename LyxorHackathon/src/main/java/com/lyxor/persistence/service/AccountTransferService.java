package com.lyxor.persistence.service;

import com.lyxor.persistence.model.AccountEntity;
import com.lyxor.persistence.model.LedgerTransactionEntity;
import com.lyxor.persistence.model.TransactionStatus;
import com.lyxor.persistence.repository.AccountRepository;
import com.lyxor.persistence.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountTransferService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;

    public AccountTransferService(AccountRepository accountRepository, LedgerTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public LedgerTransactionEntity executeTransfer(String tenantId, String fromAccountId, String toAccountId, BigDecimal amount) throws IOException {
        return this.applyTransferInternal(tenantId, fromAccountId, toAccountId, amount);
    }

    @Transactional
    public LedgerTransactionEntity applyTransferInternal(String tenantId, String fromAccountId, String toAccountId, BigDecimal amount) throws IOException {
        AccountEntity source = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + fromAccountId));
        AccountEntity target = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Target account not found: " + toAccountId));

        if (source.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in source account");
        }

        LedgerTransactionEntity transaction = new LedgerTransactionEntity(
                UUID.randomUUID().toString(), tenantId, fromAccountId, toAccountId, amount
        );

        source.debit(amount);
        accountRepository.save(source);

        if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            throw new IOException("High-value wire settlement gateway timeout");
        }

        target.credit(amount);
        accountRepository.save(target);

        transaction.setStatus(TransactionStatus.SETTLED);
        transactionRepository.save(transaction);

        return transaction;
    }
}
