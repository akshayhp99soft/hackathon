package com.lyxor.persistence.service;

import com.lyxor.persistence.model.LedgerTransactionEntity;
import com.lyxor.persistence.model.TransactionStatus;
import com.lyxor.persistence.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TransactionAuditService {

    private final LedgerTransactionRepository transactionRepository;

    public TransactionAuditService(LedgerTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public boolean markTransactionAudited(String transactionId, String auditorNote) {
        Optional<LedgerTransactionEntity> txOpt = transactionRepository.findById(transactionId);
        if (txOpt.isEmpty()) {
            return false;
        }

        LedgerTransactionEntity transaction = txOpt.get();
        transaction.getAuditTags().add("AUDITED: " + auditorNote);
        transaction.setStatus(TransactionStatus.SETTLED);

        return true;
    }
}
