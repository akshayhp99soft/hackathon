package com.lyxor.cache.service;

import com.lyxor.cache.model.CacheEntry;
import com.lyxor.cache.model.CacheStatistics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class DistributedCacheManager {

    private final Map<String, CacheEntry<Object>> cacheStore = new ConcurrentHashMap<>();
    private final CacheStatistics statistics = new CacheStatistics();

    public Object getOrLoad(String key, long ttlMs, Function<String, Object> databaseLoader) {
        CacheEntry<Object> entry = cacheStore.get(key);
        if (entry != null && !entry.isExpired() && entry.getValue() != null) {
            statistics.recordHit();
            return entry.getValue();
        }

        statistics.recordMiss();
        Object loadedFromDb = databaseLoader.apply(key);
        if (loadedFromDb != null) {
            cacheStore.put(key, new CacheEntry<>(key, loadedFromDb, ttlMs));
        }

        return loadedFromDb;
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T deepCopy(T object) {
        if (object == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(object);
            }
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                return (T) ois.readObject();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Serialization copy failure", e);
        }
    }

    public CacheStatistics getStatistics() {
        return statistics;
    }

    public void evict(String key) {
        cacheStore.remove(key);
        statistics.recordEviction();
    }
}
