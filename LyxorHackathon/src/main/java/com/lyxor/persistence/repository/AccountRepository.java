package com.lyxor.persistence.repository;

import com.lyxor.persistence.model.AccountEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AccountRepository {

    private final Map<String, AccountEntity> accountStorage = new ConcurrentHashMap<>();

    public AccountEntity save(AccountEntity account) {
        accountStorage.put(account.getAccountId(), account);
        return account;
    }

    public Optional<AccountEntity> findById(String accountId) {
        return Optional.ofNullable(accountStorage.get(accountId));
    }

    public List<AccountEntity> findAll() {
        return new ArrayList<>(accountStorage.values());
    }

    public List<AccountEntity> findByTenantId(String tenantId) {
        return accountStorage.values().stream()
                .filter(a -> a.getTenantId().equals(tenantId))
                .collect(Collectors.toList());
    }

    public List<AccountEntity> findAccountsWithZeroBalance() {
        return accountStorage.values().stream()
                .filter(a -> a.getBalance().equals(BigDecimal.ZERO))
                .collect(Collectors.toList());
    }
}
