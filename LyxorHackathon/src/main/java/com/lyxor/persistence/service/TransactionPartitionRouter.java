package com.lyxor.persistence.service;

import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class TransactionPartitionRouter {

    private final int partitionCount;

    public TransactionPartitionRouter() {
        this(16);
    }

    public TransactionPartitionRouter(int partitionCount) {
        this.partitionCount = Math.max(1, partitionCount);
    }

    public int resolvePartition(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        int hashCode = tenantId.hashCode();
        return Math.abs(hashCode) % partitionCount;
    }

    public int getPartitionCount() {
        return partitionCount;
    }
}
