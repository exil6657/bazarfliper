package com.bazaarflipper.api;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles Hypixel API rate limiting: 120 requests per 60 seconds, 429 handling.
 */
public class APIRateLimiter {
    private final int maxRequestsPerMinute = 120;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private long windowStart = System.currentTimeMillis();
    private volatile long backoffUntil = 0;

    public synchronized boolean canMakeRequest() {
        long now = System.currentTimeMillis();
        if (now < backoffUntil) return false;

        if (now - windowStart > 60_000) {
            windowStart = now;
            requestCount.set(0);
        }

        return requestCount.get() < maxRequestsPerMinute;
    }

    public synchronized void recordRequest() {
        if (System.currentTimeMillis() - windowStart > 60_000) {
            windowStart = System.currentTimeMillis();
            requestCount.set(0);
        }
        requestCount.incrementAndGet();
    }

    public synchronized void handleRateLimit() {
        // Exponential backoff
        long now = System.currentTimeMillis();
        long backoff = 5000; // start 5s
        if (backoffUntil > now) {
            // Increase
            backoff = (backoffUntil - now) * 2;
            backoff = Math.min(backoff, 60_000);
        }
        backoffUntil = now + backoff;
    }

    public synchronized void handle429() {
        handleRateLimit();
    }
}
