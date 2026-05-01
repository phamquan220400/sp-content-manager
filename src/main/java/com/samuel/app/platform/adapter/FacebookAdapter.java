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
 * Facebook platform adapter implementing standard platform integration patterns.
 * Provides circuit breaker protected access to Meta Graph API.
 * Rate limit: 200 requests / 3600s window (per application-dev.yml facebook-api config).
 */
@Component("facebookAdapter")
public class FacebookAdapter implements IPlatformAdapter {

    private static final String META_PAGE_URL_TEMPLATE = "https://graph.facebook.com/v18.0/%s?fields=id,name";
    private static final int FACEBOOK_RATE_LIMIT = 200;
    private static final int FACEBOOK_RATE_LIMIT_WINDOW_SECONDS = 3600;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final RestTemplate restTemplate;

    public FacebookAdapter(
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
     * Validates existing Facebook connection by calling Meta Graph API.
     * The actual OAuth flow is handled by FacebookConnectionService.
     */
    @Override
    @CircuitBreaker(name = "facebook-api", fallbackMethod = "connectFallback")
    @RateLimiter(name = "facebook-api")
    @Retry(name = "facebook-api")
    public PlatformConnection connect(CreatorProfile creator, PlatformCredentials creds)
            throws PlatformConnectionException {
        Optional<PlatformConnection> connectionOpt = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creator.getId(), PlatformType.FACEBOOK);

        if (connectionOpt.isEmpty()) {
            throw new PlatformConnectionException(PlatformType.FACEBOOK, "No existing Facebook connection found");
        }

        PlatformConnection connection = connectionOpt.get();
        if (connection.getAccessTokenEncrypted() == null) {
            throw new PlatformConnectionException(PlatformType.FACEBOOK, "Facebook connection has no valid access token");
        }

        try {
            String accessToken = tokenEncryptionService.decrypt(connection.getAccessTokenEncrypted());
            String pageId = connection.getPlatformUserId();
            validatePageToken(pageId, accessToken);
            return connection;
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.FACEBOOK, "Facebook connection validation failed", e);
        }
    }

    /**
     * Fallback method for connect operation when circuit breaker is open.
     */
    public PlatformConnection connectFallback(CreatorProfile creator, PlatformCredentials creds, Exception ex) {
        throw new PlatformConnectionException(PlatformType.FACEBOOK, "Facebook API circuit breaker is OPEN");
    }

    /**
     * Gets connection status based on circuit breaker state.
     * Maps circuit breaker states to platform ConnectionStatus enum values.
     */
    @Override
    public ConnectionStatus getConnectionStatus() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("facebook-api");
        io.github.resilience4j.circuitbreaker.CircuitBreaker.State state = circuitBreaker.getState();

        return switch (state) {
            case CLOSED -> ConnectionStatus.CONNECTED;
            case HALF_OPEN -> ConnectionStatus.CONNECTED;
            case OPEN -> ConnectionStatus.CIRCUIT_OPEN;
            case DISABLED -> ConnectionStatus.DISCONNECTED;
            case FORCED_OPEN -> ConnectionStatus.CIRCUIT_OPEN;
            default -> ConnectionStatus.DISCONNECTED;
        };
    }

    /**
     * Gets remaining rate limit quota for Facebook API.
     * Facebook rate limit: 200 requests per 3600-second window (1 hour).
     */
    @Override
    public RateLimitInfo getRemainingQuota() {
        io.github.resilience4j.ratelimiter.RateLimiter rateLimiter =
                rateLimiterRegistry.rateLimiter("facebook-api");
        int availablePermissions = rateLimiter.getMetrics().getAvailablePermissions();
        LocalDateTime resetAt = getNextResetTime();

        return new RateLimitInfo(
                availablePermissions,
                FACEBOOK_RATE_LIMIT,
                resetAt,
                PlatformType.FACEBOOK
        );
    }

    /**
     * Gets next rate limit reset time for Facebook (3600-second rolling window).
     * Meta rate limit resets every hour.
     */
    @Override
    public LocalDateTime getNextResetTime() {
        return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(FACEBOOK_RATE_LIMIT_WINDOW_SECONDS);
    }

    /**
     * Fetches content metrics for a Facebook page.
     * Currently returns zeroed values as page metrics are not yet implemented.
     */
    @Override
    @CircuitBreaker(name = "facebook-api", fallbackMethod = "fetchMetricsFallback")
    @RateLimiter(name = "facebook-api")
    @Retry(name = "facebook-api")
    public Optional<ContentMetrics> fetchMetrics(String platformUserId) throws PlatformApiException {
        // Return empty metrics for now - Facebook page metrics not implemented yet
        ContentMetrics metrics = new ContentMetrics(
                platformUserId,
                PlatformType.FACEBOOK,
                0L, // View count not implemented
                0L, // Like count not implemented
                0L, // Comment count not implemented
                0L, // Share count not implemented
                LocalDateTime.now()
        );

        return Optional.of(metrics);
    }

    /**
     * Fallback method for fetchMetrics operation when circuit breaker is open.
     */
    public Optional<ContentMetrics> fetchMetricsFallback(String platformUserId, Exception ex) {
        return Optional.empty();
    }

    /**
     * Facebook revenue data is not available in this story.
     * Returns empty Optional.
     */
    @Override
    @CircuitBreaker(name = "facebook-api", fallbackMethod = "getRevenueDataFallback")
    public Optional<RevenueData> getRevenueData(String platformUserId, DateRange range)
            throws PlatformApiException {
        return Optional.empty();
    }

    /**
     * Fallback method for getRevenueData operation when circuit breaker is open.
     */
    public Optional<RevenueData> getRevenueDataFallback(String platformUserId, DateRange range, Exception ex) {
        return Optional.empty();
    }

    /**
     * Returns the platform type this adapter handles.
     */
    @Override
    public PlatformType getPlatformType() {
        return PlatformType.FACEBOOK;
    }

    /**
     * Validates Facebook page connection by calling Meta Graph API.
     */
    private void validatePageToken(String pageId, String accessToken) {
        String validationUrl = UriComponentsBuilder
                .fromHttpUrl(String.format(META_PAGE_URL_TEMPLATE, pageId))
                .queryParam("access_token", accessToken)
                .build()
                .toUriString();

        // Call Meta Graph API to validate the page token is still valid
        restTemplate.getForObject(validationUrl, Object.class);
    }
}