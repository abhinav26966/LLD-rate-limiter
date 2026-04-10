package com.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;

public final class RateLimitConfig {

    private final long limit;
    private final Duration window;

    public RateLimitConfig(long limit, Duration window) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
        if (window == null || window.isZero() || window.isNegative())
            throw new IllegalArgumentException("window must be positive");
        this.limit = limit;
        this.window = window;
    }

    public long limit() { return limit; }
    public Duration window() { return window; }
    public long windowMillis() { return window.toMillis(); }

    public static RateLimitConfig perMinute(long limit) {
        return new RateLimitConfig(limit, Duration.ofMinutes(1));
    }

    public static RateLimitConfig perHour(long limit) {
        return new RateLimitConfig(limit, Duration.ofHours(1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RateLimitConfig that)) return false;
        return limit == that.limit && window.equals(that.window);
    }

    @Override
    public int hashCode() { return Objects.hash(limit, window); }

    @Override
    public String toString() { return limit + " req / " + window; }
}
