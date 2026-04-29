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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for YouTubeAdapter.
 * Tests circuit breaker integration, rate limiting, and platform API methods.
 */
@ExtendWith(MockitoExtension.class)
class YouTubeAdapterTest {
    
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
    
    private YouTubeAdapter youTubeAdapter;
    
    @BeforeEach
    void setUp() {
        youTubeAdapter = new YouTubeAdapter(
                circuitBreakerRegistry,
                rateLimiterRegistry,
                platformConnectionRepository,
                tokenEncryptionService,
                restTemplate
        );
    }
    
    @Test
    void should_return_circuit_open_when_circuit_breaker_state_is_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("youtube-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);
        
        // When
        ConnectionStatus status = youTubeAdapter.getConnectionStatus();
        
        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }
    
    @Test
    void should_return_connected_when_circuit_breaker_closed() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("youtube-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        
        // When
        ConnectionStatus status = youTubeAdapter.getConnectionStatus();
        
        // Then
        assertEquals(ConnectionStatus.CONNECTED, status);
    }
    
    @Test
    void should_return_circuit_open_when_circuit_breaker_half_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("youtube-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);
        
        // When
        ConnectionStatus status = youTubeAdapter.getConnectionStatus();
        
        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }
    
    @Test
    void should_return_disconnected_when_circuit_breaker_disabled() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("youtube-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.DISABLED);
        
        // When
        ConnectionStatus status = youTubeAdapter.getConnectionStatus();
        
        // Then
        assertEquals(ConnectionStatus.DISCONNECTED, status);
    }
    
    @Test
    void should_return_circuit_open_when_circuit_breaker_forced_open() {
        // Given
        when(circuitBreakerRegistry.circuitBreaker("youtube-api")).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.FORCED_OPEN);
        
        // When
        ConnectionStatus status = youTubeAdapter.getConnectionStatus();
        
        // Then
        assertEquals(ConnectionStatus.CIRCUIT_OPEN, status);
    }
    
    @Test
    void should_return_youtube_platform_type() {
        // When
        PlatformType platformType = youTubeAdapter.getPlatformType();
        
        // Then
        assertEquals(PlatformType.YOUTUBE, platformType);
    }
    
    @Test
    void should_return_remaining_quota_when_rate_limiter_has_permissions() {
        // Given
        when(rateLimiterRegistry.rateLimiter("youtube-api")).thenReturn(rateLimiter);
        when(rateLimiter.getMetrics()).thenReturn(rateLimiterMetrics);
        int availablePermissions = 8500;
        when(rateLimiterMetrics.getAvailablePermissions()).thenReturn(availablePermissions);
        
        // When
        RateLimitInfo rateLimitInfo = youTubeAdapter.getRemainingQuota();
        
        // Then
        assertEquals(availablePermissions, rateLimitInfo.remainingCalls());
        assertEquals(10000, rateLimitInfo.totalLimit());
        assertEquals(PlatformType.YOUTUBE, rateLimitInfo.platformType());
        
        // Verify reset time is next midnight UTC
        LocalDateTime expectedReset = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay();
        assertEquals(expectedReset, rateLimitInfo.resetAt());
    }
    
    @Test
    void should_return_next_reset_time_as_midnight_utc() {
        // When
        LocalDateTime resetTime = youTubeAdapter.getNextResetTime();
        
        // Then
        LocalDateTime expectedReset = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay();
        assertEquals(expectedReset, resetTime);
    }
    
    @Test
    void should_throw_connection_exception_when_no_existing_connection_to_validate() {
        // Given
        CreatorProfile creator = new CreatorProfile();
        creator.setId("creator-123");
        PlatformCredentials creds = new PlatformCredentials(PlatformType.YOUTUBE, "auth-code", "redirect-uri");
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                "creator-123", PlatformType.YOUTUBE)).thenReturn(Optional.empty());
        
        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () -> {
            youTubeAdapter.connect(creator, creds);
        });
        
        assertEquals(PlatformType.YOUTUBE, exception.getPlatformType());
        assertTrue(exception.getMessage().contains("No existing YouTube connection found to validate"));
    }
    
    @Test
    void should_throw_connection_exception_when_connection_has_no_valid_token() {
        // Given
        CreatorProfile creator = new CreatorProfile();
        creator.setId("creator-123");
        PlatformCredentials creds = new PlatformCredentials(PlatformType.YOUTUBE, "auth-code", "redirect-uri");
        
        PlatformConnection connection = new PlatformConnection();
        connection.setAccessTokenEncrypted(null); // No token
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                "creator-123", PlatformType.YOUTUBE)).thenReturn(Optional.of(connection));
        
        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () -> {
            youTubeAdapter.connect(creator, creds);
        });
        
        assertEquals(PlatformType.YOUTUBE, exception.getPlatformType());
        assertTrue(exception.getMessage().contains("no valid access token"));
    }
    
    @Test
    void should_return_empty_metrics_when_no_connection_found() {
        // Given
        String platformUserId = "channel-123";
        when(platformConnectionRepository.findAll()).thenReturn(List.of());
        
        // When
        Optional<ContentMetrics> result = youTubeAdapter.fetchMetrics(platformUserId);
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void should_return_empty_revenue_data_when_no_connection_found() {
        // Given
        String platformUserId = "channel-123";
        DateRange range = new DateRange(LocalDate.now().minusDays(30), LocalDate.now());
        when(platformConnectionRepository.findAll()).thenReturn(List.of());
        
        // When
        Optional<RevenueData> result = youTubeAdapter.getRevenueData(platformUserId, range);
        
        // Then
        assertTrue(result.isEmpty());
    }
    
    @Test
    void should_throw_platform_api_exception_when_circuit_breaker_fallback_triggered() {
        // Test would normally require complex circuit breaker state manipulation
        // For now, verify the fallback methods throw correct exceptions
        
        // When & Then - fetchMetrics fallback
        PlatformApiException exception1 = assertThrows(PlatformApiException.class, () -> {
            youTubeAdapter.fetchMetricsFallback("channel-123", new RuntimeException("Circuit breaker open"));
        });
        assertEquals(PlatformType.YOUTUBE, exception1.getPlatformType());
        assertEquals(503, exception1.getPlatformStatusCode());
        assertEquals("CIRCUIT_OPEN", exception1.getPlatformErrorCode());
        assertTrue(exception1.isRetryable());
        
        // When & Then - getRevenueData fallback
        DateRange range = new DateRange(LocalDate.now().minusDays(30), LocalDate.now());
        PlatformApiException exception2 = assertThrows(PlatformApiException.class, () -> {
            youTubeAdapter.getRevenueDataFallback("channel-123", range, new RuntimeException("Circuit breaker open"));
        });
        assertEquals(PlatformType.YOUTUBE, exception2.getPlatformType());
        assertEquals(503, exception2.getPlatformStatusCode());
        assertEquals("CIRCUIT_OPEN", exception2.getPlatformErrorCode());
        assertTrue(exception2.isRetryable());
    }
}