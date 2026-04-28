package com.samuel.app.platform.exception;

import com.samuel.app.platform.adapter.PlatformType;

/**
 * Exception thrown when a platform connection fails during OAuth or authorization.
 * Indicates issues with connecting to the platform's API endpoints.
 */
public class PlatformConnectionException extends RuntimeException {

    private final PlatformType platformType;
    private final String reason;

    public PlatformConnectionException(PlatformType platformType, String reason) {
        super(String.format("Platform connection failed for %s: %s", platformType, reason));
        this.platformType = platformType;
        this.reason = reason;
    }

    public PlatformConnectionException(PlatformType platformType, String reason, Throwable cause) {
        super(String.format("Platform connection failed for %s: %s", platformType, reason), cause);
        this.platformType = platformType;
        this.reason = reason;
    }

    public PlatformType getPlatformType() {
        return platformType;
    }

    public String getReason() {
        return reason;
    }
}