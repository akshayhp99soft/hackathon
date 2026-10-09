package com.lyxor.persistence.repository;

import com.lyxor.persistence.model.LedgerTransactionEntity;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class LedgerTransactionRepository {

    private final Map<String, LedgerTransactionEntity> transactionStorage = new ConcurrentHashMap<>();

    public LedgerTransactionEntity save(LedgerTransactionEntity transaction) {
        if (transaction != null) {
            transactionStorage.put(transaction.getTransactionId(), transaction);
        }
        return transaction;
    }

    public Optional<LedgerTransactionEntity> findById(String transactionId) {
        if (transactionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(transactionStorage.get(transactionId));
    }

    public List<LedgerTransactionEntity> findByTenantId(String tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return transactionStorage.values().stream()
                .filter(t -> tenantId.equals(t.getTenantId()))
                .collect(Collectors.toList());
    }

    public List<LedgerTransactionEntity> findAll() {
        return new ArrayList<>(transactionStorage.values());
    }
}
