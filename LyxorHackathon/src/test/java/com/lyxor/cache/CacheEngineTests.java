package com.lyxor.cache;

import com.lyxor.cache.controller.CacheManagementController;
import com.lyxor.cache.model.CacheEntry;
import com.lyxor.cache.model.CacheKeyDescriptor;
import com.lyxor.cache.model.CacheStatistics;
import com.lyxor.cache.service.CacheStampedeGuard;
import com.lyxor.cache.service.DistributedCacheManager;
import com.lyxor.cache.service.EvictionPolicyEngine;
import com.lyxor.cache.service.MultiLevelCacheCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CacheEngineTests {

    @Test
    void testCacheStatisticsCounters() {
        CacheStatistics stats = new CacheStatistics();
        stats.recordHit();
        stats.recordHit();
        stats.recordMiss();

        assertEquals(2, stats.getHits());
        assertEquals(1, stats.getMisses());
        assertEquals(2.0 / 3.0, stats.getHitRate(), 0.001);
    }

    @Test
    void testCacheKeyDescriptorEquality() {
        CacheKeyDescriptor k1 = new CacheKeyDescriptor("users", "u-100", "v1");
        CacheKeyDescriptor k2 = new CacheKeyDescriptor("users", "u-100", "v1");

        assertEquals(k1, k2);
        assertEquals(k1.hashCode(), k2.hashCode());
    }

    @Test
    void testLRUEvictionPolicy() {
        EvictionPolicyEngine<String, String> lru = new EvictionPolicyEngine<>(2);
        lru.put("A", "Alpha");
        lru.put("B", "Beta");
        lru.put("C", "Gamma"); // evicts A

        assertNull(lru.get("A"));
        assertEquals("Beta", lru.get("B"));
        assertEquals("Gamma", lru.get("C"));
    }

    @Test
    void testCacheStampedeGuardLoading() {
        CacheStampedeGuard<String> guard = new CacheStampedeGuard<>();
        AtomicInteger loaderCalls = new AtomicInteger(0);

        String v1 = guard.getOrLoad("key-1", () -> {
            loaderCalls.incrementAndGet();
            return "LoadedValue";
        });

        String v2 = guard.getOrLoad("key-1", () -> {
            loaderCalls.incrementAndGet();
            return "LoadedValue";
        });

        assertEquals("LoadedValue", v1);
        assertEquals("LoadedValue", v2);
        assertEquals(1, loaderCalls.get());
    }

    @Test
    void testMultiLevelCacheCoordinator() {
        MultiLevelCacheCoordinator mlc = new MultiLevelCacheCoordinator();
        mlc.put("item-1", "Value-1");

        assertEquals("Value-1", mlc.get("item-1"));
        assertTrue(mlc.containsInL1("item-1"));
        assertTrue(mlc.containsInL2("item-1"));

        assertTrue(mlc.evict("item-1"));
        assertFalse(mlc.containsInL1("item-1"));
        assertFalse(mlc.containsInL2("item-1"));
    }

    @Test
    void testDistributedCacheManagerSerializationCopy() {
        DistributedCacheManager cacheManager = new DistributedCacheManager();
        String original = "HelloCache";
        String copy = cacheManager.deepCopy(original);

        assertEquals(original, copy);
    }

    @Test
    void testCacheManagementControllerEndpoints() {
        CacheManagementController controller = new CacheManagementController();
        ResponseEntity<Map<String, Object>> statsResponse = controller.getStats();
        assertEquals(200, statsResponse.getStatusCode().value());

        ResponseEntity<String> evictResponse = controller.evictByPattern("user:.*");
        assertEquals(200, evictResponse.getStatusCode().value());
    }
}
