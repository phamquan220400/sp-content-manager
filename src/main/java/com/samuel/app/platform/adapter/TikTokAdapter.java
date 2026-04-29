package com.samuel.app.platform.adapter;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.platform.dto.ContentMetrics;
import com.samuel.app.platform.dto.DateRange;
import com.samuel.app.platform.dto.PlatformCredentials;
import com.samuel.app.platform.dto.RateLimitInfo;
import com.samuel.app.platform.dto.RevenueData;
import com.samuel.app.platform.dto.TikTokUserInfoResponse;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * TikTok platform adapter implementing standard platform integration patterns.
 * Provides circuit breaker protected access to TikTok Open API v2.
 * Rate limit: 50 requests / 60s window (per application-dev.yml tiktok-api config).
 */
@Component("tiktokAdapter")
public class TikTokAdapter implements IPlatformAdapter {

    private static final String TIKTOK_USER_INFO_URL =
            "https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,follower_count";
    private static final int TIKTOK_RATE_LIMIT = 50;
    private static final int TIKTOK_RATE_LIMIT_WINDOW_SECONDS = 60;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;

    public TikTokAdapter(
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
     * Validates existing TikTok connection by calling TikTok User Info API.
     * The actual OAuth flow is handled by TikTokConnectionService.
     */
    @Override
    @CircuitBreaker(name = "tiktok-api", fallbackMethod = "connectFallback")
    @RateLimiter(name = "tiktok-api")
    @Retry(name = "tiktok-api")
    public PlatformConnection connect(CreatorProfile creator, PlatformCredentials creds)
            throws PlatformConnectionException {
        Optional<PlatformConnection> connectionOpt = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creator.getId(), PlatformType.TIKTOK);

        if (connectionOpt.isEmpty()) {
            throw new PlatformConnectionException(PlatformType.TIKTOK, "No existing TikTok connection found to validate");
        }

        PlatformConnection connection = connectionOpt.get();
        if (connection.getAccessTokenEncrypted() == null) {
            throw new PlatformConnectionException(PlatformType.TIKTOK, "TikTok connection has no valid access token");
        }

        try {
            String accessToken = tokenEncryptionService.decrypt(connection.getAccessTokenEncrypted());
            fetchUserInfoByToken(accessToken);
            return connection;
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.TIKTOK, "TikTok connection validation failed", e);
        }
    }

    /**
     * Fallback method for connect operation when circuit breaker is open.
     */
    public PlatformConnection connectFallback(CreatorProfile creator, PlatformCredentials creds, Exception ex) {
        throw new PlatformConnectionException(PlatformType.TIKTOK, "TikTok API unavailable - circuit breaker open");
    }

    /**
     * Gets connection status based on circuit breaker state.
     * Maps circuit breaker states to platform ConnectionStatus enum values.
     */
    @Override
    public ConnectionStatus getConnectionStatus() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("tiktok-api");
        io.github.resilience4j.circuitbreaker.CircuitBreaker.State state = circuitBreaker.getState();

        return switch (state) {
            case CLOSED -> ConnectionStatus.CONNECTED;
            case OPEN -> ConnectionStatus.CIRCUIT_OPEN;
            case HALF_OPEN -> ConnectionStatus.CIRCUIT_OPEN;
            case DISABLED -> ConnectionStatus.DISCONNECTED;
            case FORCED_OPEN -> ConnectionStatus.CIRCUIT_OPEN;
            default -> ConnectionStatus.API_ERROR;
        };
    }

    /**
     * Gets remaining rate limit quota for TikTok API.
     * TikTok rate limit: 50 requests per 60-second window.
     */
    @Override
    public RateLimitInfo getRemainingQuota() {
        io.github.resilience4j.ratelimiter.RateLimiter rateLimiter =
                rateLimiterRegistry.rateLimiter("tiktok-api");
        int availablePermissions = rateLimiter.getMetrics().getAvailablePermissions();
        LocalDateTime resetAt = getNextResetTime();

        return new RateLimitInfo(
                availablePermissions,
                TIKTOK_RATE_LIMIT,
                resetAt,
                PlatformType.TIKTOK
        );
    }

    /**
     * Gets next rate limit reset time for TikTok (60-second rolling window).
     * Unlike YouTube's daily quota, TikTok resets every 60 seconds.
     */
    @Override
    public LocalDateTime getNextResetTime() {
        return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(TIKTOK_RATE_LIMIT_WINDOW_SECONDS);
    }

    /**
     * Fetches content metrics for a TikTok account.
     */
    @Override
    @CircuitBreaker(name = "tiktok-api", fallbackMethod = "fetchMetricsFallback")
    @RateLimiter(name = "tiktok-api")
    @Retry(name = "tiktok-api")
    public Optional<ContentMetrics> fetchMetrics(String platformUserId) throws PlatformApiException {
        Optional<PlatformConnection> connectionOpt = findConnectionByPlatformUserId(platformUserId);
        if (connectionOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            String accessToken = tokenEncryptionService.decrypt(connectionOpt.get().getAccessTokenEncrypted());
            TikTokUserInfoResponse response = fetchUserInfoByToken(accessToken);

            if (response == null || response.data() == null || response.data().user() == null) {
                return Optional.empty();
            }

            ContentMetrics metrics = new ContentMetrics(
                    platformUserId,
                    PlatformType.TIKTOK,
                    0L,
                    0L,
                    0L,
                    0L,
                    LocalDateTime.now()
            );

            return Optional.of(metrics);
        } catch (Exception e) {
            throw new PlatformApiException(PlatformType.TIKTOK, 500, "API_ERROR", true, e);
        }
    }

    /**
     * Fallback method for fetchMetrics operation when circuit breaker is open.
     */
    public Optional<ContentMetrics> fetchMetricsFallback(String platformUserId, Exception ex) {
        throw new PlatformApiException(PlatformType.TIKTOK, 503, "CIRCUIT_OPEN", true);
    }

    /**
     * TikTok Creator Fund revenue data is not available via the public API at this scope.
     * Returns empty Optional.
     */
    @Override
    @CircuitBreaker(name = "tiktok-api", fallbackMethod = "getRevenueDataFallback")
    public Optional<RevenueData> getRevenueData(String platformUserId, DateRange range)
            throws PlatformApiException {
        return Optional.empty();
    }

    /**
     * Fallback method for getRevenueData operation when circuit breaker is open.
     */
    public Optional<RevenueData> getRevenueDataFallback(String platformUserId, DateRange range, Exception ex) {
        throw new PlatformApiException(PlatformType.TIKTOK, 503, "CIRCUIT_OPEN", true);
    }

    /**
     * Returns the platform type this adapter handles.
     */
    @Override
    public PlatformType getPlatformType() {
        return PlatformType.TIKTOK;
    }

    /**
     * Fetches TikTok user info by Bearer access token.
     */
    private TikTokUserInfoResponse fetchUserInfoByToken(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(
                TIKTOK_USER_INFO_URL, HttpMethod.GET, request, TikTokUserInfoResponse.class
        ).getBody();
    }

    /**
     * Helper method to find connection by platform user ID.
     */
    private Optional<PlatformConnection> findConnectionByPlatformUserId(String platformUserId) {
        return platformConnectionRepository.findAll().stream()
                .filter(conn -> conn.getPlatformType() == PlatformType.TIKTOK)
                .filter(conn -> platformUserId.equals(conn.getPlatformUserId()))
                .findFirst();
    }
}
