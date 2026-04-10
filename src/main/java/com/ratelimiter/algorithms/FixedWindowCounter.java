package com.ratelimiter.algorithms;

import com.ratelimiter.clock.Clock;
import com.ratelimiter.core.RateLimitConfig;
import com.ratelimiter.core.RateLimitResult;
import com.ratelimiter.core.RateLimiter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed window counter.
 *
 *   window index  = floor(now / windowSize)
 *   per key we keep { currentWindow, count }
 *   on every request, if windows match -> increment, else reset
 *
 * Thread-safety: one short synchronized block per key's bucket; the outer
 * map is a ConcurrentHashMap, so different keys never contend.
 * O(1) time, O(K) space where K = number of active keys.
 */
public final class FixedWindowCounter implements RateLimiter {

    private final RateLimitConfig config;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public FixedWindowCounter(RateLimitConfig config, Clock clock) {
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
            if (bucket.window != currentWindow) {
                bucket.window = currentWindow;
                bucket.count = 0;
            }
            if (bucket.count < config.limit()) {
                bucket.count++;
                return RateLimitResult.allow(config.limit() - bucket.count);
            }
            long retryAfter = ((currentWindow + 1) * windowMs) - now;
            return RateLimitResult.deny(retryAfter);
        }
    }

    @Override
    public RateLimitConfig config() { return config; }

    private static final class Bucket {
        long window = -1;
        long count;
    }
}
