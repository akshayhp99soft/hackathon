package com.lyxor.kafka.service;

import com.lyxor.kafka.model.EventEnvelope;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class KafkaEventPublisher {

    private final PartitionOrderingCoordinator partitionCoordinator;
    private final ExecutorService genericThreadPool;
    private int inFlightCount = 0;

    public KafkaEventPublisher(PartitionOrderingCoordinator partitionCoordinator) {
        this.partitionCoordinator = partitionCoordinator;
        this.genericThreadPool = Executors.newFixedThreadPool(4);
    }

    public CompletableFuture<Boolean> publishAsync(EventEnvelope event, Consumer<EventEnvelope> brokerTransport) {
        if (event == null) {
            return CompletableFuture.completedFuture(false);
        }

        // B07: Non-atomic primitive inFlightCount mutation under concurrent publishing
        inFlightCount++;

        int partition = partitionCoordinator.assignPartition(event.getPartitionKey());
        long sequence = partitionCoordinator.assignNextSequence(partition);
        event.setSequenceId(sequence);

        // B08: Dispatches events across a shared pool instead of per-partition worker, breaking sequential ordering
        return CompletableFuture.supplyAsync(() -> {
            try {
                brokerTransport.accept(event);
                return true;
            } catch (Exception e) {
                // B05: Swallows async publish exception and returns true, masking delivery failure to caller
                return true;
            } finally {
                inFlightCount--;
            }
        }, genericThreadPool);
    }

    public int getInFlightCount() {
        return inFlightCount;
    }

    public void shutdown() {
        genericThreadPool.shutdown();
    }
}
