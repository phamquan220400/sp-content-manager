package com.samuel.app.platform.service;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.exceptions.ResourceNotFoundException;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.config.YouTubeProperties;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
import com.samuel.app.platform.dto.YouTubeAuthUrlResponse;
import com.samuel.app.platform.dto.YouTubeChannelResponse;
import com.samuel.app.platform.dto.YouTubeTokenResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for YouTubeConnectionService.
 * Tests OAuth flow, connection management, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class YouTubeConnectionServiceTest {
    
    @Mock
    private YouTubeProperties youtubeProperties;
    
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
    
    private YouTubeConnectionService youTubeConnectionService;
    
    @BeforeEach
    void setUp() {

        youTubeConnectionService = new YouTubeConnectionService(
                youtubeProperties,
                stringRedisTemplate,
                restTemplate,
                platformConnectionRepository,
                tokenEncryptionService,
                creatorProfileRepository
        );
    }
    
    @Test
    void should_generate_authorization_url_when_valid_user_id_then_return_url_with_state() {
        // Given
        when(youtubeProperties.getClientId()).thenReturn("test-client-id");
        when(youtubeProperties.getRedirectUri()).thenReturn("http://localhost:8080/platform/youtube/callback");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        String userId = "user-123";
        
        // When
        YouTubeAuthUrlResponse response = youTubeConnectionService.getAuthorizationUrl(userId);
        
        // Then
        assertNotNull(response);
        assertNotNull(response.authorizationUrl());
        assertTrue(response.authorizationUrl().contains("client_id=test-client-id"));
        assertTrue(response.authorizationUrl().contains("redirect_uri=" + youtubeProperties.getRedirectUri()));
        assertTrue(response.authorizationUrl().contains("scope="));
        assertTrue(response.authorizationUrl().contains("state="));
        
        // Verify Redis state storage
        verify(valueOperations).set(startsWith("oauth:yt:state:"), eq(userId), eq(Duration.ofMinutes(10)));
    }
    
    @Test
    void should_handle_callback_when_valid_state_and_code_then_create_connection() {
        // Given
        String code = "auth-code-123";
        String state = "state-456";
        String userId = "user-789";
        
        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId("creator-profile-id");
        
        YouTubeTokenResponse tokenResponse = new YouTubeTokenResponse(
                "access-token", "refresh-token", 3600, "Bearer"
        );
        
        YouTubeChannelResponse.Statistics statistics = new YouTubeChannelResponse.Statistics(1000000L);
        YouTubeChannelResponse.Snippet snippet = new YouTubeChannelResponse.Snippet("Test Channel");
        YouTubeChannelResponse.Item item = new YouTubeChannelResponse.Item("channel-123", snippet, statistics);
        YouTubeChannelResponse channelResponse = new YouTubeChannelResponse(List.of(item));
        
        PlatformConnection savedConnection = new PlatformConnection();
        savedConnection.setId("connection-id");
        savedConnection.setPlatformType(PlatformType.YOUTUBE);
        savedConnection.setStatus(ConnectionStatus.CONNECTED);
        savedConnection.setPlatformUserId("channel-123");
        savedConnection.setPlatformName("Test Channel");
        savedConnection.setFollowerCount(1000000L);
        
        // Mock Redis state validation
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:yt:state:" + state)).thenReturn(userId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        when(restTemplate.postForObject(anyString(), any(), eq(YouTubeTokenResponse.class)))
                .thenReturn(tokenResponse);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(YouTubeChannelResponse.class)))
                .thenReturn(ResponseEntity.ok(channelResponse));
        when(tokenEncryptionService.encrypt("access-token")).thenReturn("encrypted-access-token");
        when(tokenEncryptionService.encrypt("refresh-token")).thenReturn("encrypted-refresh-token");
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                "creator-profile-id", PlatformType.YOUTUBE)).thenReturn(Optional.empty());
        when(platformConnectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);
        
        // When
        PlatformConnectionResponse response = youTubeConnectionService.handleCallback(code, state);
        
        // Then
        assertNotNull(response);
        assertEquals(PlatformType.YOUTUBE.name(), response.platformType());
        assertEquals(ConnectionStatus.CONNECTED.name(), response.status());
        assertEquals("channel-123", response.platformUserId());
        assertEquals("Test Channel", response.platformName());
        assertEquals(1000000L, response.followerCount());
        
        // Verify Redis state deletion
        verify(stringRedisTemplate).delete("oauth:yt:state:" + state);
        
        // Verify connection save
        verify(platformConnectionRepository).save(any(PlatformConnection.class));
    }
    
    @Test
    void should_throw_when_invalid_state_in_callback() {
        // Given
        String code = "auth-code-123";
        String invalidState = "invalid-state";
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:yt:state:" + invalidState)).thenReturn(null);
        
        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () -> {
            youTubeConnectionService.handleCallback(code, invalidState);
        });
        
        assertEquals(PlatformType.YOUTUBE, exception.getPlatformType());
        assertTrue(exception.getMessage().contains("Invalid or expired OAuth state"));
    }
    
    @Test
    void should_throw_when_expired_state_in_callback() {
        // Given
        String code = "auth-code-123";
        String expiredState = "expired-state";
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:yt:state:" + expiredState)).thenReturn(null); // Redis returns null for expired
        
        // When & Then
        PlatformConnectionException exception = assertThrows(PlatformConnectionException.class, () -> {
            youTubeConnectionService.handleCallback(code, expiredState);
        });
        
        assertEquals(PlatformType.YOUTUBE, exception.getPlatformType());
    }
    
    @Test
    void should_return_connected_status_when_connection_exists() {
        // Given
        String creatorProfileId = "creator-123";
        
        PlatformConnection connection = new PlatformConnection();
        connection.setPlatformType(PlatformType.YOUTUBE);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformUserId("channel-456");
        connection.setPlatformName("My Channel");
        connection.setFollowerCount(500000L);
        connection.setLastSyncAt(LocalDateTime.now());
        connection.setCreatedAt(LocalDateTime.now());
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.YOUTUBE)).thenReturn(Optional.of(connection));
        
        // When
        PlatformConnectionResponse response = youTubeConnectionService.getConnectionStatus(creatorProfileId);
        
        // Then
        assertEquals(PlatformType.YOUTUBE.name(), response.platformType());
        assertEquals(ConnectionStatus.CONNECTED.name(), response.status());
        assertEquals("channel-456", response.platformUserId());
        assertEquals("My Channel", response.platformName());
        assertEquals(500000L, response.followerCount());
    }
    
    @Test
    void should_return_disconnected_status_when_connection_not_found() {
        // Given
        String creatorProfileId = "creator-123";
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.YOUTUBE)).thenReturn(Optional.empty());
        
        // When
        PlatformConnectionResponse response = youTubeConnectionService.getConnectionStatus(creatorProfileId);
        
        // Then
        assertEquals(PlatformType.YOUTUBE.name(), response.platformType());
        assertEquals(ConnectionStatus.DISCONNECTED.name(), response.status());
        assertNull(response.platformUserId());
        assertNull(response.platformName());
        assertNull(response.followerCount());
    }
    
    @Test
    void should_disconnect_youtube_when_connection_exists() {
        // Given
        String creatorProfileId = "creator-123";
        
        PlatformConnection connection = new PlatformConnection();
        connection.setId("connection-id");
        connection.setPlatformType(PlatformType.YOUTUBE);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setAccessTokenEncrypted("encrypted-access");
        connection.setRefreshTokenEncrypted("encrypted-refresh");
        
        PlatformConnection disconnectedConnection = new PlatformConnection();
        disconnectedConnection.setId("connection-id");
        disconnectedConnection.setPlatformType(PlatformType.YOUTUBE);
        disconnectedConnection.setStatus(ConnectionStatus.DISCONNECTED);
        disconnectedConnection.setAccessTokenEncrypted(null);
        disconnectedConnection.setRefreshTokenEncrypted(null);
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.YOUTUBE)).thenReturn(Optional.of(connection));
        when(platformConnectionRepository.save(connection)).thenReturn(disconnectedConnection);
        
        // When
        PlatformConnectionResponse response = youTubeConnectionService.disconnectYouTube(creatorProfileId);
        
        // Then
        assertEquals(ConnectionStatus.DISCONNECTED.name(), response.status());
        
        // Verify connection was updated
        assertEquals(ConnectionStatus.DISCONNECTED, connection.getStatus());
        assertNull(connection.getAccessTokenEncrypted());
        assertNull(connection.getRefreshTokenEncrypted());
        assertNull(connection.getTokenExpiresAt());
        
        verify(platformConnectionRepository).save(connection);
    }
    
    @Test
    void should_throw_when_disconnect_youtube_connection_not_found() {
        // Given
        String creatorProfileId = "creator-123";
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(
                creatorProfileId, PlatformType.YOUTUBE)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> {
            youTubeConnectionService.disconnectYouTube(creatorProfileId);
        });
    }
}