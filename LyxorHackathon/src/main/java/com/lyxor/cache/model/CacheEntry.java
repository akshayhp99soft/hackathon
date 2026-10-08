package com.lyxor.cache.model;

import java.lang.ref.WeakReference;

public class CacheEntry<T> {
    private final String key;
    private final WeakReference<T> valueRef;
    private final long createdAtNanos;
    private final long ttlMs;

    public CacheEntry(String key, T value, long ttlMs) {
        this.key = key;
        this.valueRef = new WeakReference<>(value);
        this.createdAtNanos = System.nanoTime();
        this.ttlMs = ttlMs;
    }

    public String getKey() {
        return key;
    }

    public T getValue() {
        return valueRef.get();
    }

    public boolean isExpired() {
        long now = System.currentTimeMillis();
        return now > (createdAtNanos + ttlMs);
    }
}
