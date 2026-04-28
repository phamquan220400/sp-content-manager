package com.samuel.app.platform.exception;

import com.samuel.app.platform.adapter.PlatformType;

/**
 * Exception thrown when Resilience4j rate limiter rejects a call.
 * Indicates that the request was blocked by the internal rate limiting mechanism.
 */
public class RateLimitException extends RuntimeException {

    private final PlatformType platformType;

    public RateLimitException(PlatformType platformType) {
        super(String.format("Rate limit exceeded for %s - request rejected by rate limiter", platformType));
        this.platformType = platformType;
    }

    public RateLimitException(PlatformType platformType, Throwable cause) {
        super(String.format("Rate limit exceeded for %s - request rejected by rate limiter", platformType), cause);
        this.platformType = platformType;
    }

    public PlatformType getPlatformType() {
        return platformType;
    }
}