package com.lyxor.persistence.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LedgerTransactionEntity {
    private final String transactionId;
    private final String tenantId;
    private final String sourceAccountId;
    private final String targetAccountId;
    private final BigDecimal amount;
    private TransactionStatus status;
    private final List<String> auditTags;
    private final Instant timestamp;

    public LedgerTransactionEntity(String transactionId, String tenantId, String sourceAccountId, String targetAccountId, BigDecimal amount) {
        this.transactionId = Objects.requireNonNull(transactionId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = amount;
        this.status = TransactionStatus.PENDING;
        this.auditTags = new ArrayList<>();
        this.timestamp = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public String getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public List<String> getAuditTags() {
        return auditTags;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
