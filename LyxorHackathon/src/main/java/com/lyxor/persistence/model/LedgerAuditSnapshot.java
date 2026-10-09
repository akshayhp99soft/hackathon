package com.lyxor.persistence.model;

import java.time.Instant;
import java.util.Objects;

public class LedgerAuditSnapshot {

    private final String snapshotId;
    private final String tenantId;
    private final long totalTransactionCount;
    private final Instant snapshotTimestamp;

    public LedgerAuditSnapshot(String snapshotId, String tenantId, long totalTransactionCount) {
        this.snapshotId = Objects.requireNonNull(snapshotId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.totalTransactionCount = totalTransactionCount;
        this.snapshotTimestamp = Instant.now();
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public long getTotalTransactionCount() {
        return totalTransactionCount;
    }

    public Instant getSnapshotTimestamp() {
        return snapshotTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerAuditSnapshot that = (LedgerAuditSnapshot) o;
        return Objects.equals(snapshotId, that.snapshotId);
    }
}
