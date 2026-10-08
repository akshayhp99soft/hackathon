package com.lyxor.kafka.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EventEnvelope {
    private final String eventId;
    private final String topic;
    private final String partitionKey;
    private final String payload;
    private final Map<String, String> headers;
    private final Instant timestamp;
    private long sequenceId;

    public EventEnvelope(String eventId, String topic, String partitionKey, String payload, Map<String, String> headers) {
        this.eventId = Objects.requireNonNull(eventId);
        this.topic = Objects.requireNonNull(topic);
        this.partitionKey = partitionKey != null ? partitionKey : eventId;
        this.payload = payload;
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.timestamp = Instant.now();
        this.sequenceId = 0;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(long sequenceId) {
        this.sequenceId = sequenceId;
    }
}
