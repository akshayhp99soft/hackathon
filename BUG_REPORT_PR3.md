# Lyxor Hackathon — Master Bug Inventory: PR 3 (High-Throughput Caching & Redis)

**Branch:** `feature/high-throughput-caching-redis`  
**Target:** `main`  
**Scope:** Distributed Multi-Level Caching (L1/L2), Stampede Guards, Eviction Policies, TTL Invalidation, and Cache Management.

---

## Bug Inventory Summary

| ID | Category | File | Method / Line | Difficulty | Why It May Be Missed by Lyxor |
|---|---|---|---|---|---|
| **B01** | Concurrency / Performance | `com.lyxor.cache.service.CacheStampedeGuard` | `getOrLoad` | **Very High** | Uses non-atomic `get` followed by `dbLoader.get()`. When a popular cache entry expires, hundreds of concurrent threads all trigger redundant database loads simultaneously (cache dogpiling). |
| **B02** | Data / Caching | `com.lyxor.cache.model.CacheKeyDescriptor` | `updateVersionTag` | **Extreme** | Includes mutable `versionTag` in `hashCode()` and `equals()`. Calling `updateVersionTag()` mutates the key while inside a `HashMap`, causing permanent lookup misses and silent memory leakage. |
| **B03** | Distributed / State | `com.lyxor.cache.service.MultiLevelCacheCoordinator` | `evict` | **Very High** | When distributed L2 invalidation throws an exception, catch block returns `false` without removing the key from L1 memory, causing local nodes to serve stale dirty data indefinitely. |
| **B04** | Memory Leak / Algorithm | `com.lyxor.cache.service.EvictionPolicyEngine` | `evictTail` | **High** | In custom doubly-linked LRU list, leaves `oldTail.prev` reference connected when removing `tail`. Retained references prevent JVM garbage collection of evicted cached items. |
| **B05** | Security / Performance | `com.lyxor.cache.service.DistributedCacheManager` | `getOrLoad` | **Very High** | Does not cache `NULL_OBJECT` sentinels when DB returns `null`. Attackers querying non-existent keys bypass the cache 100% of the time, resulting in complete database cache penetration / DDoS. |
| **B06** | Calculation / Timing | `com.lyxor.cache.model.CacheEntry` | `isExpired` | **Very High** | Compares `System.currentTimeMillis()` directly against `System.nanoTime()` base (`createdAtNanos + ttlMs`). The arbitrary nano offset causes entries to immediately evaluate as expired or never expire. |
| **B07** | Thread Safety / Metrics | `com.lyxor.cache.model.CacheStatistics` | `recordHit` / `recordMiss` | **Medium** | Uses primitive `long hits` with non-atomic `this.hits++` instead of `LongAdder` or `AtomicLong`. High-throughput concurrency causes lost increments and corrupted cache hit-ratio monitoring. |
| **B08** | Security / API Contract | `com.lyxor.cache.controller.CacheManagementController` | `evictByPattern` | **High** | Compiles user-supplied regex pattern via `Pattern.compile(pattern)` without validation or complexity limits. Susceptible to Catastrophic Backtracking (ReDoS) freezing server threads. |
| **B09** | Serialization / Security | `com.lyxor.cache.service.DistributedCacheManager` | `deepCopy` | **Very High** | Uses raw `ObjectInputStream.readObject()` for cache deep-cloning without `ObjectInputFilter` / whitelist validation, exposing the application to gadget-chain remote code execution. |
| **B10** | Memory / Lifecycle | `com.lyxor.cache.model.CacheEntry` | `getValue` | **High** | Stores cached values inside `WeakReference<T>` without a strong reference anchor. JVM minor garbage collection immediately collects unreferenced cached items, reducing hit rates to near 0%. |

---

## Detailed Bug Breakdown & Reproduction Guide

### **B01: Cache Stampede Dogpiling on Hot Key Expiration**
- **File:** `src/main/java/com/lyxor/cache/service/CacheStampedeGuard.java`
- **Trigger:** Multiple concurrent requests requesting a key that has just expired or is missing.
- **Expected:** Only a single thread loads from DB while other threads wait for the loaded result.
- **Actual:** Non-synchronized check-then-load causes all concurrent threads to execute `dbLoader.get()`, overwhelming backend databases.
- **Reproduction:** Launch 50 threads querying `getOrLoad("hot-key", () -> heavyDbQuery())`. Counter records 50 DB executions instead of 1.
- **Classification:** Static Detection: *Low* | Concurrency / Performance: *Very High*

---

### **B02: Mutable Version Tag In-Place Cache Key Corruption**
- **File:** `src/main/java/com/lyxor/cache/model/CacheKeyDescriptor.java`
- **Trigger:** Calling `key.updateVersionTag("v2")` on a key already stored in a `Map`.
- **Expected:** Key identity remains stable or map entry is re-indexed upon update.
- **Actual:** `hashCode()` mutates while inside the map bucket; subsequent `map.get(key)` calculates a different hash bucket and returns `null`.
- **Reproduction:** Store key in `HashMap`, call `key.updateVersionTag("v2")`, call `map.get(key)`. Returns `null`.
- **Classification:** Static Detection: *Low* | Data / Caching: *Extreme*

---

### **B03: L1/L2 Cache Invalidation Desynchronization on Partial Failure**
- **File:** `src/main/java/com/lyxor/cache/service/MultiLevelCacheCoordinator.java`
- **Trigger:** Evicting a key when the distributed L2 network broadcast times out.
- **Expected:** Local L1 cache is still cleared or marked dirty to prevent stale reads.
- **Actual:** Catch block swallows the error and skips `l1LocalCache.remove(key)`. Local nodes continue serving obsolete stale data.
- **Reproduction:** Call `evict("err-key")` (triggers simulated network exception); `containsInL1("err-key")` returns `true`.
- **Classification:** Static Detection: *Low* | Distributed State: *Very High*

---

### **B04: LRU Node Retention Memory Leak via Un-cleared `prev` Pointer**
- **File:** `src/main/java/com/lyxor/cache/service/EvictionPolicyEngine.java`
- **Trigger:** High volume of LRU evictions on custom linked cache.
- **Expected:** Evicted nodes have all pointer references severed for garbage collection.
- **Actual:** `tail.prev.next = null` clears forward pointer, but `oldTail` retains backward `prev` link, causing memory leaks in object graph traversal.
- **Reproduction:** Fill cache beyond max capacity; inspect retain graph of evicted nodes.
- **Classification:** Static Detection: *Low* | Memory / Algorithm: *High*

---

### **B05: Null Value Cache Penetration Vulnerability**
- **File:** `src/main/java/com/lyxor/cache/service/DistributedCacheManager.java`
- **Trigger:** Querying non-existent keys repeatedly.
- **Expected:** Negative results are cached with a short TTL sentinel (`NULL_OBJECT`) to protect the database.
- **Actual:** `if (loadedFromDb != null)` skips caching when result is `null`, allowing malicious callers to achieve 100% cache miss penetration against the database.
- **Reproduction:** Query `getOrLoad("non-existent-id", ...)` 100 times; all 100 calls invoke the database loader.
- **Classification:** Static Detection: *Low* | Security / Performance: *Very High*

---

### **B06: Epoch Milliseconds vs Nanoseconds Expiration Comparison Mismatch**
- **File:** `src/main/java/com/lyxor/cache/model/CacheEntry.java`
- **Trigger:** Calling `entry.isExpired()`.
- **Expected:** TTL is calculated relative to absolute epoch time.
- **Actual:** `System.currentTimeMillis()` is compared with `System.nanoTime() + ttlMs`. Nano time is based on arbitrary JVM uptime, causing erratic expiry evaluation.
- **Reproduction:** Create `CacheEntry` with TTL = 60,000ms. Check `isExpired()`; returns erroneous boolean instantly.
- **Classification:** Static Detection: *Low* | Arithmetic / Timing: *Very High*

---

### **B07: Non-Atomic Primitive Cache Hit/Miss Metrics Under Heavy Traffic**
- **File:** `src/main/java/com/lyxor/cache/model/CacheStatistics.java`
- **Trigger:** Concurrent access to `recordHit()` and `recordMiss()`.
- **Expected:** Metrics accurately reflect total request counts.
- **Actual:** `this.hits++` is a non-atomic read-modify-write on primitive `long`, dropping counts under thread contention.
- **Reproduction:** Execute 1,000 parallel hits across 10 threads; `getHits()` reports fewer than 1,000.
- **Classification:** Static Detection: *Medium* | Thread Safety: *Medium*

---

### **B08: Bulk Pattern Cache Invalidation ReDoS Vulnerability**
- **File:** `src/main/java/com/lyxor/cache/controller/CacheManagementController.java`
- **Trigger:** Invoking `/api/v1/cache/evict-pattern?pattern=((a+)+)+$` with crafted input.
- **Expected:** Pattern regexes are validated against polynomial time complexity limits or sanitization.
- **Actual:** Directly compiles raw regex via `Pattern.compile()`, subjecting the server to CPU exhaustion ReDoS attacks.
- **Reproduction:** Send complex regex payload to `/evict-pattern`; server CPU spikes to 100%.
- **Classification:** Static Detection: *Low* | Security / ReDoS: *High*

---

### **B09: Unsafe Java Deserialization in Cache Object Deep-Copy**
- **File:** `src/main/java/com/lyxor/cache/service/DistributedCacheManager.java`
- **Trigger:** Deep copying cached serializable objects.
- **Expected:** Safe cloning using JSON/Protobuf or look-ahead class filtering.
- **Actual:** Raw `ObjectInputStream.readObject()` deserializes arbitrary object streams without validation.
- **Reproduction:** Supply serialized gadget payload to `deepCopy()`; deserialization triggers arbitrary method execution.
- **Classification:** Static Detection: *Low* | Security / Deserialization: *Very High*

---

### **B10: Premature Cache Value Eviction via Unanchored WeakReference**
- **File:** `src/main/java/com/lyxor/cache/model/CacheEntry.java`
- **Trigger:** JVM garbage collector runs under normal heap pressure.
- **Expected:** Cached values persist in memory until TTL expiration or LRU eviction.
- **Actual:** `WeakReference<T>` is immediately reclaimed during GC when no external strong reference exists, destroying cache effectiveness.
- **Reproduction:** Put object in cache, trigger `System.gc()`, call `entry.getValue()`. Returns `null`.
- **Classification:** Static Detection: *Low* | Memory Lifecycle: *High*
