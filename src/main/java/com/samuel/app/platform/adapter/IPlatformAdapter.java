package com.samuel.app.platform.adapter;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.platform.dto.ContentMetrics;
import com.samuel.app.platform.dto.DateRange;
import com.samuel.app.platform.dto.PlatformCredentials;
import com.samuel.app.platform.dto.RateLimitInfo;
import com.samuel.app.platform.dto.RevenueData;
import com.samuel.app.platform.exception.PlatformApiException;
import com.samuel.app.platform.exception.PlatformConnectionException;
import com.samuel.app.platform.exception.QuotaExceededException;
import com.samuel.app.platform.exception.RateLimitException;
import com.samuel.app.platform.model.PlatformConnection;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Platform adapter interface defining the contract for all social media platform integrations.
 * Provides standardized methods for connection management, API interaction, and metrics retrieval
 * with built-in support for circuit breaker patterns and rate limiting.
 */
public interface IPlatformAdapter {

    /**
     * Establishes a connection to the platform using OAuth credentials.
     * 
     * @param creator The creator profile requesting the connection
     * @param creds Platform-specific OAuth credentials
     * @return PlatformConnection entity representing the established connection
     * @throws PlatformConnectionException if OAuth or connection setup fails
     * @throws RateLimitException if rate limiter rejects the request
     */
    PlatformConnection connect(CreatorProfile creator, PlatformCredentials creds) 
            throws PlatformConnectionException, RateLimitException;

    /**
     * Gets the current connection status for this adapter.
     * 
     * @return Current connection status (CONNECTED, DISCONNECTED, CIRCUIT_OPEN, API_ERROR, RATE_LIMITED)
     */
    ConnectionStatus getConnectionStatus();

    /**
     * Retrieves current rate limit information for this platform.
     * 
     * @return Rate limit details including remaining calls and reset time
     */
    RateLimitInfo getRemainingQuota();

    /**
     * Gets the next rate limit reset time for this platform.
     * 
     * @return LocalDateTime when the rate limit window resets
     */
    LocalDateTime getNextResetTime();

    /**
     * Fetches content metrics for a specific platform user.
     * 
     * @param platformUserId The platform-specific user identifier
     * @return Optional containing content metrics if successful, empty if user not found
     * @throws PlatformApiException if the API call fails
     * @throws QuotaExceededException if API quota is exhausted
     */
    Optional<ContentMetrics> fetchMetrics(String platformUserId) 
            throws PlatformApiException, QuotaExceededException;

    /**
     * Retrieves revenue data for a specific platform user within a date range.
     * 
     * @param platformUserId The platform-specific user identifier
     * @param range Date range for revenue data retrieval
     * @return Optional containing revenue data if available, empty if no data found
     * @throws PlatformApiException if the API call fails
     * @throws QuotaExceededException if API quota is exhausted
     */
    Optional<RevenueData> getRevenueData(String platformUserId, DateRange range) 
            throws PlatformApiException, QuotaExceededException;

    /**
     * Returns the platform type this adapter handles.
     * 
     * @return PlatformType enum value (YOUTUBE, TIKTOK, INSTAGRAM, FACEBOOK)
     */
    PlatformType getPlatformType();
}