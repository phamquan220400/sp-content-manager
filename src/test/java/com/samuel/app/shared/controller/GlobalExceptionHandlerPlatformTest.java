package com.samuel.app.shared.controller;

import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerPlatformTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void should_returnServiceUnavailable_when_platformConnectionException_then_correctStatusAndMessage() {
        // Given
        PlatformConnectionException exception = new PlatformConnectionException(
            PlatformType.YOUTUBE, "OAuth token expired");

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handlePlatformConnection(exception);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PLATFORM_CONNECTION_ERROR", response.getBody().errorCode());
        assertTrue(response.getBody().message().contains("OAuth token expired"));
        assertTrue(response.getBody().message().contains("YOUTUBE"));
    }

    @Test
    void should_returnBadGateway_when_retryablePlatformApiException_then_correctStatusAndMessage() {
        // Given
        PlatformApiException exception = new PlatformApiException(
            PlatformType.TIKTOK, 500, "INTERNAL_ERROR", true);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handlePlatformApi(exception);

        // Then
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PLATFORM_API_ERROR", response.getBody().errorCode());
        assertTrue(response.getBody().message().contains("TIKTOK"));
        assertTrue(response.getBody().message().contains("INTERNAL_ERROR"));
        assertTrue(response.getBody().message().contains("retryable: true"));
    }

    @Test
    void should_returnServiceUnavailable_when_nonRetryablePlatformApiException_then_correctStatusAndMessage() {
        // Given
        PlatformApiException exception = new PlatformApiException(
            PlatformType.INSTAGRAM, 401, "INVALID_TOKEN", false);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handlePlatformApi(exception);

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PLATFORM_API_ERROR", response.getBody().errorCode());
        assertTrue(response.getBody().message().contains("INSTAGRAM"));
        assertTrue(response.getBody().message().contains("INVALID_TOKEN"));
        assertTrue(response.getBody().message().contains("retryable: false"));
    }

    @Test
    void should_returnTooManyRequests_when_quotaExceededException_then_correctStatusAndHeaders() {
        // Given
        LocalDateTime resetTime = LocalDateTime.of(2026, 4, 28, 15, 30, 0);
        QuotaExceededException exception = new QuotaExceededException(
            PlatformType.FACEBOOK, resetTime);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleQuotaExceeded(exception);

        // Then
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("QUOTA_EXCEEDED", response.getBody().errorCode());
        
        // Verify Retry-After header is set
        assertNotNull(response.getHeaders().getFirst("Retry-After"));
        assertEquals("2026-04-28T15:30:00", response.getHeaders().getFirst("Retry-After"));
        
        assertTrue(response.getBody().message().contains("FACEBOOK"));
        assertTrue(response.getBody().message().contains("2026-04-28T15:30"));
    }

    @Test
    void should_returnTooManyRequests_when_rateLimitException_then_correctStatusAndMessage() {
        // Given
        RateLimitException exception = new RateLimitException(PlatformType.YOUTUBE);

        // When
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleRateLimit(exception);

        // Then
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().errorCode());
        assertTrue(response.getBody().message().contains("YOUTUBE"));
        assertTrue(response.getBody().message().contains("rate limiter"));
    }

    @Test
    void should_handleAllPlatformTypes_when_platformConnectionException_then_correctPlatformInMessage() {
        for (PlatformType platformType : PlatformType.values()) {
            // Given
            PlatformConnectionException exception = new PlatformConnectionException(
                platformType, "Test error");

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handlePlatformConnection(exception);

            // Then
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            assertTrue(response.getBody().message().contains(platformType.toString()));
        }
    }

    @Test
    void should_handleAllPlatformTypes_when_rateLimitException_then_correctPlatformInMessage() {
        for (PlatformType platformType : PlatformType.values()) {
            // Given
            RateLimitException exception = new RateLimitException(platformType);

            // When
            ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleRateLimit(exception);

            // Then
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertTrue(response.getBody().message().contains(platformType.toString()));
        }
    }
}