package com.samuel.app.platform.adapter;

import com.samuel.app.platform.dto.RateLimitInfo;
import com.samuel.app.platform.repository.PlatformConnectionRepository;
import com.samuel.app.platform.service.TokenEncryptionService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InstagramAdapter.
 * Tests circuit breaker state mapping, rate limits, and platform type.
 * Follows TikTokAdapterTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class InstagramAdapterTest {

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

    private InstagramAdapter instagramAdapter;

    @BeforeEach
    void setUp() {
        instagramAdapter = new InstagramAdapter(
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
        when(circuitBreakerRegistry.circuitBreaker("instagram-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        // When
        ConnectionStatus status = instagramAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }

    @Test
    void should_return_connected_when_circuit_breaker_closed() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("instagram-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // When
        ConnectionStatus status = instagramAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CONNECTED, status);
    }

    @Test
    void should_return_circuit_open_when_circuit_breaker_half_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("instagram-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);

        // When
        ConnectionStatus status = instagramAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }

    @Test
    void should_return_disconnected_when_circuit_breaker_disabled() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("instagram-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.DISABLED);

        // When
        ConnectionStatus status = instagramAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.DISCONNECTED, status);
    }

    @Test
    void should_return_circuit_open_when_circuit_breaker_forced_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("instagram-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.FORCED_OPEN);

        // When
        ConnectionStatus status = instagramAdapter.getConnectionStatus();

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
        when(rateLimiterRegistry.rateLimiter("instagram-api")).thenReturn(rateLimiter);
        when(rateLimiter.getMetrics()).thenReturn(rateLimiterMetrics);
        when(rateLimiterMetrics.getAvailablePermissions()).thenReturn(availablePermissions);

        // When
        RateLimitInfo rateLimitInfo = instagramAdapter.getRemainingQuota();

        // Then
        assertNotNull(rateLimitInfo);
        assertEquals(availablePermissions, rateLimitInfo.remainingCalls());
        assertEquals(200, rateLimitInfo.totalLimit()); // Instagram rate limit
        assertEquals(PlatformType.INSTAGRAM, rateLimitInfo.platformType());
        
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
        LocalDateTime resetTime = instagramAdapter.getNextResetTime();

        // Then
        LocalDateTime expectedResetTime = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(3600);
        assertTrue(resetTime.isAfter(expectedResetTime.minusMinutes(1)));
        assertTrue(resetTime.isBefore(expectedResetTime.plusMinutes(1)));
    }

    // ────────────────────────────────────────────────────────────
    // getPlatformType
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_instagram_platform_type() {
        // When
        PlatformType platformType = instagramAdapter.getPlatformType();

        // Then
        assertEquals(PlatformType.INSTAGRAM, platformType);
    }

    // ────────────────────────────────────────────────────────────
    // getRevenueData
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_empty_revenue_data_for_instagram() {
        // Given
        String platformUserId = "ig-user-123";

        // When
        var revenueDataOpt = instagramAdapter.getRevenueData(platformUserId, null);

        // Then
        assertTrue(revenueDataOpt.isEmpty());
    }
}