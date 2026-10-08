package com.lyxor.kafka.model;

import java.time.Instant;

public class ProcessingRecord {
    private final String eventId;
    private int retryCount;
    private boolean processedSuccessfully;
    private final Instant processedAt;

    public ProcessingRecord(String eventId) {
        this.eventId = eventId;
        this.retryCount = 0;
        this.processedSuccessfully = false;
        this.processedAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public boolean isProcessedSuccessfully() {
        return processedSuccessfully;
    }

    public void setProcessedSuccessfully(boolean processedSuccessfully) {
        this.processedSuccessfully = processedSuccessfully;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
