package com.samuel.app.platform.service;

import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.platform.config.FacebookProperties;
import com.samuel.app.platform.dto.FacebookAuthUrlResponse;
import com.samuel.app.platform.dto.FacebookPageDetailsResponse;
import com.samuel.app.platform.dto.InstagramTokenResponse;
import com.samuel.app.platform.dto.MetaPageResponse;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
import com.samuel.app.platform.model.PlatformConnection;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.exception.PlatformConnectionException;
import com.samuel.app.platform.repository.PlatformConnectionRepository;
import com.samuel.app.platform.service.TokenEncryptionService;
import com.samuel.app.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FacebookConnectionServiceTest {

    @Mock
    private FacebookProperties facebookProperties;
    
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
    
    @InjectMocks
    private FacebookConnectionService facebookConnectionService;

    @Test
    public void should_return_auth_url_when_valid_user_then_state_stored_in_redis() {
        // Given
        String userId = "user-123";
        String clientId = "facebook-client-id";
        String redirectUri = "http://localhost:8080/api/v1/platforms/facebook/callback";
        
        when(facebookProperties.getClientId()).thenReturn(clientId);
        when(facebookProperties.getRedirectUri()).thenReturn(redirectUri);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        FacebookAuthUrlResponse response = facebookConnectionService.getAuthorizationUrl(userId);

        // Then
        assertThat(response.authorizationUrl()).contains("https://www.facebook.com/v18.0/dialog/oauth");
        assertThat(response.authorizationUrl()).contains("client_id=" + clientId);
        assertThat(response.authorizationUrl()).contains("redirect_uri=http://localhost:8080/api/v1/platforms/facebook/callback");
        assertThat(response.authorizationUrl()).contains("response_type=code");
        assertThat(response.authorizationUrl()).contains("scope=pages_show_list,pages_read_engagement,pages_manage_posts");
        assertThat(response.authorizationUrl()).contains("state=");
        
        verify(valueOperations).set(startsWith("oauth:fb:state:"), eq(userId), eq(600L), eq(TimeUnit.SECONDS));
    }

    @Test
    public void should_build_url_with_facebook_dialog_oauth_base() {
        // Given
        String userId = "user-123";
        when(facebookProperties.getClientId()).thenReturn("test-client-id");
        when(facebookProperties.getRedirectUri()).thenReturn("http://test.com/callback");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        FacebookAuthUrlResponse response = facebookConnectionService.getAuthorizationUrl(userId);

        // Then
        assertThat(response.authorizationUrl()).startsWith("https://www.facebook.com/v18.0/dialog/oauth");
    }

    @Test
    public void should_build_url_with_pages_scopes() {
        // Given
        String userId = "user-123";
        when(facebookProperties.getClientId()).thenReturn("test-client-id");
        when(facebookProperties.getRedirectUri()).thenReturn("http://test.com/callback");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        FacebookAuthUrlResponse response = facebookConnectionService.getAuthorizationUrl(userId);

        // Then
        assertThat(response.authorizationUrl()).contains("scope=pages_show_list,pages_read_engagement,pages_manage_posts");
    }

    @Test
    public void should_connect_facebook_page_when_valid_callback_then_connection_saved_as_connected() {
        // Given
        String code = "auth-code";
        String state = "valid-state";
        String userId = "user-123";
        String creatorProfileId = "creator-123";
        
        // Mock Redis validation
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:fb:state:" + state)).thenReturn(userId);
        
        // Mock creator profile
        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        
        // Mock OAuth properties
        when(facebookProperties.getClientId()).thenReturn("client-id");
        when(facebookProperties.getClientSecret()).thenReturn("client-secret");
        when(facebookProperties.getRedirectUri()).thenReturn("redirect-uri");
        
        // Mock token exchanges
        InstagramTokenResponse shortTokenResponse = new InstagramTokenResponse("short-token", "bearer", 3600L);
        InstagramTokenResponse longTokenResponse = new InstagramTokenResponse("long-token", "bearer", 5184000L);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(InstagramTokenResponse.class)))
            .thenReturn(shortTokenResponse);
        when(restTemplate.getForObject(anyString(), eq(InstagramTokenResponse.class)))
            .thenReturn(longTokenResponse);
        
        // Mock pages response
        MetaPageResponse.Page page = new MetaPageResponse.Page("page-123", "Test Page", "page-access-token");
        MetaPageResponse pagesResponse = new MetaPageResponse(List.of(page));
        when(restTemplate.getForObject(contains("/me/accounts"), eq(MetaPageResponse.class)))
            .thenReturn(pagesResponse);
        
        // Mock page details
        FacebookPageDetailsResponse pageDetails = new FacebookPageDetailsResponse("page-123", "Test Page", 50000L, "Brand");
        when(restTemplate.getForObject(contains("/page-123"), eq(FacebookPageDetailsResponse.class)))
            .thenReturn(pageDetails);
        
        // Mock encryption
        when(tokenEncryptionService.encrypt("page-access-token")).thenReturn("encrypted-token");
        
        // Mock repository
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK))
            .thenReturn(Optional.empty());
        
        PlatformConnection savedConnection = new PlatformConnection();
        savedConnection.setStatus(ConnectionStatus.CONNECTED);
        savedConnection.setPlatformType(PlatformType.FACEBOOK);
        savedConnection.setPlatformName("Test Page");
        savedConnection.setFollowerCount(50000L);
        savedConnection.setCreatedAt(LocalDateTime.now());
        when(platformConnectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        // When
        PlatformConnectionResponse response = facebookConnectionService.handleCallback(code, state);

        // Then
        assertThat(response.status()).isEqualTo(ConnectionStatus.CONNECTED.name());
        verify(stringRedisTemplate).delete("oauth:fb:state:" + state);
        verify(platformConnectionRepository).save(argThat(conn -> 
            conn.getStatus() == ConnectionStatus.CONNECTED &&
            conn.getPlatformType() == PlatformType.FACEBOOK &&
            conn.getPlatformUserId().equals("page-123") &&
            conn.getPlatformName().equals("Test Page") &&
            conn.getFollowerCount().equals(50000L) &&
            conn.getAccessTokenEncrypted().equals("encrypted-token") &&
            conn.getRefreshTokenEncrypted() == null &&
            conn.getTokenExpiresAt() == null
        ));
    }

    @Test
    public void should_throw_when_oauth_state_is_invalid() {
        // Given
        String code = "auth-code";
        String state = "invalid-state";
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:fb:state:" + state)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> facebookConnectionService.handleCallback(code, state))
            .isInstanceOf(PlatformConnectionException.class)
            .hasMessageContaining("Invalid or expired OAuth state");
    }

    @Test
    public void should_throw_platform_connection_exception_when_no_facebook_pages_found() {
        // Given
        String code = "auth-code";
        String state = "valid-state";
        String userId = "user-123";
        
        // Mock Redis validation
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:fb:state:" + state)).thenReturn(userId);
        
        // Mock creator profile
        CreatorProfile creatorProfile = new CreatorProfile();
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        
        // Mock OAuth properties
        when(facebookProperties.getClientId()).thenReturn("client-id");
        when(facebookProperties.getClientSecret()).thenReturn("client-secret");
        when(facebookProperties.getRedirectUri()).thenReturn("redirect-uri");
        
        // Mock token exchanges
        InstagramTokenResponse shortTokenResponse = new InstagramTokenResponse("short-token", "bearer", 3600L);
        InstagramTokenResponse longTokenResponse = new InstagramTokenResponse("long-token", "bearer", 5184000L);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(InstagramTokenResponse.class)))
            .thenReturn(shortTokenResponse);
        when(restTemplate.getForObject(anyString(), eq(InstagramTokenResponse.class)))
            .thenReturn(longTokenResponse);
        
        // Mock empty pages response
        MetaPageResponse pagesResponse = new MetaPageResponse(List.of());
        when(restTemplate.getForObject(contains("/me/accounts"), eq(MetaPageResponse.class)))
            .thenReturn(pagesResponse);

        // When/Then
        assertThatThrownBy(() -> facebookConnectionService.handleCallback(code, state))
            .isInstanceOf(PlatformConnectionException.class)
            .hasMessageContaining("No Facebook Pages found. Please create a Facebook Page to connect to this platform.");
    }

    @Test
    public void should_store_page_access_token_not_user_token() {
        // Given - same setup as successful connection test
        String code = "auth-code";
        String state = "valid-state";
        String userId = "user-123";
        String creatorProfileId = "creator-123";
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:fb:state:" + state)).thenReturn(userId);
        
        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        
        when(facebookProperties.getClientId()).thenReturn("client-id");
        when(facebookProperties.getClientSecret()).thenReturn("client-secret");
        when(facebookProperties.getRedirectUri()).thenReturn("redirect-uri");
        
        InstagramTokenResponse shortTokenResponse = new InstagramTokenResponse("short-user-token", "bearer", 3600L);
        InstagramTokenResponse longTokenResponse = new InstagramTokenResponse("long-user-token", "bearer", 5184000L);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(InstagramTokenResponse.class)))
            .thenReturn(shortTokenResponse);
        when(restTemplate.getForObject(anyString(), eq(InstagramTokenResponse.class)))
            .thenReturn(longTokenResponse);
        
        MetaPageResponse.Page page = new MetaPageResponse.Page("page-123", "Test Page", "page-access-token");
        MetaPageResponse pagesResponse = new MetaPageResponse(List.of(page));
        when(restTemplate.getForObject(contains("/me/accounts"), eq(MetaPageResponse.class)))
            .thenReturn(pagesResponse);
        
        FacebookPageDetailsResponse pageDetails = new FacebookPageDetailsResponse("page-123", "Test Page", 50000L, "Brand");
        when(restTemplate.getForObject(contains("/page-123"), eq(FacebookPageDetailsResponse.class)))
            .thenReturn(pageDetails);
        
        when(tokenEncryptionService.encrypt("page-access-token")).thenReturn("encrypted-page-token");
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK))
            .thenReturn(Optional.empty());
        
        PlatformConnection savedConnection = new PlatformConnection();
        savedConnection.setStatus(ConnectionStatus.CONNECTED);
        savedConnection.setPlatformType(PlatformType.FACEBOOK);
        savedConnection.setPlatformName("Test Page");
        savedConnection.setFollowerCount(50000L);
        savedConnection.setCreatedAt(LocalDateTime.now());
        when(platformConnectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        // When
        facebookConnectionService.handleCallback(code, state);

        // Then
        verify(tokenEncryptionService).encrypt("page-access-token"); // Page token, not user token
        verify(tokenEncryptionService, never()).encrypt("long-user-token");
        verify(tokenEncryptionService, never()).encrypt("short-user-token");
    }

    @Test
    public void should_set_token_expires_at_null_for_page_token() {
        // Given - same setup as successful connection test
        String code = "auth-code";
        String state = "valid-state";
        String userId = "user-123";
        String creatorProfileId = "creator-123";
        
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("oauth:fb:state:" + state)).thenReturn(userId);
        
        CreatorProfile creatorProfile = new CreatorProfile();
        creatorProfile.setId(creatorProfileId);
        when(creatorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(creatorProfile));
        
        when(facebookProperties.getClientId()).thenReturn("client-id");
        when(facebookProperties.getClientSecret()).thenReturn("client-secret");
        when(facebookProperties.getRedirectUri()).thenReturn("redirect-uri");
        
        InstagramTokenResponse shortTokenResponse = new InstagramTokenResponse("short-token", "bearer", 3600L);
        InstagramTokenResponse longTokenResponse = new InstagramTokenResponse("long-token", "bearer", 5184000L);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(InstagramTokenResponse.class)))
            .thenReturn(shortTokenResponse);
        when(restTemplate.getForObject(anyString(), eq(InstagramTokenResponse.class)))
            .thenReturn(longTokenResponse);
        
        MetaPageResponse.Page page = new MetaPageResponse.Page("page-123", "Test Page", "page-access-token");
        MetaPageResponse pagesResponse = new MetaPageResponse(List.of(page));
        when(restTemplate.getForObject(contains("/me/accounts"), eq(MetaPageResponse.class)))
            .thenReturn(pagesResponse);
        
        FacebookPageDetailsResponse pageDetails = new FacebookPageDetailsResponse("page-123", "Test Page", 50000L, "Brand");
        when(restTemplate.getForObject(contains("/page-123"), eq(FacebookPageDetailsResponse.class)))
            .thenReturn(pageDetails);
        
        when(tokenEncryptionService.encrypt("page-access-token")).thenReturn("encrypted-token");
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK))
            .thenReturn(Optional.empty());
        
        PlatformConnection savedConnection = new PlatformConnection();
        savedConnection.setStatus(ConnectionStatus.CONNECTED);
        savedConnection.setPlatformType(PlatformType.FACEBOOK);
        savedConnection.setPlatformName("Test Page");
        savedConnection.setFollowerCount(50000L);
        savedConnection.setCreatedAt(LocalDateTime.now());
        when(platformConnectionRepository.save(any(PlatformConnection.class))).thenReturn(savedConnection);

        // When
        facebookConnectionService.handleCallback(code, state);

        // Then
        verify(platformConnectionRepository).save(argThat(conn -> 
            conn.getTokenExpiresAt() == null && 
            conn.getRefreshTokenEncrypted() == null
        ));
    }

    @Test
    public void should_return_connected_status_when_connection_exists() {
        // Given
        String creatorProfileId = "creator-123";
        PlatformConnection connection = new PlatformConnection();
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformType(PlatformType.FACEBOOK);
        connection.setPlatformName("Test Page");
        connection.setFollowerCount(50000L);
        connection.setLastSyncAt(LocalDateTime.now());
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK))
            .thenReturn(Optional.of(connection));

        // When
        PlatformConnectionResponse response = facebookConnectionService.getConnectionStatus(creatorProfileId);

        // Then
        assertThat(response.status()).isEqualTo(ConnectionStatus.CONNECTED.name());
        assertThat(response.platformName()).isEqualTo("Test Page");
        assertThat(response.followerCount()).isEqualTo(50000L);
    }

    @Test
    public void should_return_disconnected_when_no_connection_found() {
        // Given
        String creatorProfileId = "creator-123";
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK))
            .thenReturn(Optional.empty());

        // When
        PlatformConnectionResponse response = facebookConnectionService.getConnectionStatus(creatorProfileId);

        // Then
        assertThat(response.status()).isEqualTo(ConnectionStatus.DISCONNECTED.name());
        assertThat(response.platformName()).isNull();
        assertThat(response.followerCount()).isNull();
    }

    @Test
    public void should_disconnect_when_connection_exists() {
        // Given
        String creatorProfileId = "creator-123";
        PlatformConnection connection = new PlatformConnection();
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformType(PlatformType.FACEBOOK);
        connection.setAccessTokenEncrypted("encrypted-token");
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK))
            .thenReturn(Optional.of(connection));
        
        PlatformConnection updatedConnection = new PlatformConnection();
        updatedConnection.setStatus(ConnectionStatus.DISCONNECTED);
        updatedConnection.setPlatformType(PlatformType.FACEBOOK);
        updatedConnection.setPlatformName("Test Page");
        updatedConnection.setFollowerCount(50000L);
        updatedConnection.setCreatedAt(LocalDateTime.now());
        when(platformConnectionRepository.save(connection)).thenReturn(updatedConnection);

        // When
        PlatformConnectionResponse response = facebookConnectionService.disconnectFacebook(creatorProfileId);

        // Then
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(connection.getAccessTokenEncrypted()).isNull();
        assertThat(connection.getRefreshTokenEncrypted()).isNull();
        assertThat(connection.getTokenExpiresAt()).isNull();
    }

    @Test
    public void should_throw_resource_not_found_when_no_connection_to_disconnect() {
        // Given
        String creatorProfileId = "creator-123";
        
        when(platformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> facebookConnectionService.disconnectFacebook(creatorProfileId))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}