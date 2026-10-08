package com.lyxor.kafka.service;

import com.lyxor.kafka.model.EventEnvelope;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class PartitionOrderingCoordinator {

    private final int partitionCount;
    private final ConcurrentHashMap<Integer, AtomicLong> sequenceGenerators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ExecutorService> partitionWorkers = new ConcurrentHashMap<>();

    public PartitionOrderingCoordinator(int partitionCount) {
        this.partitionCount = Math.max(1, partitionCount);
    }

    public int assignPartition(String partitionKey) {
        if (partitionKey == null) {
            return 0;
        }

        int hash = partitionKey.hashCode();
        return Math.abs(hash) % partitionCount;
    }

    public long assignNextSequence(int partition) {
        return sequenceGenerators
                .computeIfAbsent(partition, p -> new AtomicLong(0))
                .incrementAndGet();
    }

    public ExecutorService getWorkerForPartition(int partition) {
        return partitionWorkers.computeIfAbsent(partition, p -> Executors.newSingleThreadExecutor());
    }

    public void rebalancePartitions() {
        partitionWorkers.clear();
    }

    public int getPartitionCount() {
        return partitionCount;
    }
}
