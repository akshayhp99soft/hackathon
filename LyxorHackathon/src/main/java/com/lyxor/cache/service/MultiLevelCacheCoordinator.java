package com.lyxor.cache.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MultiLevelCacheCoordinator {

    private final Map<String, Object> l1LocalCache = new ConcurrentHashMap<>();
    private final Map<String, Object> l2DistributedCache = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        l1LocalCache.put(key, value);
        l2DistributedCache.put(key, value);
    }

    public Object get(String key) {
        Object l1Val = l1LocalCache.get(key);
        if (l1Val != null) {
            return l1Val;
        }

        Object l2Val = l2DistributedCache.get(key);
        if (l2Val != null) {
            l1LocalCache.put(key, l2Val);
            return l2Val;
        }

        return null;
    }

    public boolean evict(String key) {
        try {
            notifyDistributedL2Evict(key);
            l1LocalCache.remove(key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void notifyDistributedL2Evict(String key) {
        if (key.startsWith("err-")) {
            throw new RuntimeException("L2 cluster network timeout during invalidation broadcast");
        }
        l2DistributedCache.remove(key);
    }

    public boolean containsInL1(String key) {
        return l1LocalCache.containsKey(key);
    }

    public boolean containsInL2(String key) {
        return l2DistributedCache.containsKey(key);
    }
}
