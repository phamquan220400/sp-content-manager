package com.samuel.app.platform.exception;

import com.samuel.app.platform.adapter.PlatformType;

import java.time.LocalDateTime;

/**
 * Exception thrown when platform API quota has been exceeded.
 * Indicates that the API rate limit has been reached and requests should be paused.
 */
public class QuotaExceededException extends RuntimeException {

    private final PlatformType platformType;
    private final LocalDateTime resetsAt;

    public QuotaExceededException(PlatformType platformType, LocalDateTime resetsAt) {
        super(String.format("API quota exceeded for %s, resets at %s", platformType, resetsAt));
        this.platformType = platformType;
        this.resetsAt = resetsAt;
    }

    public QuotaExceededException(PlatformType platformType, LocalDateTime resetsAt, Throwable cause) {
        super(String.format("API quota exceeded for %s, resets at %s", platformType, resetsAt), cause);
        this.platformType = platformType;
        this.resetsAt = resetsAt;
    }

    public PlatformType getPlatformType() {
        return platformType;
    }

    public LocalDateTime getResetsAt() {
        return resetsAt;
    }
}