package com.samuel.app.platform.model;

import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PlatformConnectionTest {

    @Test
    void should_createEntity_when_usingNoArgConstructor_then_fieldsAreNull() {
        // When
        PlatformConnection connection = new PlatformConnection();

        // Then
        assertNotNull(connection);
        assertNull(connection.getId());
        assertNull(connection.getCreatorProfileId());
        assertNull(connection.getPlatformType());
        assertNull(connection.getStatus());
        assertNull(connection.getPlatformUserId());
        assertNull(connection.getCreatedAt());
        assertNull(connection.getUpdatedAt());
    }

    @Test
    void should_mapAllFields_when_settersUsed_then_gettersReturnCorrectValues() {
        // Given
        PlatformConnection connection = new PlatformConnection();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryTime = now.plusHours(1);

        // When
        connection.setId("connection-123");
        connection.setCreatorProfileId("creator-456");
        connection.setPlatformType(PlatformType.YOUTUBE);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformUserId("youtube-user-789");
        connection.setPlatformName("Creator Channel");
        connection.setFollowerCount(5000L);
        connection.setAccessTokenEncrypted("encrypted-access-token");
        connection.setRefreshTokenEncrypted("encrypted-refresh-token");
        connection.setTokenExpiresAt(expiryTime);
        connection.setLastSyncAt(now);
        connection.setCreatedAt(now);
        connection.setUpdatedAt(now);

        // Then
        assertEquals("connection-123", connection.getId());
        assertEquals("creator-456", connection.getCreatorProfileId());
        assertEquals(PlatformType.YOUTUBE, connection.getPlatformType());
        assertEquals(ConnectionStatus.CONNECTED, connection.getStatus());
        assertEquals("youtube-user-789", connection.getPlatformUserId());
        assertEquals("Creator Channel", connection.getPlatformName());
        assertEquals(5000L, connection.getFollowerCount());
        assertEquals("encrypted-access-token", connection.getAccessTokenEncrypted());
        assertEquals("encrypted-refresh-token", connection.getRefreshTokenEncrypted());
        assertEquals(expiryTime, connection.getTokenExpiresAt());
        assertEquals(now, connection.getLastSyncAt());
        assertEquals(now, connection.getCreatedAt());
        assertEquals(now, connection.getUpdatedAt());
    }

    @Test
    void should_handleEnumValues_when_settingPlatformType_then_correctEnumStored() {
        // Given
        PlatformConnection connection = new PlatformConnection();

        // Test all platform types
        for (PlatformType type : PlatformType.values()) {
            // When
            connection.setPlatformType(type);

            // Then
            assertEquals(type, connection.getPlatformType());
        }
    }

    @Test
    void should_handleEnumValues_when_settingConnectionStatus_then_correctEnumStored() {
        // Given
        PlatformConnection connection = new PlatformConnection();

        // Test all connection statuses
        for (ConnectionStatus status : ConnectionStatus.values()) {
            // When
            connection.setStatus(status);

            // Then
            assertEquals(status, connection.getStatus());
        }
    }

    @Test
    void should_allowNullValues_when_settingOptionalFields_then_nullsAccepted() {
        // Given
        PlatformConnection connection = new PlatformConnection();

        // When - setting optional fields to null
        connection.setPlatformUserId(null);
        connection.setPlatformName(null);
        connection.setFollowerCount(null);
        connection.setAccessTokenEncrypted(null);
        connection.setRefreshTokenEncrypted(null);
        connection.setTokenExpiresAt(null);
        connection.setLastSyncAt(null);

        // Then - nulls should be preserved
        assertNull(connection.getPlatformUserId());
        assertNull(connection.getPlatformName());
        assertNull(connection.getFollowerCount());
        assertNull(connection.getAccessTokenEncrypted());
        assertNull(connection.getRefreshTokenEncrypted());
        assertNull(connection.getTokenExpiresAt());
        assertNull(connection.getLastSyncAt());
    }

    @Test
    void should_handleLongFollowerCount_when_settingLargeValues_then_correctValueStored() {
        // Given
        PlatformConnection connection = new PlatformConnection();
        Long largeFollowerCount = 1_000_000L;

        // When
        connection.setFollowerCount(largeFollowerCount);

        // Then
        assertEquals(largeFollowerCount, connection.getFollowerCount());
    }
}