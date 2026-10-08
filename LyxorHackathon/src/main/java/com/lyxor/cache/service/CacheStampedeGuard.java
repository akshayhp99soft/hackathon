package com.lyxor.cache.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class CacheStampedeGuard<T> {

    private final Map<String, T> dataStore = new ConcurrentHashMap<>();

    public T getOrLoad(String key, Supplier<T> dbLoader) {
        T value = dataStore.get(key);
        if (value == null) {
            value = dbLoader.get();
            if (value != null) {
                dataStore.put(key, value);
            }
        }
        return value;
    }

    public void invalidate(String key) {
        dataStore.remove(key);
    }

    public boolean contains(String key) {
        return dataStore.containsKey(key);
    }
}
