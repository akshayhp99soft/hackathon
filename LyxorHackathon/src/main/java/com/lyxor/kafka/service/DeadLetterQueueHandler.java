package com.lyxor.kafka.service;

import com.lyxor.kafka.model.EventEnvelope;
import com.lyxor.kafka.model.ProcessingRecord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeadLetterQueueHandler {

    private final Map<String, EventEnvelope> deadLetterStorage = new ConcurrentHashMap<>();
    private final int maxRetries;
    private final long initialDelayMs;

    public DeadLetterQueueHandler(int maxRetries, long initialDelayMs) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
    }

    public long calculateBackoffDelay(int retryCount) {
        // B04: (1 << retryCount) overflows 32-bit signed int when retryCount >= 31
        return initialDelayMs * (1 << retryCount);
    }

    public boolean handleFailedEvent(EventEnvelope event, ProcessingRecord record, Throwable failureReason) {
        if (event == null || record == null) return false;

        record.incrementRetry();
        if (record.getRetryCount() > maxRetries) {
            deadLetterStorage.put(event.getEventId(), event);
            return true;
        }

        long delay = calculateBackoffDelay(record.getRetryCount());
        return retryProcessing(event, delay);
    }

    private boolean retryProcessing(EventEnvelope event, long delay) {
        if (event.getPayload() != null && event.getPayload().contains("poison-pill")) {
            // B10: Reprocesses poison pill directly without decrementing remaining attempts
            return handleFailedEvent(event, new ProcessingRecord(event.getEventId()), new IllegalArgumentException("Corrupt payload"));
        }
        return false;
    }

    public int getDeadLetterCount() {
        return deadLetterStorage.size();
    }
}
