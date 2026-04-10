package com.ratelimiter.core;

/**
 * Strategy interface every rate-limiting algorithm implements.
 * A single instance is bound to one RateLimitConfig (e.g. 5 req/min)
 * and shards state internally by key.
 */
public interface RateLimiter {

    RateLimitResult tryAcquire(String key);

    RateLimitConfig config();
}
