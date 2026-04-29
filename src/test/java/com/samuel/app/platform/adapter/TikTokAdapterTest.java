package com.samuel.app.platform.adapter;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.platform.dto.ContentMetrics;
import com.samuel.app.platform.dto.DateRange;
import com.samuel.app.platform.dto.PlatformCredentials;
import com.samuel.app.platform.dto.RateLimitInfo;
import com.samuel.app.platform.dto.RevenueData;
import com.samuel.app.platform.exception.PlatformApiException;
import com.samuel.app.platform.model.PlatformConnection;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TikTokAdapter.
 * Tests circuit breaker integration, rate limiting, and platform API methods.
 * Mirrors YouTubeAdapterTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class TikTokAdapterTest {

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

    private TikTokAdapter tikTokAdapter;

    @BeforeEach
    void setUp() {
        tikTokAdapter = new TikTokAdapter(
                circuitBreakerRegistry,
                rateLimiterRegistry,
                platformConnectionRepository,
                tokenEncryptionService,
                restTemplate
        );
    }

    // ────────────────────────────────────────────────────────────
    // getConnectionStatus — circuit breaker state mapping
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_circuit_open_when_circuit_breaker_state_is_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("tiktok-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        // When
        ConnectionStatus status = tikTokAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }

    @Test
    void should_return_connected_when_circuit_breaker_closed() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("tiktok-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        // When
        ConnectionStatus status = tikTokAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CONNECTED, status);
    }

    @Test
    void should_return_circuit_open_when_circuit_breaker_half_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("tiktok-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);

        // When
        ConnectionStatus status = tikTokAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }

    @Test
    void should_return_disconnected_when_circuit_breaker_disabled() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("tiktok-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.DISABLED);

        // When
        ConnectionStatus status = tikTokAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.DISCONNECTED, status);
    }

    // ────────────────────────────────────────────────────────────
    // getPlatformType
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_tiktok_platform_type() {
        // When
        PlatformType platformType = tikTokAdapter.getPlatformType();

        // Then
        assertEquals(PlatformType.TIKTOK, platformType);
    }

    // ────────────────────────────────────────────────────────────
    // getRemainingQuota — TikTok: 50 req / 60s
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_rate_limit_info_with_50_limit() {
        // Given
        when(rateLimiterRegistry.rateLimiter("tiktok-api")).thenReturn(rateLimiter);
        when(rateLimiter.getMetrics()).thenReturn(rateLimiterMetrics);
        when(rateLimiterMetrics.getAvailablePermissions()).thenReturn(35);

        // When
        RateLimitInfo info = tikTokAdapter.getRemainingQuota();

        // Then
        assertEquals(35, info.remainingCalls());
        assertEquals(50, info.totalLimit());
        assertEquals(PlatformType.TIKTOK, info.platformType());
        assertNotNull(info.resetAt());
    }

    // ────────────────────────────────────────────────────────────
    // getRevenueData — always empty for TikTok
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_empty_revenue_data_for_tiktok() throws Exception {
        // Given
        DateRange range = new DateRange(LocalDate.now().minusDays(30), LocalDate.now());

        // When
        Optional<RevenueData> result = tikTokAdapter.getRevenueData("open-id-abc", range);

        // Then
        assertTrue(result.isEmpty());
    }
}
