package com.ratelimiter.factory;

public enum AlgorithmType {
    FIXED_WINDOW,
    SLIDING_WINDOW,
    TOKEN_BUCKET,     // reserved for future plug-in
    LEAKY_BUCKET,     // reserved for future plug-in
    SLIDING_LOG       // reserved for future plug-in
}
