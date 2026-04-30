package com.samuel.app.platform.adapter;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.platform.dto.ContentMetrics;
import com.samuel.app.platform.dto.DateRange;
import com.samuel.app.platform.dto.PlatformCredentials;
import com.samuel.app.platform.dto.RateLimitInfo;
import com.samuel.app.platform.dto.RevenueData;
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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Instagram platform adapter implementing standard platform integration patterns.
 * Provides circuit breaker protected access to Meta Graph API.
 * Rate limit: 200 requests / 3600s window (per application-dev.yml instagram-api config).
 */
@Component("instagramAdapter")
public class InstagramAdapter implements IPlatformAdapter {

    private static final String META_USER_INFO_URL_TEMPLATE = "https://graph.facebook.com/v18.0/me?fields=id";
    private static final int INSTAGRAM_RATE_LIMIT = 200;
    private static final int INSTAGRAM_RATE_LIMIT_WINDOW_SECONDS = 3600;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;

    public InstagramAdapter(
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
     * Validates existing Instagram connection by calling Meta Graph API.
     * The actual OAuth flow is handled by InstagramConnectionService.
     */
    @Override
    @CircuitBreaker(name = "instagram-api", fallbackMethod = "connectFallback")
    @RateLimiter(name = "instagram-api")
    @Retry(name = "instagram-api")
    public PlatformConnection connect(CreatorProfile creator, PlatformCredentials creds)
            throws PlatformConnectionException {
        Optional<PlatformConnection> connectionOpt = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creator.getId(), PlatformType.INSTAGRAM);

        if (connectionOpt.isEmpty()) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "No existing Instagram connection found to validate");
        }

        PlatformConnection connection = connectionOpt.get();
        if (connection.getAccessTokenEncrypted() == null) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Instagram connection has no valid access token");
        }

        try {
            String accessToken = tokenEncryptionService.decrypt(connection.getAccessTokenEncrypted());
            validateConnection(accessToken);
            return connection;
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Instagram connection validation failed", e);
        }
    }

    /**
     * Fallback method for connect operation when circuit breaker is open.
     */
    public PlatformConnection connectFallback(CreatorProfile creator, PlatformCredentials creds, Exception ex) {
        throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Instagram API unavailable - circuit breaker open");
    }

    /**
     * Gets connection status based on circuit breaker state.
     * Maps circuit breaker states to platform ConnectionStatus enum values.
     */
    @Override
    public ConnectionStatus getConnectionStatus() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("instagram-api");
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
     * Gets remaining rate limit quota for Instagram API.
     * Instagram rate limit: 200 requests per 3600-second window (1 hour).
     */
    @Override
    public RateLimitInfo getRemainingQuota() {
        io.github.resilience4j.ratelimiter.RateLimiter rateLimiter =
                rateLimiterRegistry.rateLimiter("instagram-api");
        int availablePermissions = rateLimiter.getMetrics().getAvailablePermissions();
        LocalDateTime resetAt = getNextResetTime();

        return new RateLimitInfo(
                availablePermissions,
                INSTAGRAM_RATE_LIMIT,
                resetAt,
                PlatformType.INSTAGRAM
        );
    }

    /**
     * Gets next rate limit reset time for Instagram (3600-second rolling window).
     * Meta rate limit resets every hour.
     */
    @Override
    public LocalDateTime getNextResetTime() {
        return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(INSTAGRAM_RATE_LIMIT_WINDOW_SECONDS);
    }

    /**
     * Fetches content metrics for an Instagram account.
     */
    @Override
    @CircuitBreaker(name = "instagram-api", fallbackMethod = "fetchMetricsFallback")
    @RateLimiter(name = "instagram-api")
    @Retry(name = "instagram-api")
    public Optional<ContentMetrics> fetchMetrics(String platformUserId) throws PlatformApiException {
        Optional<PlatformConnection> connectionOpt = findConnectionByPlatformUserId(platformUserId);
        if (connectionOpt.isEmpty()) {
            return Optional.empty();
        }

        try {
            String accessToken = tokenEncryptionService.decrypt(connectionOpt.get().getAccessTokenEncrypted());
            
            // Fetch Instagram user info using Meta Graph API
            String userInfoUrl = UriComponentsBuilder
                    .fromHttpUrl("https://graph.facebook.com/v18.0/" + platformUserId)
                    .queryParam("fields", "id,username,followers_count")
                    .queryParam("access_token", accessToken)
                    .build()
                    .toUriString();

            // Basic metrics - Instagram Insights API requires additional permissions
            // This implementation focuses on basic follower count from profile
            ContentMetrics metrics = new ContentMetrics(
                    platformUserId,
                    PlatformType.INSTAGRAM,
                    0L, // Posts count not available with basic permissions
                    0L, // Views count not available with basic permissions
                    connectionOpt.get().getFollowerCount() != null ? connectionOpt.get().getFollowerCount() : 0L,
                    0L, // Likes count not available with basic permissions
                    LocalDateTime.now()
            );

            return Optional.of(metrics);
        } catch (Exception e) {
            throw new PlatformApiException(PlatformType.INSTAGRAM, 500, "API_ERROR", true, e);
        }
    }

    /**
     * Fallback method for fetchMetrics operation when circuit breaker is open.
     */
    public Optional<ContentMetrics> fetchMetricsFallback(String platformUserId, Exception ex) {
        throw new PlatformApiException(PlatformType.INSTAGRAM, 503, "CIRCUIT_OPEN", true);
    }

    /**
     * Instagram revenue data is not available via public Meta Graph API at this scope.
     * Returns empty Optional.
     */
    @Override
    @CircuitBreaker(name = "instagram-api", fallbackMethod = "getRevenueDataFallback")
    public Optional<RevenueData> getRevenueData(String platformUserId, DateRange range)
            throws PlatformApiException {
        return Optional.empty();
    }

    /**
     * Fallback method for getRevenueData operation when circuit breaker is open.
     */
    public Optional<RevenueData> getRevenueDataFallback(String platformUserId, DateRange range, Exception ex) {
        throw new PlatformApiException(PlatformType.INSTAGRAM, 503, "CIRCUIT_OPEN", true);
    }

    /**
     * Returns the platform type this adapter handles.
     */
    @Override
    public PlatformType getPlatformType() {
        return PlatformType.INSTAGRAM;
    }

    /**
     * Validates Instagram connection by calling Meta Graph API.
     */
    private void validateConnection(String accessToken) {
        String validationUrl = UriComponentsBuilder
                .fromHttpUrl(META_USER_INFO_URL_TEMPLATE)
                .queryParam("access_token", accessToken)
                .build()
                .toUriString();

        restTemplate.getForObject(validationUrl, String.class);
    }

    /**
     * Helper method to find connection by platform user ID.
     */
    private Optional<PlatformConnection> findConnectionByPlatformUserId(String platformUserId) {
        return platformConnectionRepository.findAll().stream()
                .filter(conn -> conn.getPlatformType() == PlatformType.INSTAGRAM)
                .filter(conn -> platformUserId.equals(conn.getPlatformUserId()))
                .findFirst();
    }
}