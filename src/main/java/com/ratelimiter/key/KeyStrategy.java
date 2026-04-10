package com.ratelimiter.key;

/**
 * Builds the rate-limit key from whatever request context is relevant.
 * Swapping customer / tenant / apiKey / provider based limiting is a
 * matter of plugging in a different KeyStrategy, not rewriting the limiter.
 */
@FunctionalInterface
public interface KeyStrategy<CTX> {
    String keyFor(CTX context);
}
