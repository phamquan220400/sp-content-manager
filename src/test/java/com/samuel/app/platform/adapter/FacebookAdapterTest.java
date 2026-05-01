package com.samuel.app.platform.adapter;

import com.samuel.app.platform.dto.ContentMetrics;
import com.samuel.app.platform.dto.DateRange;
import com.samuel.app.platform.dto.RateLimitInfo;
import com.samuel.app.platform.dto.RevenueData;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import com.samuel.app.platform.repository.PlatformConnectionRepository;
import com.samuel.app.platform.service.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FacebookAdapter.
 * Tests circuit breaker state mapping, rate limits, and platform type.
 * Follows InstagramAdapterTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class FacebookAdapterTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private RateLimiterRegistry rateLimiterRegistry;

    @Mock
    private PlatformConnectionRepository platformConnectionRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CircuitBreaker circuitBreaker;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private RateLimiter.Metrics rateLimiterMetrics;

    private FacebookAdapter facebookAdapter;

    @BeforeEach
    void setUp() {
        facebookAdapter = new FacebookAdapter(
                circuitBreakerRegistry,
                rateLimiterRegistry,
                platformConnectionRepository,
                tokenEncryptionService,
                restTemplate
        );
    }

    // ────────────────────────────────────────────────────────────
    // getConnectionStatus
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_circuit_open_when_circuit_breaker_state_is_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("facebook-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        // When
        ConnectionStatus status = facebookAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }

    @Test
    void should_return_connected_when_circuit_breaker_closed() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("facebook-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // When
        ConnectionStatus status = facebookAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CONNECTED, status);
    }

    @Test
    void should_return_connected_when_circuit_breaker_half_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("facebook-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);

        // When
        ConnectionStatus status = facebookAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CONNECTED, status);
    }

    @Test
    void should_return_disconnected_when_circuit_breaker_disabled() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("facebook-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.DISABLED);

        // When
        ConnectionStatus status = facebookAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.DISCONNECTED, status);
    }

    @Test
    void should_return_circuit_open_when_circuit_breaker_forced_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("facebook-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.FORCED_OPEN);

        // When
        ConnectionStatus status = facebookAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }

    // ────────────────────────────────────────────────────────────
    // getRemainingQuota
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_rate_limit_info_with_200_limit_and_3600s_window() {
        // Given
        int availablePermissions = 150;
        when(rateLimiterRegistry.rateLimiter("facebook-api")).thenReturn(rateLimiter);
        when(rateLimiter.getMetrics()).thenReturn(rateLimiterMetrics);
        when(rateLimiterMetrics.getAvailablePermissions()).thenReturn(availablePermissions);

        // When
        RateLimitInfo rateLimitInfo = facebookAdapter.getRemainingQuota();

        // Then
        assertNotNull(rateLimitInfo);
        assertEquals(availablePermissions, rateLimitInfo.remainingCalls());
        assertEquals(200, rateLimitInfo.totalLimit()); // Facebook rate limit
        assertEquals(PlatformType.FACEBOOK, rateLimitInfo.platformType());
        
        // Verify reset time is approximately 3600 seconds from now (1 hour)
        LocalDateTime expectedResetTime = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(3600);
        assertTrue(rateLimitInfo.resetAt().isAfter(expectedResetTime.minusMinutes(1)));
        assertTrue(rateLimitInfo.resetAt().isBefore(expectedResetTime.plusMinutes(1)));
    }

    // ────────────────────────────────────────────────────────────
    // getNextResetTime
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_reset_time_3600_seconds_from_now() {
        // Given & When
        LocalDateTime resetTime = facebookAdapter.getNextResetTime();

        // Then
        LocalDateTime expectedResetTime = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(3600);
        assertTrue(resetTime.isAfter(expectedResetTime.minusMinutes(1)));
        assertTrue(resetTime.isBefore(expectedResetTime.plusMinutes(1)));
    }

    // ────────────────────────────────────────────────────────────
    // getPlatformType
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_facebook_platform_type() {
        // When
        PlatformType platformType = facebookAdapter.getPlatformType();

        // Then
        assertEquals(PlatformType.FACEBOOK, platformType);
    }

    // ────────────────────────────────────────────────────────────
    // fetchMetrics
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_zeroed_content_metrics_for_facebook() {
        // Given
        String platformUserId = "page-123";

        // When
        Optional<ContentMetrics> metricsOpt = facebookAdapter.fetchMetrics(platformUserId);

        // Then
        assertTrue(metricsOpt.isPresent());
        ContentMetrics metrics = metricsOpt.get();
        assertEquals(platformUserId, metrics.platformUserId());
        assertEquals(PlatformType.FACEBOOK, metrics.platformType());
        assertEquals(0L, metrics.viewCount());
        assertEquals(0L, metrics.likeCount());
        assertEquals(0L, metrics.commentCount());
        assertEquals(0L, metrics.shareCount());
        assertNotNull(metrics.fetchedAt());
    }

    // ────────────────────────────────────────────────────────────
    // getRevenueData
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_empty_revenue_data_for_facebook() {
        // Given
        String platformUserId = "page-123";
        DateRange dateRange = null;

        // When
        Optional<RevenueData> revenueDataOpt = facebookAdapter.getRevenueData(platformUserId, dateRange);

        // Then
        assertTrue(revenueDataOpt.isEmpty());
    }
}