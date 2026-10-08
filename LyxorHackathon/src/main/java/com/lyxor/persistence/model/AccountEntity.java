package com.lyxor.persistence.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class AccountEntity {
    private final String accountId;
    private final String tenantId;
    private final String currency;
    private BigDecimal balance;
    private final Instant createdAt;
    private Instant updatedAt;

    public AccountEntity(String accountId, String tenantId, String currency, BigDecimal balance) {
        this.accountId = Objects.requireNonNull(accountId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.currency = Objects.requireNonNull(currency);
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
        this.updatedAt = Instant.now();
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
