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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
 * Service for managing Instagram platform connections and OAuth flow.
 * Handles authorization URL generation, OAuth callback processing,
 * connection status retrieval, and disconnection.
 *
 * Uses Meta Graph API (Business/Creator flow) requiring Instagram Business
 * Account linked to a Facebook Page. Two-step token exchange: code → short-lived → long-lived.
 */
@Service
public class InstagramConnectionService {

    private static final String REDIS_STATE_KEY_PREFIX = "oauth:ig:state:";
    private static final String META_AUTH_URL = "https://www.facebook.com/v18.0/dialog/oauth";
    private static final String META_TOKEN_URL = "https://graph.facebook.com/v18.0/oauth/access_token";
    private static final String META_PAGES_URL = "https://graph.facebook.com/v18.0/me/accounts";
    private static final String META_PAGE_IG_ACCOUNT_URL_TEMPLATE = "https://graph.facebook.com/v18.0/%s";
    private static final String META_IG_USER_URL_TEMPLATE = "https://graph.facebook.com/v18.0/%s";

    private final InstagramProperties instagramProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestTemplate restTemplate;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final CreatorProfileRepository creatorProfileRepository;

    public InstagramConnectionService(
            InstagramProperties instagramProperties,
            StringRedisTemplate stringRedisTemplate,
            RestTemplate restTemplate,
            PlatformConnectionRepository platformConnectionRepository,
            TokenEncryptionService tokenEncryptionService,
            CreatorProfileRepository creatorProfileRepository) {
        this.instagramProperties = instagramProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.restTemplate = restTemplate;
        this.platformConnectionRepository = platformConnectionRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.creatorProfileRepository = creatorProfileRepository;
    }

    /**
     * Generates Instagram OAuth authorization URL for authenticated user.
     * Stores CSRF state token in Redis with 10-minute TTL.
     *
     * @param userId the authenticated user's ID
     * @return response containing Instagram OAuth authorization URL
     */
    public InstagramAuthUrlResponse getAuthorizationUrl(String userId) {
        // Generate UUID state token
        String state = UUID.randomUUID().toString();

        // Store state → userId mapping in Redis with 10-minute TTL
        String stateKey = REDIS_STATE_KEY_PREFIX + state;
        stringRedisTemplate.opsForValue().set(stateKey, userId, Duration.ofMinutes(10));

        // Build Meta OAuth authorization URL
        // Instagram uses client_id (same as YouTube, not TikTok's client_key)
        String authUrl = UriComponentsBuilder
                .fromHttpUrl(META_AUTH_URL)
                .queryParam("client_id", instagramProperties.getClientId())
                .queryParam("redirect_uri", instagramProperties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "instagram_basic,pages_read_engagement,pages_show_list")
                .queryParam("state", state)
                .build()
                .toUriString();

        return new InstagramAuthUrlResponse(authUrl);
    }

    /**
     * Handles OAuth callback from Instagram authorization.
     * Validates state, performs two-step token exchange, fetches user info from multiple APIs,
     * and stores connection with encrypted long-lived token.
     *
     * @param code  authorization code from Meta
     * @param state CSRF state token
     * @return platform connection response with connection details
     * @throws PlatformConnectionException if state is invalid, no pages found, or no IG business account
     */
    public PlatformConnectionResponse handleCallback(String code, String state) {
        // Validate state: look up in Redis
        String stateKey = REDIS_STATE_KEY_PREFIX + state;
        String userId = stringRedisTemplate.opsForValue().get(stateKey);
        if (userId == null) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Invalid or expired OAuth state");
        }

        // Delete state key from Redis (prevent replay)
        stringRedisTemplate.delete(stateKey);

        // Find creator profile by userId
        CreatorProfile creatorProfile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user: " + userId));

        // Step 1: Exchange authorization code for short-lived token
        InstagramTokenResponse shortLivedTokenResponse = exchangeCodeForShortLivedToken(code);

        // Step 2: Exchange short-lived token for long-lived token (60 days)
        InstagramTokenResponse longLivedTokenResponse = exchangeForLongLivedToken(shortLivedTokenResponse.accessToken());

        // Step 3: Get user's Facebook pages
        MetaPageResponse pagesResponse = getUserPages(longLivedTokenResponse.accessToken());
        if (pagesResponse.data().isEmpty()) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, 
                "No Facebook Pages found. Instagram Business Account requires a linked Facebook Page.");
        }

        // Step 4: Get Instagram Business Account from first page
        String pageId = pagesResponse.data().get(0).id();
        MetaIgAccountResponse igAccountResponse = getPageInstagramAccount(pageId, longLivedTokenResponse.accessToken());
        if (igAccountResponse.instagramBusinessAccount() == null) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM,
                "No Instagram Business Account found. Please ensure your Instagram account is linked to a Facebook Page.");
        }

        // Step 5: Fetch Instagram user info
        String igUserId = igAccountResponse.instagramBusinessAccount().id();
        InstagramUserResponse userResponse = getInstagramUserInfo(igUserId, longLivedTokenResponse.accessToken());

        // Encrypt long-lived access token
        String encryptedAccessToken = tokenEncryptionService.encrypt(longLivedTokenResponse.accessToken());

        // Find or create platform connection (upsert pattern)
        PlatformConnection connection = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfile.getId(), PlatformType.INSTAGRAM)
                .orElseGet(() -> {
                    PlatformConnection newConnection = new PlatformConnection();
                    newConnection.setId(UUID.randomUUID().toString());
                    newConnection.setCreatorProfileId(creatorProfile.getId());
                    newConnection.setPlatformType(PlatformType.INSTAGRAM);
                    return newConnection;
                });

        // Update connection fields
        LocalDateTime now = LocalDateTime.now();
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformUserId(igUserId);
        connection.setPlatformName(userResponse.username());
        connection.setFollowerCount(userResponse.followersCount());
        connection.setAccessTokenEncrypted(encryptedAccessToken);
        connection.setRefreshTokenEncrypted(null); // Meta long-lived tokens don't use refresh tokens
        connection.setTokenExpiresAt(now.plusSeconds(longLivedTokenResponse.expiresIn()));
        connection.setLastSyncAt(now);

        // Save connection
        PlatformConnection savedConnection = platformConnectionRepository.save(connection);

        return mapToResponse(savedConnection);
    }

    /**
     * Gets Instagram connection status for a creator profile.
     *
     * @param creatorProfileId the creator profile ID
     * @return platform connection response with status and details
     */
    public PlatformConnectionResponse getConnectionStatus(String creatorProfileId) {
        Optional<PlatformConnection> connectionOpt = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.INSTAGRAM);

        if (connectionOpt.isEmpty()) {
            return new PlatformConnectionResponse(
                    PlatformType.INSTAGRAM.name(),
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
     * Disconnects Instagram account for a creator profile.
     * Clears encrypted tokens and sets status to DISCONNECTED.
     *
     * @param creatorProfileId the creator profile ID
     * @return updated platform connection response
     * @throws ResourceNotFoundException if connection not found
     */
    public PlatformConnectionResponse disconnectInstagram(String creatorProfileId) {
        PlatformConnection connection = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.INSTAGRAM)
                .orElseThrow(() -> new ResourceNotFoundException("Instagram connection not found"));

        // Clear connection data
        connection.setStatus(ConnectionStatus.DISCONNECTED);
        connection.setAccessTokenEncrypted(null);
        connection.setRefreshTokenEncrypted(null);
        connection.setTokenExpiresAt(null);

        PlatformConnection savedConnection = platformConnectionRepository.save(connection);
        return mapToResponse(savedConnection);
    }

    /**
     * Step 1: Exchanges authorization code for short-lived access token (~1 hour).
     */
    private InstagramTokenResponse exchangeCodeForShortLivedToken(String authorizationCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", instagramProperties.getClientId());
        params.add("client_secret", instagramProperties.getClientSecret());
        params.add("code", authorizationCode);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", instagramProperties.getRedirectUri());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            return restTemplate.postForObject(META_TOKEN_URL, request, InstagramTokenResponse.class);
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Failed to exchange authorization code for short-lived token", e);
        }
    }

    /**
     * Step 2: Exchanges short-lived token for long-lived token (~60 days).
     */
    private InstagramTokenResponse exchangeForLongLivedToken(String shortLivedToken) {
        String longLivedTokenUrl = UriComponentsBuilder
                .fromHttpUrl(META_TOKEN_URL)
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", instagramProperties.getClientId())
                .queryParam("client_secret", instagramProperties.getClientSecret())
                .queryParam("fb_exchange_token", shortLivedToken)
                .build()
                .toUriString();

        try {
            return restTemplate.getForObject(longLivedTokenUrl, InstagramTokenResponse.class);
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Failed to exchange short-lived token for long-lived token", e);
        }
    }

    /**
     * Step 3: Retrieves user's Facebook pages using long-lived token.
     */
    private MetaPageResponse getUserPages(String accessToken) {
        String pagesUrl = UriComponentsBuilder
                .fromHttpUrl(META_PAGES_URL)
                .queryParam("access_token", accessToken)
                .build()
                .toUriString();

        try {
            return restTemplate.getForObject(pagesUrl, MetaPageResponse.class);
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Failed to retrieve Facebook pages", e);
        }
    }

    /**
     * Step 4: Gets Instagram Business Account linked to Facebook page.
     */
    private MetaIgAccountResponse getPageInstagramAccount(String pageId, String accessToken) {
        String pageIgUrl = UriComponentsBuilder
                .fromHttpUrl(String.format(META_PAGE_IG_ACCOUNT_URL_TEMPLATE, pageId))
                .queryParam("fields", "instagram_business_account")
                .queryParam("access_token", accessToken)
                .build()
                .toUriString();

        try {
            return restTemplate.getForObject(pageIgUrl, MetaIgAccountResponse.class);
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Failed to retrieve Instagram Business Account", e);
        }
    }

    /**
     * Step 5: Fetches Instagram user profile information.
     */
    private InstagramUserResponse getInstagramUserInfo(String igUserId, String accessToken) {
        String userInfoUrl = UriComponentsBuilder
                .fromHttpUrl(String.format(META_IG_USER_URL_TEMPLATE, igUserId))
                .queryParam("fields", "id,username,followers_count")
                .queryParam("access_token", accessToken)
                .build()
                .toUriString();

        try {
            return restTemplate.getForObject(userInfoUrl, InstagramUserResponse.class);
        } catch (Exception e) {
            throw new PlatformConnectionException(PlatformType.INSTAGRAM, "Failed to fetch Instagram user information", e);
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