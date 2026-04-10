package com.ratelimiter.algorithms;

import com.ratelimiter.clock.Clock;
import com.ratelimiter.core.RateLimitConfig;
import com.ratelimiter.core.RateLimitResult;
import com.ratelimiter.core.RateLimiter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Weighted sliding window counter.
 *
 * Instead of storing every timestamp (expensive) we keep two buckets per
 * key — the current fixed window and the previous one — and estimate the
 * true sliding count as:
 *
 *   estimated = prevCount * ((windowSize - elapsedInCurrent) / windowSize)
 *             + currCount
 *
 * This smooths out the classic burst-at-the-boundary problem of the
 * fixed-window counter while staying O(1) in time and space per key.
 */
public final class SlidingWindowCounter implements RateLimiter {

    private final RateLimitConfig config;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public SlidingWindowCounter(RateLimitConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        long now = clock.nowMillis();
        long windowMs = config.windowMillis();
        long currentWindow = now / windowMs;

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());

        synchronized (bucket) {
            if (currentWindow == bucket.currentWindow) {
                // same window, nothing to roll
            } else if (currentWindow == bucket.currentWindow + 1) {
                bucket.prevCount = bucket.currentCount;
                bucket.currentCount = 0;
                bucket.currentWindow = currentWindow;
            } else {
                bucket.prevCount = 0;
                bucket.currentCount = 0;
                bucket.currentWindow = currentWindow;
            }

            long elapsedInCurrent = now - (currentWindow * windowMs);
            double prevWeight = (double) (windowMs - elapsedInCurrent) / windowMs;
            double estimated = bucket.prevCount * prevWeight + bucket.currentCount;

            if (estimated + 1 <= config.limit()) {
                bucket.currentCount++;
                long remaining = Math.max(0, config.limit() - (long) Math.ceil(estimated + 1));
                return RateLimitResult.allow(remaining);
            }
            long retryAfter = windowMs - elapsedInCurrent;
            return RateLimitResult.deny(retryAfter);
        }
    }

    @Override
    public RateLimitConfig config() { return config; }

    private static final class Bucket {
        long currentWindow = -1;
        long currentCount;
        long prevCount;
    }
}
