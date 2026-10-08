package com.lyxor.cache.model;

public class CacheStatistics {
    private long hits = 0;
    private long misses = 0;
    private long evictions = 0;

    public void recordHit() {
        this.hits++;
    }

    public void recordMiss() {
        this.misses++;
    }

    public void recordEviction() {
        this.evictions++;
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }

    public long getEvictions() {
        return evictions;
    }

    public double getHitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
