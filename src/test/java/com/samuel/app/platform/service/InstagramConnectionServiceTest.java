package com.samuel.app.platform.service;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.exceptions.ResourceNotFoundException;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.config.InstagramProperties;
import com.samuel.app.platform.dto.*;
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
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InstagramConnectionService.
 * Tests OAuth flow, connection management, and error handling.
 * Follows TikTokConnectionServiceTest pattern.
 */
@ExtendWith(MockitoExtension.class)
class InstagramConnectionServiceTest {

    @Mock
    private InstagramProperties instagramProperties;

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

    private InstagramConnectionService instagramConnectionService;

    @BeforeEach
    void setUp() {
        lenient().when(instagramProperties.getClientId()).thenReturn("test-client-id");
        lenient().when(instagramProperties.getClientSecret()).thenReturn("test-secret");
        lenient().when(instagramProperties.getRedirectUri()).thenReturn("http://localhost/instagram/callback");

        instagramConnectionService = new InstagramConnectionService(
                instagramProperties,
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
        InstagramAuthUrlResponse response = instagramConnectionService.getAuthorizationUrl(userId);

        // Then
        assertNotNull(response);
        assertNotNull(response.authorizationUrl());
        assertTrue(response.authorizationUrl().contains("https://www.facebook.com/v18.0/dialog/oauth"));
        assertTrue(response.authorizationUrl().contains("state="));

        // Verify Redis state storage with 10-minute TTL
        verify(valueOperations).set(startsWith("oauth:ig:state:"), eq(userId), eq(Duration.ofMinutes(10)));
    }

    @Test
    void should_build_url_with_facebook_dialog_oauth_base() {
        // Given
        String userId = "user-456";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        InstagramAuthUrlResponse response = instagramConnectionService.getAuthorizationUrl(userId);

        // Then
        assertTrue(response.authorizationUrl().contains("https://www.facebook.com/v18.0/dialog/oauth"));
        assertTrue(response.authorizationUrl().contains("client_id=test-client-id"));
    }

    @Test
    void should_build_url_with_instagram_scopes() {
        // Given
        String userId = "user-789";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        InstagramAuthUrlResponse response = instagramConnectionService.getAuthorizationUrl(userId);

        // Then
        assertTrue(response.authorizationUrl().contains("scope=instagram_basic,pages_read_engagement,pages_show_list"));
    }

    // ────────────────────────────────────────────────────────────
    // handleCallback
    // ────────────────────────────────────────────────────────────

    @Test
    void should_connect_instagram_when_valid_callback_then_connection_saved_as_connected() {
        // Given
        String code = "auth-code-123";
        String state = "state-456";
        String userId = "user-789";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId("creator-profile-id");

        InstagramTokenResponse shortLivedToken = new InstagramTokenResponse("short-token", "Bearer", null);
        InstagramTokenResponse longLivedToken = new InstagramTokenResponse("long-token", "Bearer", 5184000L);

        MetaPageResponse.Page page = new MetaPageResponse.Page("page-123", "Test Page", "page-token");
        MetaPageResponse pagesResponse = new MetaPageResponse(List.of(page));

        MetaIgAccountResponse.IgAccount igAccount = new MetaIgAccountResponse.IgAccount("ig-user-123");
        MetaIgAccountResponse igAccountResponse = new MetaIgAccountResponse(igAccount);

        InstagramUserResponse userResponse = new InstagramUserResponse("ig-user-123", "testuser", 10000L);

        PlatformConnection savedConnection = new PlatformConnection();
        savedConnection.setId("connection-id");
        savedConnection.setPlatformType(PlatformType.INSTAGRAM);
        savedConnection.setStatus(ConnectionStatus.CONNECTED);
        savedConnection.setPlatformUserId("ig-user-123");
        savedConnection.setPlatformName("testuser");
        savedConnection.setFollowerCount(10000L);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:ig:state:" + state)).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        
        // Mock the two-step token exchange
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(InstagramTokenResponse.class)))
                .thenReturn(shortLivedToken);
        when(restTemplate.getForObject(contains("fb_exchange_token"), eq(InstagramTokenResponse.class)))
                .thenReturn(longLivedToken);
        
        // Mock the multi-step user info retrieval
        when(restTemplate.getForObject(contains("/me/accounts"), eq(MetaPageResponse.class)))
                .thenReturn(pagesResponse);
        when(restTemplate.getForObject(contains("instagram_business_account"), eq(MetaIgAccountResponse.class)))
                .thenReturn(igAccountResponse);
        when(restTemplate.getForObject(contains("ig-user-123"), eq(InstagramUserResponse.class)))
                .thenReturn(userResponse);

        when(tokenEncryptionService.encrypt("long-token")).thenReturn("encrypted-long-token");
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                "creator-profile-id", PlatformType.INSTAGRAM)).thenReturn(Optional.empty());
        when(platformConnectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        // When
        PlatformConnectionResponse response = instagramConnectionService.handleCallback(code, state);

        // Then
        assertNotNull(response);
        assertEquals(PlatformType.INSTAGRAM.name(), response.platformType());
        assertEquals(ConnectionStatus.CONNECTED.name(), response.status());
        assertEquals("ig-user-123", response.platformUserId());
        assertEquals("testuser", response.platformName());
        assertEquals(10000L, response.followerCount());

        // Verify Redis state deletion (replay prevention)
        verify(stringRedisTemplate).delete("oauth:ig:state:" + state);
        verify(platformConnectionRepository).save(any(PlatformConnection.class));
    }

    @Test
    void should_throw_when_oauth_state_is_invalid() {
        // Given
        String code = "auth-code";
        String invalidState = "bad-state";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:ig:state:" + invalidState)).thenReturn(null);

        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () ->
                instagramConnectionService.handleCallback(code, invalidState));

        assertEquals(PlatformType.INSTAGRAM, exception.getPlatformType());
        assertTrue(exception.getMessage().contains("Invalid or expired OAuth state"));
    }

    @Test
    void should_throw_platform_connection_exception_when_no_facebook_pages_found() {
        // Given
        String code = "auth-code";
        String state = "state-123";
        String userId = "user-456";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId("creator-profile-id");

        InstagramTokenResponse shortLivedToken = new InstagramTokenResponse("short-token", "Bearer", null);
        InstagramTokenResponse longLivedToken = new InstagramTokenResponse("long-token", "Bearer", 5184000L);
        MetaPageResponse emptyPagesResponse = new MetaPageResponse(List.of()); // Empty pages list

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:ig:state:" + state)).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(InstagramTokenResponse.class)))
                .thenReturn(shortLivedToken);
        when(restTemplate.getForObject(contains("fb_exchange_token"), eq(InstagramTokenResponse.class)))
                .thenReturn(longLivedToken);
        when(restTemplate.getForObject(contains("/me/accounts"), eq(MetaPageResponse.class)))
                .thenReturn(emptyPagesResponse);

        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () ->
                instagramConnectionService.handleCallback(code, state));

        assertEquals(PlatformType.INSTAGRAM, exception.getPlatformType());
        assertTrue(exception.getMessage().contains("No Facebook Pages found"));
    }

    @Test
    void should_throw_platform_connection_exception_when_no_instagram_business_account_linked() {
        // Given
        String code = "auth-code";
        String state = "state-123";
        String userId = "user-456";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId("creator-profile-id");

        InstagramTokenResponse shortLivedToken = new InstagramTokenResponse("short-token", "Bearer", null);
        InstagramTokenResponse longLivedToken = new InstagramTokenResponse("long-token", "Bearer", 5184000L);

        MetaPageResponse.Page page = new MetaPageResponse.Page("page-123", "Test Page", "page-token");
        MetaPageResponse pagesResponse = new MetaPageResponse(List.of(page));
        
        // No Instagram Business Account linked to page
        MetaIgAccountResponse noIgAccountResponse = new MetaIgAccountResponse(null);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:ig:state:" + state)).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(InstagramTokenResponse.class)))
                .thenReturn(shortLivedToken);
        when(restTemplate.getForObject(contains("fb_exchange_token"), eq(InstagramTokenResponse.class)))
                .thenReturn(longLivedToken);
        when(restTemplate.getForObject(contains("/me/accounts"), eq(MetaPageResponse.class)))
                .thenReturn(pagesResponse);
        when(restTemplate.getForObject(contains("instagram_business_account"), eq(MetaIgAccountResponse.class)))
                .thenReturn(noIgAccountResponse);

        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () ->
                instagramConnectionService.handleCallback(code, state));

        assertEquals(PlatformType.INSTAGRAM, exception.getPlatformType());
        assertTrue(exception.getMessage().contains("No Instagram Business Account found"));
    }

    // ────────────────────────────────────────────────────────────
    // getConnectionStatus
    // ────────────────────────────────────────────────────────────

    @Test
    void should_return_connected_status_when_connection_exists() {
        // Given
        String creatorProfileId = "creator-123";
        PlatformConnection connection = new PlatformConnection();
        connection.setPlatformType(PlatformType.INSTAGRAM);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformUserId("ig-user-123");
        connection.setPlatformName("testuser");
        connection.setFollowerCount(5000L);

        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.INSTAGRAM)).thenReturn(Optional.of(connection));

        // When
        PlatformConnectionResponse response = instagramConnectionService.getConnectionStatus(creatorProfileId);

        // Then
        assertNotNull(response);
        assertEquals(PlatformType.INSTAGRAM.name(), response.platformType());
        assertEquals(ConnectionStatus.CONNECTED.name(), response.status());
        assertEquals("ig-user-123", response.platformUserId());
        assertEquals("testuser", response.platformName());
        assertEquals(5000L, response.followerCount());
    }

    @Test
    void should_return_disconnected_when_no_connection_found() {
        // Given
        String creatorProfileId = "creator-456";
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.INSTAGRAM)).thenReturn(Optional.empty());

        // When
        PlatformConnectionResponse response = instagramConnectionService.getConnectionStatus(creatorProfileId);

        // Then
        assertNotNull(response);
        assertEquals(PlatformType.INSTAGRAM.name(), response.platformType());
        assertEquals(ConnectionStatus.DISCONNECTED.name(), response.status());
        assertNull(response.platformUserId());
        assertNull(response.platformName());
        assertNull(response.followerCount());
    }

    // ────────────────────────────────────────────────────────────
    // disconnectInstagram
    // ────────────────────────────────────────────────────────────

    @Test
    void should_disconnect_when_connection_exists() {
        // Given
        String creatorProfileId = "creator-123";
        PlatformConnection connection = new PlatformConnection();
        connection.setId("connection-id");
        connection.setPlatformType(PlatformType.INSTAGRAM);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setAccessTokenEncrypted("encrypted-token");

        PlatformConnection updatedConnection = new PlatformConnection();
        updatedConnection.setId("connection-id");
        updatedConnection.setPlatformType(PlatformType.INSTAGRAM);
        updatedConnection.setStatus(ConnectionStatus.DISCONNECTED);
        updatedConnection.setAccessTokenEncrypted(null);

        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.INSTAGRAM)).thenReturn(Optional.of(connection));
        when(platformConnectionRepository.save(connection)).thenReturn(updatedConnection);

        // When
        PlatformConnectionResponse response = instagramConnectionService.disconnectInstagram(creatorProfileId);

        // Then
        assertNotNull(response);
        assertEquals(PlatformType.INSTAGRAM.name(), response.platformType());
        assertEquals(ConnectionStatus.DISCONNECTED.name(), response.status());

        verify(platformConnectionRepository).save(connection);
        // Verify connection data was cleared
        assertEquals(ConnectionStatus.DISCONNECTED, connection.getStatus());
        assertNull(connection.getAccessTokenEncrypted());
        assertNull(connection.getRefreshTokenEncrypted());
        assertNull(connection.getTokenExpiresAt());
    }

    @Test
    void should_throw_resource_not_found_when_no_connection_to_disconnect() {
        // Given
        String creatorProfileId = "creator-nonexistent";
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.INSTAGRAM)).thenReturn(Optional.empty());

        // When & Then
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () ->
                instagramConnectionService.disconnectInstagram(creatorProfileId));

        assertTrue(exception.getMessage().contains("Instagram connection not found"));
    }

    @Test
    void should_use_60_day_expiry_when_expires_in_is_null() {
        // Given
        String code = "auth-code-123";
        String state = "state-456";
        String userId = "user-789";

        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId("creator-profile-id");

        // Long-lived token response with null expiresIn (should fallback to 60 days)
        InstagramTokenResponse shortLivedToken = new InstagramTokenResponse("short-token", "Bearer", null);
        InstagramTokenResponse longLivedTokenNullExpiry = new InstagramTokenResponse("long-token", "Bearer", null);

        MetaPageResponse.Page page = new MetaPageResponse.Page("page-123", "Test Page", "page-token");
        MetaPageResponse pagesResponse = new MetaPageResponse(List.of(page));
        MetaIgAccountResponse.IgAccount igAccount = new MetaIgAccountResponse.IgAccount("ig-user-123");
        MetaIgAccountResponse igAccountResponse = new MetaIgAccountResponse(igAccount);
        InstagramUserResponse userResponse = new InstagramUserResponse("ig-user-123", "testuser", 1000L);

        PlatformConnection savedConnection = new PlatformConnection();
        savedConnection.setPlatformType(PlatformType.INSTAGRAM);
        savedConnection.setStatus(ConnectionStatus.CONNECTED);
        savedConnection.setPlatformUserId("ig-user-123");
        savedConnection.setPlatformName("testuser");
        savedConnection.setFollowerCount(1000L);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:ig:state:" + state)).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(InstagramTokenResponse.class)))
                .thenReturn(shortLivedToken);
        when(restTemplate.getForObject(contains("fb_exchange_token"), eq(InstagramTokenResponse.class)))
                .thenReturn(longLivedTokenNullExpiry);
        when(restTemplate.getForObject(contains("/me/accounts"), eq(MetaPageResponse.class)))
                .thenReturn(pagesResponse);
        when(restTemplate.getForObject(contains("instagram_business_account"), eq(MetaIgAccountResponse.class)))
                .thenReturn(igAccountResponse);
        when(restTemplate.getForObject(contains("ig-user-123"), eq(InstagramUserResponse.class)))
                .thenReturn(userResponse);
        when(tokenEncryptionService.encrypt("long-token")).thenReturn("encrypted-token");
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                "creator-profile-id", PlatformType.INSTAGRAM)).thenReturn(Optional.empty());
        when(platformConnectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        // When — must not throw NPE
        assertDoesNotThrow(() -> instagramConnectionService.handleCallback(code, state));

        // Verify the connection was saved with ~60 day expiry (tokenExpiresAt set to now+60days)
        verify(platformConnectionRepository).save(argThat(conn ->
                conn.getTokenExpiresAt() != null &&
                conn.getTokenExpiresAt().isAfter(java.time.LocalDateTime.now().plusDays(59))
        ));
    }
}