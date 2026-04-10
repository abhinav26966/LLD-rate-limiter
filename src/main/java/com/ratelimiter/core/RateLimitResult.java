package com.ratelimiter.core;

public final class RateLimitResult {

    private final boolean allowed;
    private final long remaining;
    private final long retryAfterMillis;

    private RateLimitResult(boolean allowed, long remaining, long retryAfterMillis) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.retryAfterMillis = retryAfterMillis;
    }

    public static RateLimitResult allow(long remaining) {
        return new RateLimitResult(true, remaining, 0L);
    }

    public static RateLimitResult deny(long retryAfterMillis) {
        return new RateLimitResult(false, 0L, retryAfterMillis);
    }

    public boolean allowed() { return allowed; }
    public long remaining() { return remaining; }
    public long retryAfterMillis() { return retryAfterMillis; }

    @Override
    public String toString() {
        return allowed
                ? "ALLOW(remaining=" + remaining + ")"
                : "DENY(retryAfterMs=" + retryAfterMillis + ")";
    }
}
