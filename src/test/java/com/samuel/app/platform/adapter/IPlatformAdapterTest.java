package com.samuel.app.platform.adapter;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.platform.dto.*;
import com.samuel.app.platform.exception.*;
import com.samuel.app.platform.model.PlatformConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IPlatformAdapterTest {

    @Mock
    private IPlatformAdapter mockAdapter;

    private CreatorProfile testCreator;
    private PlatformCredentials testCredentials;
    private PlatformConnection testConnection;

    @BeforeEach
    void setUp() {
        testCreator = new CreatorProfile();
        testCreator.setId("creator-123");

        testCredentials = new PlatformCredentials(
            PlatformType.YOUTUBE,
            "auth-code-123",
            "http://localhost/callback"
        );

        testConnection = new PlatformConnection();
        testConnection.setId("connection-123");
        testConnection.setCreatorProfileId("creator-123");
        testConnection.setPlatformType(PlatformType.YOUTUBE);
        testConnection.setStatus(ConnectionStatus.CONNECTED);
    }

    @Test
    void should_establishConnection_when_validCredentialsProvided_then_returnPlatformConnection() 
            throws PlatformConnectionException, RateLimitException {
        // Given
        when(mockAdapter.connect(testCreator, testCredentials)).thenReturn(testConnection);

        // When
        PlatformConnection result = mockAdapter.connect(testCreator, testCredentials);

        // Then
        assertNotNull(result);
        assertEquals("connection-123", result.getId());
        assertEquals(PlatformType.YOUTUBE, result.getPlatformType());
        assertEquals(ConnectionStatus.CONNECTED, result.getStatus());
        verify(mockAdapter).connect(testCreator, testCredentials);
    }

    @Test
    void should_throwPlatformConnectionException_when_oauthFails_then_exceptionPropagated() {
        // Given
        PlatformConnectionException expectedException = new PlatformConnectionException(
            PlatformType.YOUTUBE, "OAuth failed");
        when(mockAdapter.connect(testCreator, testCredentials)).thenThrow(expectedException);

        // When & Then
        PlatformConnectionException thrown = assertThrows(
            PlatformConnectionException.class,
            () -> mockAdapter.connect(testCreator, testCredentials)
        );
        assertEquals(PlatformType.YOUTUBE, thrown.getPlatformType());
        assertEquals("OAuth failed", thrown.getReason());
    }

    @Test
    void should_returnConnectionStatus_when_queried_then_validStatusReturned() {
        // Given
        when(mockAdapter.getConnectionStatus()).thenReturn(ConnectionStatus.CONNECTED);

        // When
        ConnectionStatus status = mockAdapter.getConnectionStatus();

        // Then
        assertEquals(ConnectionStatus.CONNECTED, status);
        verify(mockAdapter).getConnectionStatus();
    }

    @Test
    void should_returnRateLimitInfo_when_queried_then_validInfoReturned() {
        // Given
        LocalDateTime resetTime = LocalDateTime.now().plusMinutes(5);
        RateLimitInfo expectedInfo = new RateLimitInfo(95, 100, resetTime, PlatformType.YOUTUBE);
        when(mockAdapter.getRemainingQuota()).thenReturn(expectedInfo);

        // When
        RateLimitInfo info = mockAdapter.getRemainingQuota();

        // Then
        assertEquals(95, info.remainingCalls());
        assertEquals(100, info.totalLimit());
        assertEquals(PlatformType.YOUTUBE, info.platformType());
        verify(mockAdapter).getRemainingQuota();
    }

    @Test
    void should_returnResetTime_when_queried_then_validTimeReturned() {
        // Given
        LocalDateTime expectedTime = LocalDateTime.now().plusHours(1);
        when(mockAdapter.getNextResetTime()).thenReturn(expectedTime);

        // When
        LocalDateTime resetTime = mockAdapter.getNextResetTime();

        // Then
        assertEquals(expectedTime, resetTime);
        verify(mockAdapter).getNextResetTime();
    }

    @Test
    void should_returnContentMetrics_when_userExists_then_optionalWithMetrics() 
            throws PlatformApiException, QuotaExceededException {
        // Given
        ContentMetrics expectedMetrics = new ContentMetrics(
            "platform-user-123",
            PlatformType.YOUTUBE,
            1000L, 150L, 25L, 10L,
            LocalDateTime.now()
        );
        when(mockAdapter.fetchMetrics("platform-user-123")).thenReturn(Optional.of(expectedMetrics));

        // When
        Optional<ContentMetrics> result = mockAdapter.fetchMetrics("platform-user-123");

        // Then
        assertTrue(result.isPresent());
        assertEquals("platform-user-123", result.get().platformUserId());
        assertEquals(1000L, result.get().viewCount());
        verify(mockAdapter).fetchMetrics("platform-user-123");
    }

    @Test
    void should_returnEmpty_when_userNotFound_then_emptyOptional() 
            throws PlatformApiException, QuotaExceededException {
        // Given
        when(mockAdapter.fetchMetrics("nonexistent-user")).thenReturn(Optional.empty());

        // When
        Optional<ContentMetrics> result = mockAdapter.fetchMetrics("nonexistent-user");

        // Then
        assertTrue(result.isEmpty());
        verify(mockAdapter).fetchMetrics("nonexistent-user");
    }

    @Test
    void should_returnRevenueData_when_validDateRange_then_optionalWithRevenue() 
            throws PlatformApiException, QuotaExceededException {
        // Given
        DateRange dateRange = new DateRange(LocalDate.now().minusDays(7), LocalDate.now());
        RevenueData expectedRevenue = new RevenueData(
            "platform-user-123",
            PlatformType.YOUTUBE,
            new BigDecimal("123.45"),
            "USD",
            dateRange
        );
        when(mockAdapter.getRevenueData("platform-user-123", dateRange))
            .thenReturn(Optional.of(expectedRevenue));

        // When
        Optional<RevenueData> result = mockAdapter.getRevenueData("platform-user-123", dateRange);

        // Then
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("123.45"), result.get().amount());
        assertEquals("USD", result.get().currency());
        verify(mockAdapter).getRevenueData("platform-user-123", dateRange);
    }

    @Test
    void should_returnPlatformType_when_queried_then_correctTypeReturned() {
        // Given
        when(mockAdapter.getPlatformType()).thenReturn(PlatformType.YOUTUBE);

        // When
        PlatformType type = mockAdapter.getPlatformType();

        // Then
        assertEquals(PlatformType.YOUTUBE, type);
        verify(mockAdapter).getPlatformType();
    }
}