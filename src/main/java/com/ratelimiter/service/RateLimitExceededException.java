package com.ratelimiter.service;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterMillis;

    public RateLimitExceededException(long retryAfterMillis) {
        super("Rate limit exceeded, retry after " + retryAfterMillis + " ms");
        this.retryAfterMillis = retryAfterMillis;
    }

    public long retryAfterMillis() { return retryAfterMillis; }
}
