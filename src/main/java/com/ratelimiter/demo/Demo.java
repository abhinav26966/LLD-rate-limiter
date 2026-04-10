package com.ratelimiter.demo;

import com.ratelimiter.clock.Clock;
import com.ratelimiter.core.RateLimitConfig;
import com.ratelimiter.core.RateLimiter;
import com.ratelimiter.factory.AlgorithmType;
import com.ratelimiter.factory.RateLimiterFactory;
import com.ratelimiter.key.KeyStrategy;
import com.ratelimiter.service.ExternalCallGuard;
import com.ratelimiter.service.RateLimitExceededException;

/**
 * Shows:
 *   1. A fake business-logic path that only hits the rate limiter when
 *      an external call is actually required.
 *   2. Swapping algorithms (Fixed <-> Sliding) without touching the
 *      business code — we only rewire the factory output.
 */
public final class Demo {

    record RequestCtx(String tenantId, boolean needsExternalCall) {}

    static final class PaidApiClient {
        String fetch(String tenantId) {
            return "external-response-for-" + tenantId;
        }
    }

    static final class BusinessService {
        private final ExternalCallGuard<RequestCtx> guard;
        private final PaidApiClient client;

        BusinessService(ExternalCallGuard<RequestCtx> guard, PaidApiClient client) {
            this.guard = guard;
            this.client = client;
        }

        String handle(RequestCtx ctx) {
            // Business logic decides whether we even need the paid API.
            if (!ctx.needsExternalCall()) {
                return "served-from-cache";
            }
            try {
                return guard.execute(ctx, () -> client.fetch(ctx.tenantId()));
            } catch (RateLimitExceededException e) {
                return "rejected(retryAfterMs=" + e.retryAfterMillis() + ")";
            }
        }
    }

    public static void main(String[] args) {
        RateLimiterFactory factory = new RateLimiterFactory(Clock.SYSTEM);
        RateLimitConfig config = RateLimitConfig.perMinute(5);
        KeyStrategy<RequestCtx> perTenant = ctx -> "tenant:" + ctx.tenantId();

        run("FIXED_WINDOW",
                factory.create(AlgorithmType.FIXED_WINDOW, config),
                perTenant);

        run("SLIDING_WINDOW",
                factory.create(AlgorithmType.SLIDING_WINDOW, config),
                perTenant);
    }

    private static void run(String label,
                            RateLimiter limiter,
                            KeyStrategy<RequestCtx> keyStrategy) {
        System.out.println("\n=== " + label + " (5/min, tenant=T1) ===");
        ExternalCallGuard<RequestCtx> guard = new ExternalCallGuard<>(limiter, keyStrategy);
        BusinessService svc = new BusinessService(guard, new PaidApiClient());

        // 3 requests that skip the external call -> never touch the limiter
        for (int i = 0; i < 3; i++) {
            System.out.println("cached  -> " + svc.handle(new RequestCtx("T1", false)));
        }
        // 7 requests that hit the paid API -> first 5 allowed, rest denied
        for (int i = 0; i < 7; i++) {
            System.out.println("external-> " + svc.handle(new RequestCtx("T1", true)));
        }
    }
}
