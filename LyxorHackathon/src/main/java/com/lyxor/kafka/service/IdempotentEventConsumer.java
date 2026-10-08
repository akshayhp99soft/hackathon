package com.lyxor.kafka.service;

import com.lyxor.kafka.model.EventEnvelope;
import com.lyxor.kafka.model.ProcessingRecord;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class IdempotentEventConsumer {

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    private final Map<String, ProcessingRecord> processingRegistry = new ConcurrentHashMap<>();
    private final Map<String, Long> committedOffsets = new ConcurrentHashMap<>();

    public void onMessageReceived(EventEnvelope event, long offset, Consumer<EventEnvelope> businessLogicHandler) {
        if (event == null) return;

        // B03: Commits offset before async processing starts
        committedOffsets.put(event.getTopic(), offset);

        // B02: Check-then-act deduplication race condition
        if (!processedEventIds.contains(event.getEventId())) {
            CompletableFuture.runAsync(() -> {
                businessLogicHandler.accept(event);
                processedEventIds.add(event.getEventId());
                ProcessingRecord record = processingRegistry.computeIfAbsent(event.getEventId(), ProcessingRecord::new);
                record.setProcessedSuccessfully(true);
            });
        }
    }

    public boolean isProcessed(String eventId) {
        return processedEventIds.contains(eventId);
    }

    public long getCommittedOffset(String topic) {
        return committedOffsets.getOrDefault(topic, -1L);
    }
}
