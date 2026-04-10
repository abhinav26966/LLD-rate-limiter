# Pluggable Rate Limiter

A small, dependency-free Java library for rate-limiting calls to a **paid external resource**. The limiter sits *inside* the backend, guarding the point where the system is about to hit the downstream API — not the incoming client API — so requests that don't need the external call never consume quota.

Two algorithms ship out of the box:

- **Fixed Window Counter**
- **Sliding Window Counter** (weighted)

The design is built around a strategy interface and a factory registry, so adding Token Bucket, Leaky Bucket, or Sliding Log later is a one-line change.

---

## Project layout

```
src/main/java/com/ratelimiter/
├── core/
│   ├── RateLimiter.java            strategy interface: tryAcquire(key) -> RateLimitResult
│   ├── RateLimitConfig.java        value object (limit + Duration window)
│   └── RateLimitResult.java        allow(remaining) | deny(retryAfterMs)
├── clock/Clock.java                time abstraction for deterministic tests
├── algorithms/
│   ├── FixedWindowCounter.java     O(1) per-key fixed window
│   └── SlidingWindowCounter.java   O(1) per-key weighted sliding window
├── factory/
│   ├── AlgorithmType.java          enum (incl. reserved TOKEN_BUCKET / LEAKY / LOG)
│   └── RateLimiterFactory.java     type -> builder registry
├── key/KeyStrategy.java            CTX -> String (per tenant / api key / provider / …)
├── service/
│   ├── ExternalCallGuard.java      façade business code calls before the paid API
│   └── RateLimitExceededException.java
└── demo/Demo.java                  end-to-end runnable example
src/test/java/com/ratelimiter/
└── RateLimiterTests.java           zero-dependency test runner
```

---

## How it fits into a request flow

```
Client -> API -> BusinessService
                    |
                    |-- no external call needed --> return cached / computed result
                    |
                    `-- external call needed -----> ExternalCallGuard.execute(ctx, call)
                                                         |
                                                         |-- allowed --> paid API
                                                         `-- denied  --> RateLimitExceededException
```

Business code only talks to `ExternalCallGuard`. The concrete algorithm is chosen at wiring time by the factory, so switching Fixed → Sliding (or any future algorithm) does **not** touch business logic.

---

## Minimal usage

```java
RateLimiterFactory factory = new RateLimiterFactory(Clock.SYSTEM);

RateLimiter limiter = factory.create(
        AlgorithmType.SLIDING_WINDOW,
        RateLimitConfig.perMinute(5));          // 5 req / min

KeyStrategy<RequestCtx> perTenant = ctx -> "tenant:" + ctx.tenantId();

ExternalCallGuard<RequestCtx> guard =
        new ExternalCallGuard<>(limiter, perTenant);

// Somewhere in business logic, only when the paid call is actually needed:
String result = guard.execute(ctx, () -> paidApiClient.fetch(ctx.tenantId()));
```

Swap the algorithm by changing one argument:

```java
factory.create(AlgorithmType.FIXED_WINDOW, RateLimitConfig.perMinute(5));
```

Rate-limit on something else (API key, provider, composite) by swapping the `KeyStrategy`:

```java
KeyStrategy<RequestCtx> perProvider =
        ctx -> "tenant:" + ctx.tenantId() + "|provider:" + ctx.provider();
```

---

## Key design decisions

1. **Strategy interface, not inheritance.** `RateLimiter` is a plain interface; each algorithm is an independent implementation. New algorithms can't break old ones (Open/Closed).
2. **Factory registry.** `RateLimiterFactory.register(type, builder)` adds an algorithm in one line. Callers never reference concrete classes.
3. **`ExternalCallGuard` façade.** Business code depends on one class, not on an algorithm. Switching algorithms is a composition-root change (Dependency Inversion).
4. **Limit only at the external-call site.** `BusinessService` runs business logic first and only calls the guard when the paid API is actually needed, so cached paths never consume quota.
5. **`KeyStrategy<CTX>`** decouples *what* you limit on from *how* you limit. Same limiter works per tenant, per API key, per provider, or a composite.
6. **Thread safety.** Outer `ConcurrentHashMap<String, Bucket>` gives lock-free lookup; contention is per-key, bounded by a short `synchronized(bucket)` block covering the read-modify-write. Verified with a 16-thread × 500-op test against a 1000-limit bucket — exactly 1000 allowed.
7. **Pluggable `Clock`.** Algorithms take a `Clock`; tests use a `FakeClock` so behavior is deterministic without `Thread.sleep`.
8. **O(1) time and O(1) space per key** for both algorithms. No per-request allocations on the hot path.

---

## Fixed vs. Sliding — trade-offs

|                    | Fixed Window                                                          | Sliding Window (weighted)                                                                         |
| ------------------ | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Memory per key     | 2 longs                                                               | 3 longs                                                                                           |
| CPU per request    | trivial                                                               | trivial (one division)                                                                            |
| Boundary burst     | Can allow **2× limit** across a window edge (5 at t=59.9s + 5 at 60s) | Smoothed by weighting the previous window — see `slidingWindow_preventsBoundaryBurst` test        |
| Accuracy           | Exact inside a window, lumpy across edges                             | Approximation (assumes uniform distribution in the previous window); tiny error for real traffic  |
| Reset behavior     | Hard reset — aligns with billing cycles                               | Decays smoothly                                                                                   |
| Best for           | Cheap, billing-aligned quotas where occasional bursts are fine        | Protecting a **paid downstream** where sustained rate matters                                     |

For a paid external resource the sliding window is usually the safer default: the cost of a burst is real money, while the approximation error is negligible.

---

## Adding a new algorithm (e.g. Token Bucket)

1. Write `TokenBucket implements RateLimiter`.
2. In `RateLimiterFactory`, add one line:
   ```java
   register(AlgorithmType.TOKEN_BUCKET, TokenBucket::new);
   ```
3. Done. `ExternalCallGuard`, `BusinessService`, and `KeyStrategy` are untouched.

---

## Build and run

No build tool required — plain `javac` / `java`:

```bash
find src -name "*.java" > sources.txt
mkdir -p out
javac -d out @sources.txt

# run the test suite (zero dependencies)
java -cp out com.ratelimiter.RateLimiterTests

# run the end-to-end demo
java -cp out com.ratelimiter.demo.Demo
```

Expected test output:

```
PASS  fixed #1 allow
...
PASS  fixed thread-safe exact limit

14 passed, 0 failed
```

---

## Example output (demo)

```
=== FIXED_WINDOW (5/min, tenant=T1) ===
cached  -> served-from-cache
cached  -> served-from-cache
cached  -> served-from-cache
external-> external-response-for-T1
external-> external-response-for-T1
external-> external-response-for-T1
external-> external-response-for-T1
external-> external-response-for-T1
external-> rejected(retryAfterMs=...)
external-> rejected(retryAfterMs=...)

=== SLIDING_WINDOW (5/min, tenant=T1) ===
... same shape, identical business code ...
```

The three cached requests never touch the limiter. The first five external requests are allowed; the remaining two are rejected with a `retryAfterMs` hint. The business code is byte-for-byte identical between the two runs — only the algorithm behind the factory changed.
