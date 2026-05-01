package com.samuel.app.platform.service;

import com.samuel.app.creator.model.CreatorProfile;
import com.samuel.app.creator.repository.CreatorProfileRepository;
import com.samuel.app.exceptions.ResourceNotFoundException;
import com.samuel.app.platform.adapter.ConnectionStatus;
import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.config.FacebookProperties;
import com.samuel.app.platform.dto.FacebookAuthUrlResponse;
import com.samuel.app.platform.dto.FacebookPageDetailsResponse;
import com.samuel.app.platform.dto.InstagramTokenResponse;
import com.samuel.app.platform.dto.MetaPageResponse;
import com.samuel.app.platform.dto.PlatformConnectionResponse;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FacebookConnectionService {

    private final FacebookProperties facebookProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RestTemplate restTemplate;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final CreatorProfileRepository creatorProfileRepository;

    public FacebookConnectionService(FacebookProperties facebookProperties,
                                   StringRedisTemplate stringRedisTemplate,
                                   RestTemplate restTemplate,
                                   PlatformConnectionRepository platformConnectionRepository,
                                   TokenEncryptionService tokenEncryptionService,
                                   CreatorProfileRepository creatorProfileRepository) {
        this.facebookProperties = facebookProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.restTemplate = restTemplate;
        this.platformConnectionRepository = platformConnectionRepository;
        this.tokenEncryptionService = tokenEncryptionService;
        this.creatorProfileRepository = creatorProfileRepository;
    }

    public FacebookAuthUrlResponse getAuthorizationUrl(String userId) {
        // Generate UUID state token
        String state = UUID.randomUUID().toString();
        
        // Store state in Redis with 10-minute TTL
        String redisKey = "oauth:fb:state:" + state;
        stringRedisTemplate.opsForValue().set(redisKey, userId, 600L, TimeUnit.SECONDS);
        
        // Build Meta OAuth authorization URL
        String authorizationUrl = UriComponentsBuilder
            .fromHttpUrl("https://www.facebook.com/v18.0/dialog/oauth")
            .queryParam("client_id", facebookProperties.getClientId())
            .queryParam("redirect_uri", facebookProperties.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", "pages_show_list,pages_read_engagement,pages_manage_posts")
            .queryParam("state", state)
            .build()
            .toUriString();
        
        return new FacebookAuthUrlResponse(authorizationUrl);
    }

    public PlatformConnectionResponse handleCallback(String code, String state) {
        // 1. Validate state
        String redisKey = "oauth:fb:state:" + state;
        String userId = stringRedisTemplate.opsForValue().get(redisKey);
        if (userId == null) {
            throw new PlatformConnectionException(PlatformType.FACEBOOK, "Invalid or expired OAuth state");
        }
        
        // 2. Delete state key from Redis (prevent replay)
        stringRedisTemplate.delete(redisKey);
        
        // 3. Retrieve userId from Redis value
        CreatorProfile creatorProfile = creatorProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found"));
        
        // 4. Exchange authorization code for short-lived user token (POST)
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", facebookProperties.getClientId());
        params.add("client_secret", facebookProperties.getClientSecret());
        params.add("code", code);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", facebookProperties.getRedirectUri());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        InstagramTokenResponse shortLivedTokenResponse = restTemplate.postForObject(
            "https://graph.facebook.com/v18.0/oauth/access_token",
            request,
            InstagramTokenResponse.class
        );
        
        // 5. Exchange short-lived → long-lived user token (GET)
        String longLivedTokenUrl = UriComponentsBuilder
            .fromHttpUrl("https://graph.facebook.com/v18.0/oauth/access_token")
            .queryParam("grant_type", "fb_exchange_token")
            .queryParam("client_id", facebookProperties.getClientId())
            .queryParam("client_secret", facebookProperties.getClientSecret())
            .queryParam("fb_exchange_token", shortLivedTokenResponse.accessToken())
            .build()
            .toUriString();

        InstagramTokenResponse longLivedTokenResponse = restTemplate.getForObject(
            longLivedTokenUrl,
            InstagramTokenResponse.class
        );
        
        // 6. Get list of Facebook pages (GET /me/accounts)
        String pagesUrl = "https://graph.facebook.com/v18.0/me/accounts?access_token=" + longLivedTokenResponse.accessToken();
        MetaPageResponse pagesResponse = restTemplate.getForObject(pagesUrl, MetaPageResponse.class);
        
        // 7. Check if pages exist
        if (pagesResponse.data().isEmpty()) {
            throw new PlatformConnectionException(PlatformType.FACEBOOK, 
                "No Facebook Pages found. Please create a Facebook Page to connect to this platform.");
        }
        
        // 8. Select first page
        MetaPageResponse.Page page = pagesResponse.data().get(0);
        String pageId = page.id();
        String pageAccessToken = page.pageAccessToken();
        
        // 9. Fetch page details to get fan_count
        String pageDetailsUrl = String.format(
            "https://graph.facebook.com/v18.0/%s?fields=id,name,fan_count,category&access_token=%s",
            pageId, pageAccessToken
        );
        FacebookPageDetailsResponse pageDetails = restTemplate.getForObject(pageDetailsUrl, FacebookPageDetailsResponse.class);
        
        // 10. Encrypt the page access token
        String encryptedPageAccessToken = tokenEncryptionService.encrypt(pageAccessToken);
        
        // 11. Find or create PlatformConnection (upsert pattern)
        PlatformConnection connection = platformConnectionRepository
            .findByCreatorProfileIdAndPlatformType(creatorProfile.getId(), PlatformType.FACEBOOK)
            .orElseGet(() -> {
                PlatformConnection c = new PlatformConnection();
                c.setId(UUID.randomUUID().toString());
                c.setCreatorProfileId(creatorProfile.getId());
                c.setPlatformType(PlatformType.FACEBOOK);
                return c;
            });
        
        // 12. Set connection fields
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setPlatformUserId(pageId);
        connection.setPlatformName(pageDetails.name());
        connection.setFollowerCount(pageDetails.fanCount());
        connection.setAccessTokenEncrypted(encryptedPageAccessToken);
        connection.setRefreshTokenEncrypted(null); // page tokens have no refresh
        connection.setTokenExpiresAt(null); // page tokens don't expire based on time
        connection.setLastSyncAt(LocalDateTime.now());
        
        // 13. Save and return mapped response
        PlatformConnection savedConnection = platformConnectionRepository.save(connection);
        return mapToResponse(savedConnection);
    }

    public PlatformConnectionResponse getConnectionStatus(String creatorProfileId) {
        Optional<PlatformConnection> connectionOpt = platformConnectionRepository
                .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK);

        if (connectionOpt.isEmpty()) {
            return new PlatformConnectionResponse(
                    PlatformType.FACEBOOK.name(),
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

    public PlatformConnectionResponse disconnectFacebook(String creatorProfileId) {
        PlatformConnection connection = platformConnectionRepository
            .findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK)
            .orElseThrow(() -> new ResourceNotFoundException("Facebook connection not found"));
        
        connection.setStatus(ConnectionStatus.DISCONNECTED);
        connection.setAccessTokenEncrypted(null);
        connection.setRefreshTokenEncrypted(null);
        connection.setTokenExpiresAt(null);
        
        PlatformConnection savedConnection = platformConnectionRepository.save(connection);
        return mapToResponse(savedConnection);
    }

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