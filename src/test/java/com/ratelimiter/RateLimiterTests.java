package com.ratelimiter;

import com.ratelimiter.algorithms.FixedWindowCounter;
import com.ratelimiter.algorithms.SlidingWindowCounter;
import com.ratelimiter.clock.Clock;
import com.ratelimiter.core.RateLimitConfig;
import com.ratelimiter.core.RateLimitResult;
import com.ratelimiter.core.RateLimiter;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal self-contained test runner. Prints PASS/FAIL per check and
 * exits non-zero on any failure. Kept dependency-free so it can run
 * with plain javac/java.
 */
public final class RateLimiterTests {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        fixedWindow_allowsUpToLimitPerWindow();
        fixedWindow_resetsAfterWindowRolls();
        fixedWindow_isolatesKeys();
        slidingWindow_preventsBoundaryBurst();
        slidingWindow_recoversAfterFullWindow();
        fixedWindow_isThreadSafe();

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void fixedWindow_allowsUpToLimitPerWindow() {
        FakeClock clock = new FakeClock(0);
        RateLimiter rl = new FixedWindowCounter(
                new RateLimitConfig(3, Duration.ofSeconds(1)), clock);

        check("fixed #1 allow", rl.tryAcquire("k").allowed(), true);
        check("fixed #2 allow", rl.tryAcquire("k").allowed(), true);
        check("fixed #3 allow", rl.tryAcquire("k").allowed(), true);
        check("fixed #4 deny", rl.tryAcquire("k").allowed(), false);
    }

    static void fixedWindow_resetsAfterWindowRolls() {
        FakeClock clock = new FakeClock(0);
        RateLimiter rl = new FixedWindowCounter(
                new RateLimitConfig(2, Duration.ofSeconds(1)), clock);

        rl.tryAcquire("k"); rl.tryAcquire("k");
        check("fixed denied before roll", rl.tryAcquire("k").allowed(), false);
        clock.advance(1000);
        check("fixed allowed after roll", rl.tryAcquire("k").allowed(), true);
    }

    static void fixedWindow_isolatesKeys() {
        FakeClock clock = new FakeClock(0);
        RateLimiter rl = new FixedWindowCounter(
                new RateLimitConfig(1, Duration.ofSeconds(1)), clock);

        check("key A allow", rl.tryAcquire("A").allowed(), true);
        check("key B allow", rl.tryAcquire("B").allowed(), true);
        check("key A deny",  rl.tryAcquire("A").allowed(), false);
    }

    /**
     * Classic fixed-window boundary burst: 5 requests in last 1ms of
     * window N and 5 more at start of window N+1 — fixed window would
     * permit all 10 in 2ms. Sliding window should reject some.
     */
    static void slidingWindow_preventsBoundaryBurst() {
        FakeClock clock = new FakeClock(0);
        RateLimiter rl = new SlidingWindowCounter(
                new RateLimitConfig(5, Duration.ofSeconds(1)), clock);

        clock.set(999);
        int allowedInFirst = 0;
        for (int i = 0; i < 5; i++) {
            if (rl.tryAcquire("k").allowed()) allowedInFirst++;
        }
        check("sliding allows 5 in first window", allowedInFirst, 5);

        clock.set(1000);
        int allowedInSecond = 0;
        for (int i = 0; i < 5; i++) {
            if (rl.tryAcquire("k").allowed()) allowedInSecond++;
        }
        // prevCount=5, elapsed~=0 -> weight~=1 -> estimated ~5 already, so all 5 denied
        check("sliding blocks boundary burst", allowedInSecond, 0);
    }

    static void slidingWindow_recoversAfterFullWindow() {
        FakeClock clock = new FakeClock(0);
        RateLimiter rl = new SlidingWindowCounter(
                new RateLimitConfig(2, Duration.ofSeconds(1)), clock);

        rl.tryAcquire("k"); rl.tryAcquire("k");
        check("sliding denies over limit", rl.tryAcquire("k").allowed(), false);
        clock.advance(2000); // skip an entire empty window
        check("sliding recovers", rl.tryAcquire("k").allowed(), true);
    }

    static void fixedWindow_isThreadSafe() throws Exception {
        FakeClock clock = new FakeClock(0);
        RateLimiter rl = new FixedWindowCounter(
                new RateLimitConfig(1000, Duration.ofSeconds(1)), clock);

        int threads = 16, perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException ignored) {}
                for (int i = 0; i < perThread; i++) {
                    if (rl.tryAcquire("shared").allowed()) allowed.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        check("fixed thread-safe exact limit", allowed.get(), 1000);
    }

    // --- helpers ---

    static void check(String label, Object actual, Object expected) {
        if (java.util.Objects.equals(actual, expected)) {
            System.out.println("PASS  " + label);
            passed++;
        } else {
            System.out.println("FAIL  " + label + "  expected=" + expected + " actual=" + actual);
            failed++;
        }
    }

    static final class FakeClock implements Clock {
        private final AtomicLong now;
        FakeClock(long start) { this.now = new AtomicLong(start); }
        void advance(long ms) { now.addAndGet(ms); }
        void set(long ms) { now.set(ms); }
        @Override public long nowMillis() { return now.get(); }
    }

    // silence unused warning
    @SuppressWarnings("unused") private static void touch(RateLimitResult r) {}
}
