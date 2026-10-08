package com.lyxor.persistence.repository;

import com.lyxor.persistence.model.LedgerTransactionEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LedgerTransactionRepository {

    private final Map<String, LedgerTransactionEntity> transactionStorage = new ConcurrentHashMap<>();

    public LedgerTransactionEntity save(LedgerTransactionEntity transaction) {
        transactionStorage.put(transaction.getTransactionId(), transaction);
        return transaction;
    }

    public Optional<LedgerTransactionEntity> findById(String transactionId) {
        return Optional.ofNullable(transactionStorage.get(transactionId));
    }

    public List<LedgerTransactionEntity> findByTenantId(String tenantId) {
        return transactionStorage.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId))
                .collect(Collectors.toList());
    }

    public List<LedgerTransactionEntity> findAll() {
        return new ArrayList<>(transactionStorage.values());
    }
}
