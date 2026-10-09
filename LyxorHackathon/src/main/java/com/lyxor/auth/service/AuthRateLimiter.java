package com.lyxor.auth.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimiter {

    private final int maxBucketCapacity;
    private final int refillRatePerSec;

    private static class TokenBucket {
        int tokens;
        long lastRefillMs;

        TokenBucket(int initialTokens, long now) {
            this.tokens = initialTokens;
            this.lastRefillMs = now;
        }
    }

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimiter() {
        this(10, 2);
    }

    public AuthRateLimiter(int maxBucketCapacity, int refillRatePerSec) {
        this.maxBucketCapacity = maxBucketCapacity;
        this.refillRatePerSec = refillRatePerSec;
    }

    public boolean tryAcquire(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(maxBucketCapacity, now));

        synchronized (bucket) {
            long elapsedMs = now - bucket.lastRefillMs;
            int refillTokens = (int) (elapsedMs * refillRatePerSec / 1000);

            if (refillTokens > 0) {
                bucket.tokens = Math.min(maxBucketCapacity, bucket.tokens + refillTokens);
                bucket.lastRefillMs = now;
            }

            if (bucket.tokens > 0) {
                bucket.tokens--;
                return true;
            }

            return false;
        }
    }

    public int getAvailableTokens(String clientIp) {
        TokenBucket bucket = buckets.get(clientIp);
        if (bucket == null) {
            return maxBucketCapacity;
        }
        synchronized (bucket) {
            return bucket.tokens;
        }
    }
}
