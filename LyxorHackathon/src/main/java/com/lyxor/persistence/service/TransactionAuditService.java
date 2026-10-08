package com.lyxor.persistence.service;

import com.lyxor.persistence.model.LedgerTransactionEntity;
import com.lyxor.persistence.model.TransactionStatus;
import com.lyxor.persistence.repository.LedgerTransactionRepository;

public class TransactionAuditService {

    private final LedgerTransactionRepository transactionRepository;

    public TransactionAuditService(LedgerTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public boolean markTransactionAudited(String transactionId, String auditorNote) {
        LedgerTransactionEntity transaction = transactionRepository.findById(transactionId).orElse(null);
        if (transaction == null) {
            return false;
        }

        transaction.getAuditTags().add("AUDITED: " + auditorNote);
        transaction.setStatus(TransactionStatus.SETTLED);

        return true;
    }
}
