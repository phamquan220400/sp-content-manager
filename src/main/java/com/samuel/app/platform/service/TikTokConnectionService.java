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
 * Service for managing TikTok platform connections and OAuth flow.
 * Handles authorization URL generation, OAuth callback processing,
 * connection status retrieval, and disconnection.
 *
 * Critical: TikTok uses "client_key" (not "client_id") throughout the OAuth flow.
 */
@Service
public class TikTokConnectionService {

    private static final String REDIS_STATE_KEY_PREFIX = "oauth:tt:state:";
    private static final String TIKTOK_AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/";
    private static final String TIKTOK_TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/";
    private static final String TIKTOK_USER_INFO_URL =
            "https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,follower_count";

    private final TikTokProperties tikTokProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestTemplate restTemplate;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final CreatorProfileRepository creatorProfileRepository;

    public TikTokConnectionService(
            TikTokProperties tikTokProperties,
            StringRedisTemplate stringRedisTemplate,
            RestTemplate restTemplate,
            PlatformConnectionRepository platformConnectionRepository,
            TokenEncryptionService tokenEncryptionService,
            CreatorProfileRepository creatorProfileRepository) {
        this.tikTokProperties = tikTokProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.restTemplate = restTemplate;
        this.platformConnectionRepository = platformConnectionRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.creatorProfileRepository = creatorProfileRepository;
    }

    /**
     * Generates TikTok OAuth authorization URL for authenticated user.
     * Stores CSRF state token in Redis with 10-minute TTL.
     *
     * @param userId the authenticated user's ID
     * @return response containing TikTok OAuth authorization URL
     */
    public TikTokAuthUrlResponse getAuthorizationUrl(String userId) {
        // Generate UUID state token
        String state = UUID.randomUUID().toString();

        // Store state → userId mapping in Redis with 10-minute TTL
        String stateKey = REDIS_STATE_KEY_PREFIX + state;
        stringRedisTemplate.opsForValue().set(stateKey, userId, Duration.ofMinutes(10));

        // Build TikTok OAuth authorization URL
        // IMPORTANT: TikTok uses "client_key" parameter, not "client_id"
        String authUrl = UriComponentsBuilder
                .fromHttpUrl(TIKTOK_AUTH_URL)
                .queryParam("client_key", tikTokProperties.getClientKey())
                .queryParam("redirect_uri", tikTokProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "user.info.profile,user.info.stats")
                .queryParam("state", state)
                .build()
                .toUriString();

        return new TikTokAuthUrlResponse(authUrl);
    }

    /**
     * Handles OAuth callback from TikTok authorization.
     * Validates state, exchanges code for tokens, fetches user info, and stores connection.
     *
     * @param code  authorization code from TikTok
     * @param state CSRF state token
     * @return platform connection response with connection details
     * @throws PlatformConnectionException if state is invalid or expired
     */
    public PlatformConnectionResponse handleCallback(String code, String state) {
        // Validate state: look up in Redis
        String stateKey = REDIS_STATE_KEY_PREFIX + state;
        String userId = stringRedisTemplate.opsForValue().get(stateKey);
        if (userId == null) {
            throw new PlatformConnectionException(PlatformType.TIKTOK, "Invalid or expired OAuth state");
        }

        // Delete state key from Redis (prevent replay)
        stringRedisTemplate.delete(stateKey);

        // Find creator profile by userId
        CreatorProfile creatorProfile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));

        // Exchange authorization code for tokens
        TikTokTokenResponse tokenResponse = exchangeCodeForTokens(code);

        // Fetch TikTok user info
        TikTokUserInfoResponse userInfoResponse = fetchUserInfo(tokenResponse.accessToken());
        String openId = userInfoResponse.data().user().openId();
        String displayName = userInfoResponse.data().user().displayName();
        Long followerCount = userInfoResponse.data().user().followerCount();

        // Encrypt tokens
        String encryptedAccessToken = tokenEncryptionService.encrypt(tokenResponse.accessToken());
        String encryptedRefreshToken = tokenEncryptionService.encrypt(tokenResponse.refreshToken());

        // Find or create platform connection (upsert pattern)
        PlatformConnection connection = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfile.getId(), PlatformType.TIKTOK)
                .orElseGet(() -> {
                    PlatformConnection newConnection = new PlatformConnection();
                    newConnection.setId(UUID.randomUUID().toString());
                    newConnection.setCreatorProfileId(creatorProfile.getId());
                    newConnection.setPlatformType(PlatformType.TIKTOK);
                    return newConnection;
                });

        // Update connection fields
        LocalDateTime now = LocalDateTime.now();
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformUserId(openId);
        connection.setPlatformName(displayName);
        connection.setFollowerCount(followerCount);
        connection.setAccessTokenEncrypted(encryptedAccessToken);
        connection.setRefreshTokenEncrypted(encryptedRefreshToken);
        connection.setTokenExpiresAt(now.plusSeconds(tokenResponse.expiresIn()));
        connection.setLastSyncAt(now);

        // Save connection
        PlatformConnection savedConnection = platformConnectionRepository.save(connection);

        return mapToResponse(savedConnection);
    }

    /**
     * Gets TikTok connection status for a creator profile.
     *
     * @param creatorProfileId the creator profile ID
     * @return platform connection response with status and details
     */
    public PlatformConnectionResponse getConnectionStatus(String creatorProfileId) {
        Optional<PlatformConnection> connectionOpt = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.TIKTOK);

        if (connectionOpt.isEmpty()) {
            return new PlatformConnectionResponse(
                    PlatformType.TIKTOK.name(),
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
     * Disconnects TikTok account for a creator profile.
     * Clears encrypted tokens and sets status to DISCONNECTED.
     *
     * @param creatorProfileId the creator profile ID
     * @return updated platform connection response
     * @throws ResourceNotFoundException if connection not found
     */
    public PlatformConnectionResponse disconnectTikTok(String creatorProfileId) {
        PlatformConnection connection = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.TIKTOK)
                .orElseThrow(() -> new ResourceNotFoundException("TikTok connection not found"));

        // Clear connection data
        connection.setStatus(ConnectionStatus.DISCONNECTED);
        connection.setAccessTokenEncrypted(null);
        connection.setRefreshTokenEncrypted(null);
        connection.setTokenExpiresAt(null);

        PlatformConnection savedConnection = platformConnectionRepository.save(connection);
        return mapToResponse(savedConnection);
    }

    /**
     * Exchanges authorization code for access and refresh tokens via TikTok token endpoint.
     * IMPORTANT: TikTok token endpoint uses "client_key" NOT "client_id".
     */
    private TikTokTokenResponse exchangeCodeForTokens(String authorizationCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_key", tikTokProperties.getClientKey());
        params.add("client_secret", tikTokProperties.getClientSecret());
        params.add("code", authorizationCode);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", tikTokProperties.getRedirectUri());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            return restTemplate.postForObject(TIKTOK_TOKEN_URL, request, TikTokTokenResponse.class);
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.TIKTOK, "Failed to exchange authorization code for tokens", e);
        }
    }

    /**
     * Fetches TikTok user info using Bearer access token.
     * Returns open_id, display_name, and follower_count from TikTok User Info API.
     */
    private TikTokUserInfoResponse fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(
                    TIKTOK_USER_INFO_URL, HttpMethod.GET, request, TikTokUserInfoResponse.class
            ).getBody();
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.TIKTOK, "Failed to fetch TikTok user information", e);
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
