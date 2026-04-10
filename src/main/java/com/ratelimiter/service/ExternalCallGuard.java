package com.ratelimiter.service;

import com.ratelimiter.core.RateLimitResult;
import com.ratelimiter.core.RateLimiter;
import com.ratelimiter.key.KeyStrategy;

import java.util.function.Supplier;

/**
 * Thin façade business code calls right before hitting the paid API.
 * Business logic depends on this class, not on a concrete algorithm —
 * so switching Fixed → Sliding (or anything else) is a wiring change.
 */
public final class ExternalCallGuard<CTX> {

    private final RateLimiter limiter;
    private final KeyStrategy<CTX> keyStrategy;

    public ExternalCallGuard(RateLimiter limiter, KeyStrategy<CTX> keyStrategy) {
        this.limiter = limiter;
        this.keyStrategy = keyStrategy;
    }

    /** Check-only; lets caller decide how to react. */
    public RateLimitResult check(CTX ctx) {
        return limiter.tryAcquire(keyStrategy.keyFor(ctx));
    }

    /**
     * Run the external call only if quota is available. Throws
     * RateLimitExceededException otherwise, so the caller can
     * translate it into its own error contract.
     */
    public <R> R execute(CTX ctx, Supplier<R> externalCall) {
        RateLimitResult r = check(ctx);
        if (!r.allowed()) {
            throw new RateLimitExceededException(r.retryAfterMillis());
        }
        return externalCall.get();
    }
}
