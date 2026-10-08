package com.lyxor.kafka;

import com.lyxor.kafka.controller.EventStreamingController;
import com.lyxor.kafka.model.EventEnvelope;
import com.lyxor.kafka.model.ProcessingRecord;
import com.lyxor.kafka.service.DeadLetterQueueHandler;
import com.lyxor.kafka.service.IdempotentEventConsumer;
import com.lyxor.kafka.service.KafkaEventPublisher;
import com.lyxor.kafka.service.PartitionOrderingCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class KafkaPipelineTests {

    @Test
    void testPartitionAssignment() {
        PartitionOrderingCoordinator coordinator = new PartitionOrderingCoordinator(4);
        int partition = coordinator.assignPartition("user-100");
        assertTrue(partition >= 0 && partition < 4);
        assertEquals(1, coordinator.assignNextSequence(partition));
    }

    @Test
    void testKafkaPublisherAsync() throws ExecutionException, InterruptedException {
        PartitionOrderingCoordinator coordinator = new PartitionOrderingCoordinator(4);
        KafkaEventPublisher publisher = new KafkaEventPublisher(coordinator);
        EventEnvelope event = new EventEnvelope("EVT-1", "orders", "key-1", "{}", Map.of("version", "1"));

        AtomicBoolean transported = new AtomicBoolean(false);
        CompletableFuture<Boolean> future = publisher.publishAsync(event, e -> transported.set(true));

        assertTrue(future.get());
        assertTrue(transported.get());
        publisher.shutdown();
    }

    @Test
    void testIdempotentConsumerDeduplication() throws InterruptedException {
        IdempotentEventConsumer consumer = new IdempotentEventConsumer();
        EventEnvelope event = new EventEnvelope("EVT-DUP-1", "orders", "key-1", "{}", Map.of());

        consumer.onMessageReceived(event, 100L, e -> {});
        Thread.sleep(50);

        assertEquals(100L, consumer.getCommittedOffset("orders"));
    }

    @Test
    void testDeadLetterQueueRouting() {
        DeadLetterQueueHandler dlq = new DeadLetterQueueHandler(2, 100);
        EventEnvelope event = new EventEnvelope("EVT-FAIL-1", "orders", "key-1", "{}", Map.of());
        ProcessingRecord record = new ProcessingRecord("EVT-FAIL-1");
        record.incrementRetry();
        record.incrementRetry(); // retryCount = 2

        boolean sentToDlq = dlq.handleFailedEvent(event, record, new RuntimeException("Fatal parser error"));
        assertTrue(sentToDlq);
        assertEquals(1, dlq.getDeadLetterCount());
    }

    @Test
    void testEventStreamingControllerEndpoints() {
        EventStreamingController controller = new EventStreamingController();
        ResponseEntity<Map<String, Object>> pubResponse = controller.publishEvent(Map.of(
                "topic", "test-topic",
                "key", "test-key",
                "payload", "{\"status\":\"ok\"}"
        ));
        assertEquals(200, pubResponse.getStatusCode().value());

        ResponseEntity<Map<String, Object>> offsetResponse = controller.getOffset("test-topic");
        assertEquals(200, offsetResponse.getStatusCode().value());
    }
}
