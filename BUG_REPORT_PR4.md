# Lyxor Hackathon — Master Bug Inventory: PR 4 (Async Kafka Event Pipeline)

**Branch:** `feature/async-kafka-event-pipeline`  
**Target:** `main`  
**Scope:** Event Streaming Pipeline, Partition Key Routing, Idempotent Consumers, Dead Letter Queue (DLQ), and Async Publisher Callbacks.

---

## Bug Inventory Summary

| ID | Category | File | Method / Line | Difficulty | Why It May Be Missed by Lyxor |
|---|---|---|---|---|---|
| **B01** | Boundary / Algorithm | `com.lyxor.kafka.service.PartitionOrderingCoordinator` | `assignPartition` | **Extreme** | Uses `Math.abs(key.hashCode()) % partitionCount`. For keys hashing to `Integer.MIN_VALUE`, `Math.abs()` returns a negative integer, causing `ArrayIndexOutOfBoundsException`. |
| **B02** | Concurrency / Messaging | `com.lyxor.kafka.service.IdempotentEventConsumer` | `onMessageReceived` | **Very High** | Checks `!processedEventIds.contains(id)` before spawning async worker; the ID is only added *after* async execution completes. Duplicate rapid bursts bypass deduplication and execute multiple times. |
| **B03** | Data / Reliability | `com.lyxor.kafka.service.IdempotentEventConsumer` | `onMessageReceived` | **Very High** | Commits offset to Kafka partition state *before* handing event to async background executor. If the worker crashes or runs out of memory, the event is permanently lost without reprocessing. |
| **B04** | Calculation / Timing | `com.lyxor.kafka.service.DeadLetterQueueHandler` | `calculateBackoffDelay` | **Very High** | Uses `initialDelayMs * (1 << retryCount)`. When `retryCount >= 31`, 32-bit signed bit-shift overflows into negative/zero values, resulting in zero retry delay and CPU spinning. |
| **B05** | Error Handling / Availability | `com.lyxor.kafka.service.KafkaEventPublisher` | `publishAsync` | **Very High** | In async completion callback, catches `Exception e` from broker transport and returns `CompletableFuture.completedFuture(true)`, falsely notifying caller that delivery succeeded. |
| **B06** | Resource Leak / Architecture | `com.lyxor.kafka.service.PartitionOrderingCoordinator` | `rebalancePartitions` | **High** | Clears `partitionWorkers` map without invoking `executor.shutdown()` on active `ExecutorService` instances, leaking orphaned OS worker threads upon every partition rebalance. |
| **B07** | Thread Safety / Metrics | `com.lyxor.kafka.service.KafkaEventPublisher` | `publishAsync` | **Medium** | Mutates `inFlightCount++` and `inFlightCount--` as a primitive `int` without synchronization or `AtomicInteger`. High-concurrency throughput corrupts queue backlog metrics. |
| **B08** | Integration / Event Ordering | `com.lyxor.kafka.service.KafkaEventPublisher` | `publishAsync` | **High** | Submits same-partition events to a generic shared thread pool instead of per-partition serialized workers, causing out-of-order execution across concurrent messages. |
| **B09** | Data Integrity / Object | `com.lyxor.kafka.model.EventEnvelope` | `getHeaders` | **Medium** | Exposes direct mutable `Map<String, String> headers` reference instead of `Collections.unmodifiableMap()`. Consumers mutating headers pollute event metadata across all concurrent listener threads. |
| **B10** | Recovery / Crash | `com.lyxor.kafka.service.DeadLetterQueueHandler` | `retryProcessing` | **High** | Reprocesses poison-pill payload by creating a fresh `ProcessingRecord(0)` on each iteration, causing an infinite recursive retry loop that exhausts memory and thread resources. |

---

## Detailed Bug Breakdown & Reproduction Guide

### **B01: Partition Key Hash Modulo Negative Value Exception**
- **File:** `src/main/java/com/lyxor/kafka/service/PartitionOrderingCoordinator.java`
- **Trigger:** Processing a partition key whose `hashCode()` equals `Integer.MIN_VALUE` (e.g. key `"polygenelubricants"`).
- **Expected:** Maps to a valid non-negative partition index `[0, partitionCount - 1]`.
- **Actual:** In Java, `Math.abs(Integer.MIN_VALUE)` equals `Integer.MIN_VALUE` (overflow). The `%` operation yields a negative index, triggering `ArrayIndexOutOfBoundsException`.
- **Reproduction:** Call `assignPartition("polygenelubricants")` with `partitionCount = 8`.
- **Classification:** Static Detection: *Low* | Boundary / Algorithm: *Extreme*

---

### **B02: Check-Then-Act Idempotency Race in Async Consumer**
- **File:** `src/main/java/com/lyxor/kafka/service/IdempotentEventConsumer.java`
- **Trigger:** Rapid burst of duplicate events sent simultaneously.
- **Expected:** Exactly one event processes; all duplicates are discarded.
- **Actual:** `contains(id)` check occurs before async execution; duplicate events arrive before `processedEventIds.add()` finishes, causing multiple processing executions.
- **Reproduction:** Send 10 identical events concurrently; `businessLogicHandler` is invoked multiple times.
- **Classification:** Static Detection: *Low* | Concurrency: *Very High*

---

### **B03: Premature Consumer Offset Commit Before Execution**
- **File:** `src/main/java/com/lyxor/kafka/service/IdempotentEventConsumer.java`
- **Trigger:** Event offset is committed before async execution completes.
- **Expected:** Offset is committed only after business logic successfully executes.
- **Actual:** `committedOffsets.put()` executes synchronously before async task. If task fails or JVM restarts, the message is skipped on consumer restart.
- **Reproduction:** Throw runtime error in `businessLogicHandler`; inspect `getCommittedOffset()` → updated despite failure.
- **Classification:** Static Detection: *Low* | Reliability: *Very High*

---

### **B04: Exponential Backoff Signed Bit-Shift Overflow**
- **File:** `src/main/java/com/lyxor/kafka/service/DeadLetterQueueHandler.java`
- **Trigger:** Event retries reaching 31 or higher in high-retry policies.
- **Expected:** Delay caps at maximum backoff or increments monotonically.
- **Actual:** `(1 << 31)` evaluates to `Integer.MIN_VALUE` (-2147483648), producing negative millisecond delays.
- **Reproduction:** Call `calculateBackoffDelay(31)`; returns negative long value.
- **Classification:** Static Detection: *Low* | Arithmetic: *Very High*

---

### **B05: Swallowed Async Publisher Exception Reporting False Delivery**
- **File:** `src/main/java/com/lyxor/kafka/service/KafkaEventPublisher.java`
- **Trigger:** Network broker disconnection or serialization failure during `publishAsync()`.
- **Expected:** Future completes exceptionally (`completeExceptionally`) or returns `false`.
- **Actual:** Catch block catches `Exception`, logs it, and returns `true`, tricking caller into believing message reached Kafka broker.
- **Reproduction:** Pass failing `brokerTransport` throwing `KafkaTimeoutException`; `future.get()` returns `true`.
- **Classification:** Static Detection: *Low* | Error Handling: *Very High*

---

### **B06: Leaked ThreadPool Executor on Dynamic Consumer Spawning**
- **File:** `src/main/java/com/lyxor/kafka/service/PartitionOrderingCoordinator.java`
- **Trigger:** Triggering partition rebalancing (`rebalancePartitions()`).
- **Expected:** All active partition worker thread pools are shut down gracefully.
- **Actual:** `partitionWorkers.clear()` drops map references without calling `shutdown()`, leaving threads alive and leaking OS thread handles.
- **Reproduction:** Repeatedly call `getWorkerForPartition()` followed by `rebalancePartitions()`; thread count continually increases.
- **Classification:** Static Detection: *Low* | Resource Leak: *High*

---

### **B07: Non-Atomic In-Flight Messages Backlog Counter**
- **File:** `src/main/java/com/lyxor/kafka/service/KafkaEventPublisher.java`
- **Trigger:** Thousands of concurrent messages published across multiple threads.
- **Expected:** `inFlightCount` accurately reflects pending async tasks.
- **Actual:** `inFlightCount++` / `inFlightCount--` suffers lost updates under thread contention, drifting into negative or inaccurate values.
- **Reproduction:** Publish 1,000 events in parallel; inspect `getInFlightCount()` after completion → non-zero value.
- **Classification:** Static Detection: *Medium* | Thread Safety: *Medium*

---

### **B08: Same-Partition Event Serialization Inversion**
- **File:** `src/main/java/com/lyxor/kafka/service/KafkaEventPublisher.java`
- **Trigger:** Publishing sequential updates for the same entity (same partition key).
- **Expected:** Events for key `K` execute strictly sequentially (Event 1 then Event 2).
- **Actual:** Shared multi-threaded executor picks up Event 2 on Thread B while Thread A pauses, causing Event 2 to persist before Event 1.
- **Reproduction:** Send `UPDATE_1` then `UPDATE_2` for user `USR-100` concurrently; `UPDATE_2` is processed first.
- **Classification:** Static Detection: *Low* | Event Ordering: *High*

---

### **B09: Mutable Event Headers Reference Modification**
- **File:** `src/main/java/com/lyxor/kafka/model/EventEnvelope.java`
- **Trigger:** Interceptor or downstream subscriber modifies `event.getHeaders()`.
- **Expected:** Event envelope headers are immutable to downstream consumers.
- **Actual:** Returns direct mutable `Map<String, String>`, permitting external code to alter or clear headers in-place.
- **Reproduction:** Call `event.getHeaders().put("X-Audit", "Tampered")`; inspect original event headers.
- **Classification:** Static Detection: *Low* | Data Integrity: *Medium*

---

### **B10: Poison Pill Recursive Dead-Letter Reprocessing Loop**
- **File:** `src/main/java/com/lyxor/kafka/service/DeadLetterQueueHandler.java`
- **Trigger:** Ingesting an event payload containing `"poison-pill"`.
- **Expected:** Event routes directly to DLQ after exceeding retries.
- **Actual:** `retryProcessing()` resets retry counter via `new ProcessingRecord(0)` on poison pills, causing infinite recursive retry and `StackOverflowError`.
- **Reproduction:** Submit event with payload `"poison-pill"`; triggers infinite recursion loop.
- **Classification:** Static Detection: *Low* | Crash / Recursion: *High*
