package com.ratelimiter.clock;

/**
 * Abstraction over time so algorithms are deterministic in tests.
 */
@FunctionalInterface
public interface Clock {

    long nowMillis();

    Clock SYSTEM = System::currentTimeMillis;
}
