package com.samuel.app.platform.service;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.exceptions.ResourceNotFoundException;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.config.TikTokProperties;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
import com.samuel.app.platform.dto.TikTokAuthUrlResponse;
import com.samuel.app.platform.dto.TikTokTokenResponse;
import com.samuel.app.platform.dto.TikTokUserInfoResponse;
import com.samuel.app.platform.exception.PlatformConnectionException;
import com.samuel.app.platform.model.PlatformConnection;
import com.samuel.app.platform.repository.PlatformConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TikTokConnectionService.
 * Tests OAuth flow, connection management, and error handling.
 * Mirrors YouTubeConnectionServiceTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class TikTokConnectionServiceTest {

    @Mock
    private TikTokProperties tikTokProperties;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PlatformConnectionRepository platformConnectionRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    private TikTokConnectionService tikTokConnectionService;

    @BeforeEach
    void setUp() {
        lenient().when(tikTokProperties.getClientKey()).thenReturn("test-client-key");
        lenient().when(tikTokProperties.getClientSecret()).thenReturn("test-secret");
        lenient().when(tikTokProperties.getRedirectUri()).thenReturn("http://localhost/tiktok/callback");

        tikTokConnectionService = new TikTokConnectionService(
                tikTokProperties,
                stringRedisTemplate,
                restTemplate,
                platformConnectionRepository,
                tokenEncryptionService,
                creatorProfileRepository
        );
    }

    // ────────────────────────────────────────────────────────────
    // getAuthorizationUrl
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_auth_url_when_valid_user_then_state_stored_in_redis() {
        // Given
        String userId = "user-123";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        TikTokAuthUrlResponse response = tikTokConnectionService.getAuthorizationUrl(userId);

        // Then
        assertNotNull(response);
        assertNotNull(response.authorizationUrl());
        assertTrue(response.authorizationUrl().contains("https://www.tiktok.com/v2/auth/authorize/"));
        assertTrue(response.authorizationUrl().contains("state="));

        // Verify Redis state storage with 10-minute TTL
        verify(valueOperations).set(startsWith("oauth:tt:state:"), eq(userId), eq(Duration.ofMinutes(10)));
    }

    @Test
    void should_build_url_with_client_key_param_not_client_id() {
        // Given
        String userId = "user-456";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        TikTokAuthUrlResponse response = tikTokConnectionService.getAuthorizationUrl(userId);

        // Then — TikTok uses client_key NOT client_id
        assertTrue(response.authorizationUrl().contains("client_key=test-client-key"));
        assertFalse(response.authorizationUrl().contains("client_id="));
    }

    // ────────────────────────────────────────────────────────────
    // handleCallback
    // ────────────────────────────────────────────────────────────

    @Test
    void should_connect_tiktok_when_valid_callback_then_connection_saved_as_connected() {
        // Given
        String code = "auth-code-123";
        String state = "state-456";
        String userId = "user-789";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId("creator-profile-id");

        TikTokTokenResponse tokenResponse = new TikTokTokenResponse(
                "access-token", "refresh-token", 86400L, 604800L,
                "open-id-abc", "user.info.basic", "Bearer"
        );

        TikTokUserInfoResponse.Data.User user =
                new TikTokUserInfoResponse.Data.User("open-id-abc", "Creator Name", 50000L);
        TikTokUserInfoResponse.Data data = new TikTokUserInfoResponse.Data(user);
        TikTokUserInfoResponse userInfoResponse = new TikTokUserInfoResponse(data);

        PlatformConnection savedConnection = new PlatformConnection();
        savedConnection.setId("connection-id");
        savedConnection.setPlatformType(PlatformType.TIKTOK);
        savedConnection.setStatus(ConnectionStatus.CONNECTED);
        savedConnection.setPlatformUserId("open-id-abc");
        savedConnection.setPlatformName("Creator Name");
        savedConnection.setFollowerCount(50000L);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:tt:state:" + state)).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(restTemplate.postForObject(anyString(), any(), eq(TikTokTokenResponse.class)))
                .thenReturn(tokenResponse);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(TikTokUserInfoResponse.class)))
                .thenReturn(ResponseEntity.ok(userInfoResponse));
        when(tokenEncryptionService.encrypt("access-token")).thenReturn("encrypted-access-token");
        when(tokenEncryptionService.encrypt("refresh-token")).thenReturn("encrypted-refresh-token");
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                "creator-profile-id", PlatformType.TIKTOK)).thenReturn(Optional.empty());
        when(platformConnectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        // When
        PlatformConnectionResponse response = tikTokConnectionService.handleCallback(code, state);

        // Then
        assertNotNull(response);
        assertEquals(PlatformType.TIKTOK.name(), response.platformType());
        assertEquals(ConnectionStatus.CONNECTED.name(), response.status());
        assertEquals("open-id-abc", response.platformUserId());
        assertEquals("Creator Name", response.platformName());
        assertEquals(50000L, response.followerCount());

        // Verify Redis state deletion (replay prevention)
        verify(stringRedisTemplate).delete("oauth:tt:state:" + state);
        verify(platformConnectionRepository).save(any(PlatformConnection.class));
    }

    @Test
    void should_throw_when_oauth_state_is_invalid() {
        // Given
        String code = "auth-code";
        String invalidState = "bad-state";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:tt:state:" + invalidState)).thenReturn(null);

        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () ->
                tikTokConnectionService.handleCallback(code, invalidState));

        assertEquals(PlatformType.TIKTOK, exception.getPlatformType());
        assertTrue(exception.getMessage().contains("Invalid or expired OAuth state"));
    }

    @Test
    void should_throw_when_oauth_state_is_expired_redis_returns_null() {
        // Given
        String code = "auth-code";
        String expiredState = "expired-state";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:tt:state:" + expiredState)).thenReturn(null);

        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () ->
                tikTokConnectionService.handleCallback(code, expiredState));

        assertEquals(PlatformType.TIKTOK, exception.getPlatformType());
    }

    // ────────────────────────────────────────────────────────────
    // getConnectionStatus
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_connected_status_when_connection_exists() {
        // Given
        String creatorProfileId = "creator-profile-id";
        PlatformConnection connection = new PlatformConnection();
        connection.setId("conn-id");
        connection.setPlatformType(PlatformType.TIKTOK);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformUserId("open-id-abc");
        connection.setPlatformName("Creator Name");
        connection.setFollowerCount(50000L);
        connection.setLastSyncAt(LocalDateTime.now());

        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.TIKTOK)).thenReturn(Optional.of(connection));

        // When
        PlatformConnectionResponse response = tikTokConnectionService.getConnectionStatus(creatorProfileId);

        // Then
        assertNotNull(response);
        assertEquals(PlatformType.TIKTOK.name(), response.platformType());
        assertEquals(ConnectionStatus.CONNECTED.name(), response.status());
        assertEquals("open-id-abc", response.platformUserId());
    }

    @Test
    void should_return_disconnected_when_no_connection_found() {
        // Given
        String creatorProfileId = "creator-profile-id";
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.TIKTOK)).thenReturn(Optional.empty());

        // When
        PlatformConnectionResponse response = tikTokConnectionService.getConnectionStatus(creatorProfileId);

        // Then
        assertNotNull(response);
        assertEquals(PlatformType.TIKTOK.name(), response.platformType());
        assertEquals(ConnectionStatus.DISCONNECTED.name(), response.status());
        assertNull(response.platformUserId());
        assertNull(response.platformName());
    }

    // ────────────────────────────────────────────────────────────
    // disconnectTikTok
    // ────────────────────────────────────────────────────────────

    @Test
    void should_disconnect_when_connection_exists() {
        // Given
        String creatorProfileId = "creator-profile-id";
        PlatformConnection connection = new PlatformConnection();
        connection.setId("conn-id");
        connection.setPlatformType(PlatformType.TIKTOK);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setAccessTokenEncrypted("encrypted-token");
        connection.setRefreshTokenEncrypted("encrypted-refresh");

        PlatformConnection savedConnection = new PlatformConnection();
        savedConnection.setId("conn-id");
        savedConnection.setPlatformType(PlatformType.TIKTOK);
        savedConnection.setStatus(ConnectionStatus.DISCONNECTED);

        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.TIKTOK)).thenReturn(Optional.of(connection));
        when(platformConnectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        // When
        PlatformConnectionResponse response = tikTokConnectionService.disconnectTikTok(creatorProfileId);

        // Then
        assertNotNull(response);
        assertEquals(ConnectionStatus.DISCONNECTED.name(), response.status());
        verify(platformConnectionRepository).save(argThat(conn ->
                conn.getStatus() == ConnectionStatus.DISCONNECTED &&
                conn.getAccessTokenEncrypted() == null &&
                conn.getRefreshTokenEncrypted() == null &&
                conn.getTokenExpiresAt() == null
        ));
    }

    @Test
    void should_throw_resource_not_found_when_no_connection_to_disconnect() {
        // Given
        String creatorProfileId = "creator-profile-id";
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.TIKTOK)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                tikTokConnectionService.disconnectTikTok(creatorProfileId));
    }
}
