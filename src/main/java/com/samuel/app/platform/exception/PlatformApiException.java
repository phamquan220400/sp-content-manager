package com.samuel.app.platform.exception;

import com.samuel.app.platform.adapter.PlatformType;

/**
 * Exception thrown when a platform API call fails with an error response.
 * Contains platform-specific error information and retry eligibility.
 */
public class PlatformApiException extends RuntimeException {

    private final PlatformType platformType;
    private final int platformStatusCode;
    private final String platformErrorCode;
    private final boolean retryable;

    public PlatformApiException(PlatformType platformType, int platformStatusCode, 
                              String platformErrorCode, boolean retryable) {
        super(String.format("Platform API call failed for %s: [%d] %s (retryable: %s)", 
                           platformType, platformStatusCode, platformErrorCode, retryable));
        this.platformType = platformType;
        this.platformStatusCode = platformStatusCode;
        this.platformErrorCode = platformErrorCode;
        this.retryable = retryable;
    }

    public PlatformApiException(PlatformType platformType, int platformStatusCode, 
                              String platformErrorCode, boolean retryable, Throwable cause) {
        super(String.format("Platform API call failed for %s: [%d] %s (retryable: %s)", 
                           platformType, platformStatusCode, platformErrorCode, retryable), cause);
        this.platformType = platformType;
        this.platformStatusCode = platformStatusCode;
        this.platformErrorCode = platformErrorCode;
        this.retryable = retryable;
    }

    public PlatformType getPlatformType() {
        return platformType;
    }

    public int getPlatformStatusCode() {
        return platformStatusCode;
    }

    public String getPlatformErrorCode() {
        return platformErrorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}