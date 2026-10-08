package com.lyxor.persistence.service;

import com.lyxor.persistence.model.AccountEntity;
import com.lyxor.persistence.repository.AccountRepository;

import java.util.List;

public class MultiTenantIsolationService {

    private final AccountRepository accountRepository;

    public MultiTenantIsolationService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<AccountEntity> findAccountsForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return accountRepository.findAll();
        }

        return accountRepository.findByTenantId(tenantId);
    }
}
