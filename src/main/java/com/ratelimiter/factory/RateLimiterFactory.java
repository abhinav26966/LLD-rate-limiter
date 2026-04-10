package com.ratelimiter.factory;

import com.ratelimiter.algorithms.FixedWindowCounter;
import com.ratelimiter.algorithms.SlidingWindowCounter;
import com.ratelimiter.clock.Clock;
import com.ratelimiter.core.RateLimitConfig;
import com.ratelimiter.core.RateLimiter;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Factory that builds a RateLimiter for a given algorithm + config.
 *
 * Adding a new algorithm = one register() call. Callers never need
 * to know concrete classes — this is how we honour OCP: open for
 * extension, closed for modification.
 */
public final class RateLimiterFactory {

    private final Map<AlgorithmType, BiFunction<RateLimitConfig, Clock, RateLimiter>> registry =
            new EnumMap<>(AlgorithmType.class);
    private final Clock clock;

    public RateLimiterFactory(Clock clock) {
        this.clock = clock;
        register(AlgorithmType.FIXED_WINDOW, FixedWindowCounter::new);
        register(AlgorithmType.SLIDING_WINDOW, SlidingWindowCounter::new);
    }

    public void register(AlgorithmType type,
                         BiFunction<RateLimitConfig, Clock, RateLimiter> builder) {
        registry.put(type, builder);
    }

    public RateLimiter create(AlgorithmType type, RateLimitConfig config) {
        BiFunction<RateLimitConfig, Clock, RateLimiter> builder = registry.get(type);
        if (builder == null) {
            throw new IllegalArgumentException("No rate limiter registered for " + type);
        }
        return builder.apply(config, clock);
    }
}
