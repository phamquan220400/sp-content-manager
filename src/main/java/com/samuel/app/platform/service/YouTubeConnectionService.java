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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing YouTube platform connections and OAuth flow.
 * Handles authorization URL generation, OAuth callback processing, 
 * connection status retrieval, and disconnection.
 */
@Service
public class YouTubeConnectionService {

    private static final String REDIS_STATE_KEY_PREFIX = "oauth:yt:state:";
    private static final String GOOGLE_OAUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String YOUTUBE_CHANNEL_URL = "https://www.googleapis.com/youtube/v3/channels";
    private static final String YOUTUBE_SCOPES = "https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/yt-analytics.readonly";

    private final YouTubeProperties youtubeProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestTemplate restTemplate;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final CreatorProfileRepository creatorProfileRepository;

    public YouTubeConnectionService(
            YouTubeProperties youtubeProperties,
            StringRedisTemplate stringRedisTemplate,
            RestTemplate restTemplate,
            PlatformConnectionRepository platformConnectionRepository,
            TokenEncryptionService tokenEncryptionService,
            CreatorProfileRepository creatorProfileRepository) {
        this.youtubeProperties = youtubeProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.restTemplate = restTemplate;
        this.platformConnectionRepository = platformConnectionRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.creatorProfileRepository = creatorProfileRepository;
    }

    /**
     * Generates YouTube OAuth authorization URL for authenticated user.
     * Stores CSRF state token in Redis with 10-minute TTL.
     * 
     * @param userId the authenticated user's ID
     * @return response containing Google OAuth authorization URL
     */
    public YouTubeAuthUrlResponse getAuthorizationUrl(String userId) {
        // Generate UUID state token
        String state = UUID.randomUUID().toString();

        // Store state → userId mapping in Redis with 10-minute TTL
        String stateKey = REDIS_STATE_KEY_PREFIX + state;
        stringRedisTemplate.opsForValue().set(stateKey, userId, Duration.ofMinutes(10));

        // Build Google OAuth authorization URL
        String authUrl = UriComponentsBuilder
                .fromHttpUrl(GOOGLE_OAUTH_URL)
                .queryParam("client_id", youtubeProperties.getClientId())
                .queryParam("redirect_uri", youtubeProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", YOUTUBE_SCOPES)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .toUriString();

        return new YouTubeAuthUrlResponse(authUrl);
    }

    /**
     * Handles OAuth callback from Google authorization.
     * Validates state, exchanges code for tokens, fetches channel info, and stores connection.
     * 
     * @param code authorization code from Google
     * @param state CSRF state token
     * @return platform connection response with connection details
     * @throws PlatformConnectionException if state is invalid or expired
     */
    public PlatformConnectionResponse handleCallback(String code, String state) {
        // Validate state: look up in Redis
        String stateKey = REDIS_STATE_KEY_PREFIX + state;
        String userId = stringRedisTemplate.opsForValue().get(stateKey);
        if (userId == null) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE, "Invalid or expired OAuth state");
        }

        // Delete state key from Redis (prevent replay)
        stringRedisTemplate.delete(stateKey);

        // Find creator profile by userId
        CreatorProfile creatorProfile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));

        // Exchange authorization code for tokens
        YouTubeTokenResponse tokenResponse = exchangeCodeForTokens(code);

        // Fetch channel info from YouTube
        YouTubeChannelResponse channelResponse = fetchChannelInfo(tokenResponse.accessToken());
        if (channelResponse.items().isEmpty()) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE, "No YouTube channel found for this account");
        }

        // Extract channel data
        YouTubeChannelResponse.Item channel = channelResponse.items().get(0);
        String channelId = channel.id();
        String channelName = channel.snippet().title();
        Long subscriberCount = channel.statistics().subscriberCount();

        // Encrypt tokens
        String encryptedAccessToken = tokenEncryptionService.encrypt(tokenResponse.accessToken());
        String encryptedRefreshToken = tokenEncryptionService.encrypt(tokenResponse.refreshToken());

        // Find or create platform connection (upsert pattern)
        PlatformConnection connection = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfile.getId(), PlatformType.YOUTUBE)
                .orElseGet(() -> {
                    PlatformConnection newConnection = new PlatformConnection();
                    newConnection.setId(UUID.randomUUID().toString());
                    newConnection.setCreatorProfileId(creatorProfile.getId());
                    newConnection.setPlatformType(PlatformType.YOUTUBE);
                    return newConnection;
                });

        // Update connection fields
        LocalDateTime now = LocalDateTime.now();
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformUserId(channelId);
        connection.setPlatformName(channelName);
        connection.setFollowerCount(subscriberCount);
        connection.setAccessTokenEncrypted(encryptedAccessToken);
        connection.setRefreshTokenEncrypted(encryptedRefreshToken);
        connection.setTokenExpiresAt(now.plusSeconds(tokenResponse.expiresIn()));
        connection.setLastSyncAt(now);

        // Save connection
        PlatformConnection savedConnection = platformConnectionRepository.save(connection);

        // Return response
        return mapToResponse(savedConnection);
    }

    /**
     * Gets connection status for a creator profile.
     * 
     * @param creatorProfileId the creator profile ID
     * @return platform connection response with status and details
     */
    public PlatformConnectionResponse getConnectionStatus(String creatorProfileId) {
        Optional<PlatformConnection> connectionOpt = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.YOUTUBE);

        if (connectionOpt.isEmpty()) {
            // Return disconnected response if no connection found
            return new PlatformConnectionResponse(
                    PlatformType.YOUTUBE.name(),
                    ConnectionStatus.DISCONNECTED.name(),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return mapToResponse(connectionOpt.get());
    }

    /**
     * Disconnects YouTube channel for a creator profile.
     * Clears encrypted tokens and sets status to DISCONNECTED.
     * 
     * @param creatorProfileId the creator profile ID
     * @return updated platform connection response
     * @throws ResourceNotFoundException if connection not found
     */
    public PlatformConnectionResponse disconnectYouTube(String creatorProfileId) {
        PlatformConnection connection = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.YOUTUBE)
                .orElseThrow(() -> new ResourceNotFoundException("YouTube connection not found"));

        // Clear connection data
        connection.setStatus(ConnectionStatus.DISCONNECTED);
        connection.setAccessTokenEncrypted(null);
        connection.setRefreshTokenEncrypted(null);
        connection.setTokenExpiresAt(null);

        // Save and return updated response
        PlatformConnection savedConnection = platformConnectionRepository.save(connection);
        return mapToResponse(savedConnection);
    }

    /**
     * Exchanges authorization code for access and refresh tokens.
     */
    private YouTubeTokenResponse exchangeCodeForTokens(String authorizationCode) {
        // Prepare form-encoded request body
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", authorizationCode);
        params.add("client_id", youtubeProperties.getClientId());
        params.add("client_secret", youtubeProperties.getClientSecret());
        params.add("redirect_uri", youtubeProperties.getRedirectUri());
        params.add("grant_type", "authorization_code");

        // Set content type header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        // Make token exchange request
        try {
            return restTemplate.postForObject(GOOGLE_TOKEN_URL, request, YouTubeTokenResponse.class);
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE, "Failed to exchange authorization code for tokens", e);
        }
    }

    /**
     * Fetches YouTube channel information using access token.
     */
    private YouTubeChannelResponse fetchChannelInfo(String accessToken) {
        // Set authorization header
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Build channel info URL
        String channelUrl = UriComponentsBuilder
                .fromHttpUrl(YOUTUBE_CHANNEL_URL)
                .queryParam("part", "snippet,statistics")
                .queryParam("mine", "true")
                .build()
                .toUriString();

        // Make channel info request
        try {
            return restTemplate.exchange(channelUrl, HttpMethod.GET, request, YouTubeChannelResponse.class).getBody();
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.YOUTUBE, "Failed to fetch YouTube channel information", e);
        }
    }

    /**
     * Maps PlatformConnection entity to response DTO.
     */
    private PlatformConnectionResponse mapToResponse(PlatformConnection connection) {
        return new PlatformConnectionResponse(
                connection.getPlatformType().name(),
                connection.getStatus().name(),
                connection.getPlatformUserId(),
                connection.getPlatformName(),
                connection.getFollowerCount(),
                connection.getLastSyncAt(),
                connection.getCreatedAt()
        );
    }
}