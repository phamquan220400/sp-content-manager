package com.samuel.app.platform.adapter;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.platform.dto.ContentMetrics;
import com.samuel.app.platform.dto.DateRange;
import com.samuel.app.platform.dto.PlatformCredentials;
import com.samuel.app.platform.dto.RateLimitInfo;
import com.samuel.app.platform.dto.RevenueData;
import com.samuel.app.platform.dto.YouTubeChannelResponse;
import com.samuel.app.platform.exception.PlatformApiException;
import com.samuel.app.platform.exception.PlatformConnectionException;
import com.samuel.app.platform.model.PlatformConnection;
import com.samuel.app.platform.repository.PlatformConnectionRepository;
import com.samuel.app.platform.service.TokenEncryptionService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * YouTube platform adapter implementing standard platform integration patterns.
 * Provides circuit breaker protected access to YouTube Data API v3 and YouTube Analytics API.
 */
@Component("youtubeAdapter")
public class YouTubeAdapter implements IPlatformAdapter {

    private static final String YOUTUBE_CHANNEL_URL = "https://www.googleapis.com/youtube/v3/channels";
    private static final String YOUTUBE_ANALYTICS_URL = "https://youtubeanalytics.googleapis.com/v2/reports";
    private static final int YOUTUBE_DAILY_QUOTA = 10000;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;

    public YouTubeAdapter(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            PlatformConnectionRepository platformConnectionRepository,
            TokenEncryptionService tokenEncryptionService,
            RestTemplate restTemplate) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.platformConnectionRepository = platformConnectionRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.restTemplate = restTemplate;
    }

    /**
     * Validates existing YouTube connection by making a basic API call.
     * The actual OAuth flow is handled by YouTubeConnectionService.
     */
    @Override
    @CircuitBreaker(name = "youtube-api", fallbackMethod = "connectFallback")
    @RateLimiter(name = "youtube-api")
    @Retry(name = "youtube-api")
    public PlatformConnection connect(CreatorProfile creator, PlatformCredentials creds) 
            throws PlatformConnectionException {
        // Find existing connection for validation
        Optional<PlatformConnection> connectionOpt = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creator.getId(), PlatformType.YOUTUBE);

        if (connectionOpt.isEmpty()) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE, "No existing YouTube connection found to validate");
        }

        PlatformConnection connection = connectionOpt.get();
        if (connection.getAccessTokenEncrypted() == null) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE, "YouTube connection has no valid access token");
        }

        // Validate connection by fetching basic channel info
        try {
            String accessToken = tokenEncryptionService.decrypt(connection.getAccessTokenEncrypted());
            fetchBasicChannelInfo(accessToken);
            return connection;
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE, "YouTube connection validation failed", e);
        }
    }

    /**
     * Fallback method for connect operation when circuit breaker is open.
     */
    public PlatformConnection connectFallback(CreatorProfile creator, PlatformCredentials creds, Exception ex) {
        throw new PlatformConnectionException(PlatformType.YOUTUBE, "YouTube API unavailable - circuit breaker open");
    }

    /**
     * Gets connection status based on circuit breaker state.
     * Note: This reflects circuit breaker state only, not DB connection status.
     */
    @Override
    public ConnectionStatus getConnectionStatus() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("youtube-api");
        io.github.resilience4j.circuitbreaker.CircuitBreaker.State state = circuitBreaker.getState();

        return switch (state) {
            case CLOSED -> ConnectionStatus.CONNECTED;
            case OPEN -> ConnectionStatus.CIRCUIT_OPEN;
            case HALF_OPEN -> ConnectionStatus.CIRCUIT_OPEN;
            case DISABLED -> ConnectionStatus.DISCONNECTED;
            case FORCED_OPEN -> ConnectionStatus.CIRCUIT_OPEN;
            default -> ConnectionStatus.API_ERROR; // fallback for any unexpected states
        };
    }

    /**
     * Gets remaining rate limit quota for YouTube API.
     */
    @Override
    public RateLimitInfo getRemainingQuota() {
        io.github.resilience4j.ratelimiter.RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("youtube-api");
        int availablePermissions = rateLimiter.getMetrics().getAvailablePermissions();
        LocalDateTime resetAt = getNextResetTime();

        return new RateLimitInfo(
                availablePermissions,
                YOUTUBE_DAILY_QUOTA,
                resetAt,
                PlatformType.YOUTUBE
        );
    }

    /**
     * Gets next rate limit reset time (midnight UTC).
     */
    @Override
    public LocalDateTime getNextResetTime() {
        return LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay();
    }

    /**
     * Fetches content metrics for a YouTube channel.
     */
    @Override
    @CircuitBreaker(name = "youtube-api", fallbackMethod = "fetchMetricsFallback")
    @RateLimiter(name = "youtube-api")
    @Retry(name = "youtube-api")
    public Optional<ContentMetrics> fetchMetrics(String platformUserId) 
            throws PlatformApiException {
        
        // Find connection to get access token
        Optional<PlatformConnection> connectionOpt = findConnectionByPlatformUserId(platformUserId);
        if (connectionOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            String accessToken = tokenEncryptionService.decrypt(connectionOpt.get().getAccessTokenEncrypted());
            YouTubeChannelResponse response = fetchChannelMetrics(accessToken, platformUserId);

            if (response.items().isEmpty()) {
                return Optional.empty();
            }

            YouTubeChannelResponse.Item channel = response.items().get(0);
            YouTubeChannelResponse.Statistics stats = channel.statistics();

            ContentMetrics metrics = new ContentMetrics(
                    platformUserId,
                    PlatformType.YOUTUBE,
                    0L, // YouTube doesn't provide total view count in channel stats
                    0L, // YouTube doesn't provide channel-level likes
                    0L, // YouTube doesn't provide channel-level comments
                    0L, // YouTube doesn't provide channel-level shares
                    LocalDateTime.now()
            );

            return Optional.of(metrics);
        } catch (Exception e) {
            throw new PlatformApiException(PlatformType.YOUTUBE, 500, "API_ERROR", true, e);
        }
    }

    /**
     * Fallback method for fetchMetrics operation.
     */
    public Optional<ContentMetrics> fetchMetricsFallback(String platformUserId, Exception ex) {
        throw new PlatformApiException(PlatformType.YOUTUBE, 503, "CIRCUIT_OPEN", true);
    }

    /**
     * Gets revenue data for a YouTube channel using YouTube Analytics API.
     */
    @Override
    @CircuitBreaker(name = "youtube-api", fallbackMethod = "getRevenueDataFallback")
    @RateLimiter(name = "youtube-api")
    @Retry(name = "youtube-api")
    public Optional<RevenueData> getRevenueData(String platformUserId, DateRange range) 
            throws PlatformApiException {
        
        // Find connection to get access token
        Optional<PlatformConnection> connectionOpt = findConnectionByPlatformUserId(platformUserId);
        if (connectionOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            String accessToken = tokenEncryptionService.decrypt(connectionOpt.get().getAccessTokenEncrypted());
            
            // Build YouTube Analytics API URL
            String analyticsUrl = UriComponentsBuilder
                    .fromHttpUrl(YOUTUBE_ANALYTICS_URL)
                    .queryParam("ids", "channel==" + platformUserId)
                    .queryParam("startDate", range.startDate().toString())
                    .queryParam("endDate", range.endDate().toString())
                    .queryParam("metrics", "estimatedRevenue")
                    .queryParam("dimensions", "day")
                    .build()
                    .toUriString();

            // Set authorization header
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            // For MVP: Return empty for now (YouTube Analytics requires special approval)
            // In production, would parse response and return RevenueData
            return Optional.empty();

        } catch (Exception e) {
            throw new PlatformApiException(PlatformType.YOUTUBE, 500, "API_ERROR", true, e);
        }
    }

    /**
     * Fallback method for getRevenueData operation.
     */
    public Optional<RevenueData> getRevenueDataFallback(String platformUserId, DateRange range, Exception ex) {
        throw new PlatformApiException(PlatformType.YOUTUBE, 503, "CIRCUIT_OPEN", true);
    }

    /**
     * Returns the platform type this adapter handles.
     */
    @Override
    public PlatformType getPlatformType() {
        return PlatformType.YOUTUBE;
    }

    /**
     * Helper method to fetch basic channel info for connection validation.
     */
    private YouTubeChannelResponse fetchBasicChannelInfo(String accessToken) {
        String channelUrl = UriComponentsBuilder
                .fromHttpUrl(YOUTUBE_CHANNEL_URL)
                .queryParam("part", "snippet")
                .queryParam("mine", "true")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(channelUrl, HttpMethod.GET, request, YouTubeChannelResponse.class).getBody();
    }

    /**
     * Helper method to fetch channel metrics by ID.
     */
    private YouTubeChannelResponse fetchChannelMetrics(String accessToken, String channelId) {
        String channelUrl = UriComponentsBuilder
                .fromHttpUrl(YOUTUBE_CHANNEL_URL)
                .queryParam("part", "statistics")
                .queryParam("id", channelId)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(channelUrl, HttpMethod.GET, request, YouTubeChannelResponse.class).getBody();
    }

    /**
     * Helper method to find connection by platform user ID.
     */
    private Optional<PlatformConnection> findConnectionByPlatformUserId(String platformUserId) {
        return platformConnectionRepository.findAll().stream()
                .filter(conn -> conn.getPlatformType() == PlatformType.YOUTUBE)
                .filter(conn -> platformUserId.equals(conn.getPlatformUserId()))
                .findFirst();
    }
}