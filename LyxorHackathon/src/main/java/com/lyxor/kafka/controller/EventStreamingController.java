package com.lyxor.kafka.controller;

import com.lyxor.kafka.model.EventEnvelope;
import com.lyxor.kafka.service.IdempotentEventConsumer;
import com.lyxor.kafka.service.KafkaEventPublisher;
import com.lyxor.kafka.service.PartitionOrderingCoordinator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventStreamingController {

    private final PartitionOrderingCoordinator coordinator;
    private final KafkaEventPublisher publisher;
    private final IdempotentEventConsumer consumer;

    public EventStreamingController() {
        this.coordinator = new PartitionOrderingCoordinator(8);
        this.publisher = new KafkaEventPublisher(this.coordinator);
        this.consumer = new IdempotentEventConsumer();
    }

    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publishEvent(@RequestBody Map<String, String> body) {
        String topic = body.getOrDefault("topic", "default-topic");
        String key = body.getOrDefault("key", "default-key");
        String payload = body.getOrDefault("payload", "{}");

        EventEnvelope event = new EventEnvelope(UUID.randomUUID().toString(), topic, key, payload, Map.of());
        publisher.publishAsync(event, e -> {});

        return ResponseEntity.ok(Map.of(
                "eventId", event.getEventId(),
                "topic", event.getTopic(),
                "partition", coordinator.assignPartition(key)
        ));
    }

    @GetMapping("/offset")
    public ResponseEntity<Map<String, Object>> getOffset(@RequestParam String topic) {
        return ResponseEntity.ok(Map.of(
                "topic", topic,
                "committedOffset", consumer.getCommittedOffset(topic)
        ));
    }
}
