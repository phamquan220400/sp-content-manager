# Story 2.6: Facebook Platform Connection and Authentication

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a creator,
I want to connect my Facebook page to the platform,
so that I can manage Facebook content alongside other social media platforms.

## Acceptance Criteria

1. **AC1 — OAuth authorization URL generated for Facebook (Meta Graph API)**
   **Given** I am an authenticated creator on the platform
   **When** I request a Facebook OAuth authorization URL via `GET /platforms/facebook/auth/url`
   **Then** I receive a Facebook Login OAuth URL pointing to `https://www.facebook.com/v18.0/dialog/oauth`
   **And** the URL includes a CSRF state token (UUID) stored in Redis with 10-minute TTL under key `oauth:fb:state:{state}`
   **And** the URL includes scopes `pages_show_list,pages_read_engagement,pages_manage_posts`
   **And** only authenticated users can call this endpoint

2. **AC2 — OAuth callback exchanges code and stores Facebook Page connection**
   **Given** I have granted Facebook permissions on the Meta consent screen
   **When** Meta redirects to `GET /platforms/facebook/callback?code=...&state=...`
   **Then** the state parameter is validated against Redis (CSRF protection)
   **And** the authorization code is exchanged for a short-lived user access token via `POST https://graph.facebook.com/v18.0/oauth/access_token`
   **And** the short-lived token is exchanged for a long-lived user token (~60 days) via `GET https://graph.facebook.com/v18.0/oauth/access_token?grant_type=fb_exchange_token`
   **And** the long-lived user token is used to retrieve my Facebook pages via `GET https://graph.facebook.com/v18.0/me/accounts?access_token={token}`
   **And** the first available page is selected (MVP scope — multi-page selection is deferred)
   **And** page details are fetched: `GET https://graph.facebook.com/v18.0/{page-id}?fields=id,name,fan_count,category&access_token={page-token}`
   **And** the **page access token** (not the user token) is stored encrypted — page tokens do not expire based on time
   **And** the connection is stored in the `platform_connections` table with status `CONNECTED`

3. **AC3 — Connection status reflects CONNECTED with Facebook Page details**
   **Given** I have successfully connected my Facebook page
   **When** I request my Facebook connection status via `GET /platforms/facebook/connection`
   **Then** I receive the connection status as `CONNECTED`
   **And** the response includes my Facebook page name, fan count (as followerCount), and last sync timestamp

4. **AC4 — Facebook page can be disconnected**
   **Given** I have a connected Facebook page
   **When** I request disconnection via `DELETE /platforms/facebook/disconnect`
   **Then** the platform connection status is set to `DISCONNECTED`
   **And** the encrypted page token is cleared from the database
   **And** the response confirms the disconnection

5. **AC5 — Facebook page access token is encrypted at rest**
   **Given** the token exchange produces a page access token
   **When** the token is persisted to `platform_connections.access_token_encrypted`
   **Then** the token is encrypted using the existing `TokenEncryptionService` (AES-256-GCM)
   **And** the same `${PLATFORM_TOKEN_ENCRYPTION_KEY}` env var used for YouTube, TikTok, and Instagram is reused
   **And** `refresh_token_encrypted` is set to `null` (page access tokens do not use refresh tokens)
   **And** `token_expires_at` is set to `null` (page access tokens do not expire based on time)

6. **AC6 — Connection health monitored via circuit breaker**
   **Given** the `FacebookAdapter` methods are annotated with `@CircuitBreaker(name = "facebook-api")`
   **When** the circuit breaker is OPEN (Facebook/Meta API unreachable)
   **Then** `getConnectionStatus()` returns `CIRCUIT_OPEN`
   **And** circuit breaker state is visible at `/actuator/health` (already configured from Story 2.1)

7. **AC7 — Circuit breaker opens on Facebook API failures**
   **Given** the Facebook/Meta API is repeatedly failing with `PlatformApiException`
   **When** the failure rate exceeds 50% over the last 10 calls (inherited from Story 2.1 default config)
   **Then** the circuit breaker transitions to OPEN state
   **And** subsequent calls to `FacebookAdapter` immediately throw `PlatformConnectionException` without hitting the API
   **And** the platform connection status reflects `CIRCUIT_OPEN`

8. **AC8 — No available Facebook pages handled gracefully**
   **Given** I have granted OAuth permissions but have no Facebook pages
   **When** the callback processes and `/me/accounts` returns an empty list
   **Then** a clear error message is returned: `"No Facebook Pages found. Please create a Facebook Page to connect to this platform."`
   **And** the error is returned as a 400 Bad Request (not a 500)

## Tasks / Subtasks

- [x] **Task 1: Add Facebook OAuth configuration properties (AC: 1, 2, 5)**
  - [x] Create `com.samuel.app.platform.config.FacebookProperties` using `@ConfigurationProperties(prefix = "facebook")`:
    - `String clientId` (from `${facebook.client-id}`) — Meta/Facebook uses `client_id` (same as YouTube and Instagram, NOT TikTok's `client_key`)
    - `String clientSecret` (from `${facebook.client-secret}`)
    - `String redirectUri` (from `${facebook.redirect-uri}`)
    - Annotate class with `@Configuration` and `@ConfigurationProperties(prefix = "facebook")`
    - File: `src/main/java/com/samuel/app/platform/config/FacebookProperties.java`
  - [x] Add to `application-dev.yml` under `facebook:` section (append after existing `instagram:` section):
    ```yaml
    facebook:
      client-id: ${FACEBOOK_CLIENT_ID:your-facebook-client-id}
      client-secret: ${FACEBOOK_CLIENT_SECRET:your-facebook-client-secret}
      redirect-uri: ${FACEBOOK_REDIRECT_URI:http://localhost:8080/api/v1/platforms/facebook/callback}
    ```
    **DO NOT** re-add `platform.token-encryption-key` — already present from Story 2.2.
    **DO NOT** add `facebook-api` to Resilience4j config — already present in `application-dev.yml` from Story 2.1.

- [x] **Task 2: Create Facebook-specific DTOs (AC: 1, 2, 3)**
  - [x] Create `com.samuel.app.platform.dto.FacebookAuthUrlResponse` record:
    - `String authorizationUrl`
    - File: `src/main/java/com/samuel/app/platform/dto/FacebookAuthUrlResponse.java`
  - [x] Create `com.samuel.app.platform.dto.FacebookPageDetailsResponse` record (internal, maps `/{page-id}?fields=id,name,fan_count,category` response):
    - `@JsonProperty("id") String id`
    - `@JsonProperty("name") String name`
    - `@JsonProperty("fan_count") Long fanCount`
    - `@JsonProperty("category") String category`
    - File: `src/main/java/com/samuel/app/platform/dto/FacebookPageDetailsResponse.java`
  - **REUSE** existing DTOs (DO NOT recreate):
    - `MetaPageResponse` — already defined, maps `/me/accounts` response and includes `pageAccessToken` field ✅
    - `InstagramTokenResponse` — reusable for the short-lived and long-lived user token exchange (same JSON structure: `access_token`, `token_type`, `expires_in`) ✅
    - `PlatformConnectionResponse` — used for all external API responses ✅

- [ ] **Task 3: Create FacebookConnectionService (AC: 1, 2, 3, 4, 5, 8)**
  - [ ] Create `com.samuel.app.platform.service.FacebookConnectionService`:
    - `@Service` with constructor injection: `FacebookProperties`, `StringRedisTemplate`, `RestTemplate`, `PlatformConnectionRepository`, `TokenEncryptionService`, `CreatorProfileRepository`
    - **`getAuthorizationUrl(String userId)`**:
      1. Generate UUID state token
      2. Store `"oauth:fb:state:{state}" → userId` in Redis with 10-minute TTL
         - Key prefix: `oauth:fb:state:` (consistent with `oauth:yt:state:`, `oauth:tt:state:`, `oauth:ig:state:` patterns)
      3. Build Meta OAuth authorization URL:
         - Base: `https://www.facebook.com/v18.0/dialog/oauth`
         - Params: `client_id={clientId}`, `redirect_uri={redirectUri}` (URL-encoded by UriComponentsBuilder), `response_type=code`, `scope=pages_show_list,pages_read_engagement,pages_manage_posts`, `state={state}`
         - Use `UriComponentsBuilder` from Spring Web (same pattern as `InstagramConnectionService`)
         - **IMPORTANT**: Facebook uses `client_id` (NOT TikTok's `client_key`)
      4. Return `FacebookAuthUrlResponse(authorizationUrl)`
    - **`handleCallback(String code, String state)`**:
      1. Validate state: look up `"oauth:fb:state:{state}"` in Redis; throw `PlatformConnectionException(FACEBOOK, "Invalid or expired OAuth state")` if missing
      2. Delete state key from Redis (prevent replay)
      3. Retrieve `userId` from Redis value
      4. Find creator profile: `CreatorProfileRepository.findByUserId(userId)` — throw `ResourceNotFoundException` if missing
      5. Exchange authorization code for short-lived user token (POST):
         - URL: `https://graph.facebook.com/v18.0/oauth/access_token`
         - Body (form-encoded `application/x-www-form-urlencoded`): `client_id`, `client_secret`, `code`, `grant_type=authorization_code`, `redirect_uri`
         - Map response to `InstagramTokenResponse` (reuse — same JSON structure)
      6. Exchange short-lived → long-lived user token (GET):
         - URL: `https://graph.facebook.com/v18.0/oauth/access_token?grant_type=fb_exchange_token&client_id={id}&client_secret={secret}&fb_exchange_token={shortLivedToken}`
         - Use `RestTemplate.getForObject` with `UriComponentsBuilder`
         - Map response to `InstagramTokenResponse` (reuse)
      7. Get list of Facebook pages (GET `/me/accounts`):
         - URL: `https://graph.facebook.com/v18.0/me/accounts?access_token={longLivedUserToken}`
         - Map response to `MetaPageResponse` (reuse — already has `id`, `name`, `pageAccessToken`)
         - If `pagesResponse.data().isEmpty()`: throw `PlatformConnectionException(FACEBOOK, "No Facebook Pages found. Please create a Facebook Page to connect to this platform.")`
      8. Select first page — store page ID and **page access token** from `MetaPageResponse.Page.pageAccessToken()`
      9. Fetch page details to get fan_count:
         - URL: `https://graph.facebook.com/v18.0/{pageId}?fields=id,name,fan_count,category&access_token={pageAccessToken}`
         - Map response to `FacebookPageDetailsResponse`
      10. Encrypt the **page access token** using `TokenEncryptionService.encrypt(pageAccessToken)`
      11. Find or create `PlatformConnection` for this creator + FACEBOOK (upsert pattern):
          ```java
          PlatformConnection connection = platformConnectionRepository
              .findByCreatorProfileIdAndPlatformType(creatorProfile.getId(), PlatformType.FACEBOOK)
              .orElseGet(() -> {
                  PlatformConnection c = new PlatformConnection();
                  c.setId(UUID.randomUUID().toString());
                  c.setCreatorProfileId(creatorProfile.getId());
                  c.setPlatformType(PlatformType.FACEBOOK);
                  return c;
              });
          ```
      12. Set connection fields:
          - `status = CONNECTED`
          - `platformUserId = pageId`
          - `platformName = pageDetails.name()`
          - `followerCount = pageDetails.fanCount()`
          - `accessTokenEncrypted = encryptedPageAccessToken`
          - `refreshTokenEncrypted = null` (page tokens have no refresh)
          - `tokenExpiresAt = null` (page tokens don't expire based on time)
          - `lastSyncAt = LocalDateTime.now()`
      13. Save and return `PlatformConnectionResponse` mapped from saved entity
    - **`getConnectionStatus(String creatorProfileId)`**:
      1. Find connection via `PlatformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.FACEBOOK)`
      2. If not found, return `PlatformConnectionResponse` with status `DISCONNECTED`, all other fields null
      3. Otherwise map entity to `PlatformConnectionResponse` and return
    - **`disconnectFacebook(String creatorProfileId)`**:
      1. Find connection — throw `ResourceNotFoundException` if not found
      2. Set `status = DISCONNECTED`, `accessTokenEncrypted = null`, `refreshTokenEncrypted = null`, `tokenExpiresAt = null`
      3. Save and return updated `PlatformConnectionResponse`
    - File: `src/main/java/com/samuel/app/platform/service/FacebookConnectionService.java`

- [ ] **Task 4: Create FacebookAdapter implementing IPlatformAdapter (AC: 6, 7)**
  - [ ] Create `com.samuel.app.platform.adapter.FacebookAdapter` implementing `IPlatformAdapter`:
    - `@Component("facebookAdapter")`
    - Constructor injection: `CircuitBreakerRegistry`, `RateLimiterRegistry`, `PlatformConnectionRepository`, `TokenEncryptionService`, `RestTemplate`
    - All methods annotated with `@CircuitBreaker(name = "facebook-api", fallbackMethod = "...")`, `@RateLimiter(name = "facebook-api")`, `@Retry(name = "facebook-api")`
    - **`connect(CreatorProfile creator, PlatformCredentials creds)`**:
      - Validates existing connection — OAuth flow is handled by `FacebookConnectionService`, not this method
      - Looks up connection via repository; throws `PlatformConnectionException(FACEBOOK, "No existing Facebook connection found")` if absent
      - Calls `GET /{pageId}?fields=id,name&access_token={decryptedToken}` via RestTemplate to validate page token is still valid
      - Returns the existing `PlatformConnection`
      - Fallback method `connectFallback`: throws `PlatformConnectionException(FACEBOOK, "Facebook API circuit breaker is OPEN")`
    - **`getConnectionStatus()`**:
      - Check circuit breaker state via `circuitBreakerRegistry.circuitBreaker("facebook-api").getState()`
      - Map: `CLOSED/HALF_OPEN → CONNECTED`, `OPEN → CIRCUIT_OPEN`, other → `DISCONNECTED`
    - **`getRemainingQuota()`**:
      - Return `RateLimitInfo` with `limit = 200`, `windowSeconds = 3600`
      - Use `rateLimiterRegistry.rateLimiter("facebook-api").getMetrics().getAvailablePermissions()`
    - **`getNextResetTime()`**:
      - Return `LocalDateTime.now(ZoneOffset.UTC).plusSeconds(FACEBOOK_RATE_LIMIT_WINDOW_SECONDS)` (3600s)
    - **`fetchMetrics(String platformUserId)`**:
      - Annotated with `@CircuitBreaker(name = "facebook-api", fallbackMethod = "fetchMetricsFallback")`
      - Returns `ContentMetrics` with zeroed values (page metrics not yet implemented)
      - Fallback: returns empty `ContentMetrics`
    - **`getRevenueData(String platformUserId, DateRange range)`**:
      - Returns empty `RevenueData` (Facebook revenue data not available in this story)
    - **`getPlatformType()`**:
      - Returns `PlatformType.FACEBOOK`
    - Constants:
      ```java
      private static final String META_PAGE_URL_TEMPLATE = "https://graph.facebook.com/v18.0/%s?fields=id,name";
      private static final int FACEBOOK_RATE_LIMIT = 200;
      private static final int FACEBOOK_RATE_LIMIT_WINDOW_SECONDS = 3600;
      ```
    - File: `src/main/java/com/samuel/app/platform/adapter/FacebookAdapter.java`

- [ ] **Task 5: Add Facebook endpoints to PlatformConnectionController (AC: 1, 2, 3, 4)**
  - [ ] Modify `src/main/java/com/samuel/app/platform/controller/PlatformConnectionController.java`:
    - Add `FacebookConnectionService` import and field; add to constructor injection (4th parameter after `instagramConnectionService`)
    - Add `FacebookAuthUrlResponse` import
    - Add `GET /platforms/facebook/auth/url` — authenticated:
      ```java
      @GetMapping("/facebook/auth/url")
      public ResponseEntity<ApiResponse<FacebookAuthUrlResponse>> getFacebookAuthUrl() {
          String userId = SecurityContextHolder.getContext().getAuthentication().getName();
          FacebookAuthUrlResponse response = facebookConnectionService.getAuthorizationUrl(userId);
          return ResponseEntity.ok(ApiResponse.ok(response));
      }
      ```
    - Add `GET /platforms/facebook/callback` — NOT authenticated (permitAll):
      - Same error handling pattern as YouTube/TikTok/Instagram callbacks
      - Handles `error`, `error_description` params (Meta sends these on user denial)
      - Calls `facebookConnectionService.handleCallback(code, state)`
    - Add `GET /platforms/facebook/connection` — authenticated:
      - Calls `getCreatorProfileId()` helper (already exists, no change)
      - Calls `facebookConnectionService.getConnectionStatus(creatorProfileId)`
    - Add `DELETE /platforms/facebook/disconnect` — authenticated:
      - Calls `getCreatorProfileId()` helper (already exists, no change)
      - Calls `facebookConnectionService.disconnectFacebook(creatorProfileId)`
    - **DO NOT** change existing YouTube, TikTok, or Instagram endpoints

- [ ] **Task 6: Update SecurityConfig to permit Facebook OAuth callback URL (AC: 2)**
  - [ ] Modify `src/main/java/com/samuel/app/config/SecurityConfig.java`:
    - Add `.requestMatchers("/platforms/facebook/callback").permitAll()` on a new line after the existing `.requestMatchers("/platforms/instagram/callback").permitAll()` line
    - Context path `/api/v1` is handled by `server.servlet.context-path` — Spring Security matches AFTER context path is stripped, so permit `/platforms/facebook/callback` (without `/api/v1` prefix)
    - **DO NOT** modify any other `permitAll` rules

- [ ] **Task 7: Write unit tests (AC: 1-8)**
  - [ ] Create `com.samuel.app.platform.service.FacebookConnectionServiceTest`:
    - `@ExtendWith(MockitoExtension.class)` — no Spring context
    - Mocks: `FacebookProperties`, `StringRedisTemplate`, `RestTemplate`, `PlatformConnectionRepository`, `TokenEncryptionService`, `CreatorProfileRepository`
    - Tests for `getAuthorizationUrl()`:
      - `should_return_auth_url_when_valid_user_then_state_stored_in_redis()`
      - `should_build_url_with_facebook_dialog_oauth_base()`
      - `should_build_url_with_pages_scopes()`
    - Tests for `handleCallback()`:
      - `should_connect_facebook_page_when_valid_callback_then_connection_saved_as_connected()`
      - `should_throw_when_oauth_state_is_invalid()`
      - `should_throw_platform_connection_exception_when_no_facebook_pages_found()`
      - `should_store_page_access_token_not_user_token()`
      - `should_set_token_expires_at_null_for_page_token()`
    - Tests for `getConnectionStatus()`:
      - `should_return_connected_status_when_connection_exists()`
      - `should_return_disconnected_when_no_connection_found()`
    - Tests for `disconnectFacebook()`:
      - `should_disconnect_when_connection_exists()`
      - `should_throw_resource_not_found_when_no_connection_to_disconnect()`
    - File: `src/test/java/com/samuel/app/platform/service/FacebookConnectionServiceTest.java`
  - [ ] Create `com.samuel.app.platform.adapter.FacebookAdapterTest`:
    - `@ExtendWith(MockitoExtension.class)`
    - Tests:
      - `should_return_circuit_open_when_circuit_breaker_state_is_open()`
      - `should_return_connected_when_circuit_breaker_closed()`
      - `should_return_facebook_platform_type()`
      - `should_return_rate_limit_info_with_200_limit_and_3600s_window()`
      - `should_return_empty_revenue_data_for_facebook()`
    - File: `src/test/java/com/samuel/app/platform/adapter/FacebookAdapterTest.java`
  - [ ] Create `com.samuel.app.platform.controller.PlatformConnectionControllerFacebookTest`:
    - `@ExtendWith(MockitoExtension.class)` with `MockMvcBuilders.standaloneSetup()`
    - Tests:
      - `should_return_facebook_auth_url_when_authenticated_user_requests()`
      - `should_handle_facebook_callback_and_return_connection_response()`
      - `should_return_bad_request_when_facebook_callback_has_error_param()`
      - `should_return_connection_status_when_user_requests_facebook_status()`
      - `should_return_success_when_user_disconnects_facebook()`
    - File: `src/test/java/com/samuel/app/platform/controller/PlatformConnectionControllerFacebookTest.java`

## Dev Notes

### Critical: Facebook Page Connection vs. Instagram Business Account

This story connects **Facebook Pages** (creator content destination) not Instagram accounts. The key difference from Story 2.5 (Instagram):

| Aspect | Story 2.5 — Instagram | Story 2.6 — Facebook |
|--------|----------------------|----------------------|
| Target resource | Instagram Business Account (linked to Page) | Facebook Page directly |
| OAuth scopes | `instagram_basic,pages_read_engagement,pages_show_list` | `pages_show_list,pages_read_engagement,pages_manage_posts` |
| Token stored | Long-lived **user** token (60-day) | Long-lived **page** access token (no expiry) |
| `token_expires_at` | `now + expiresIn seconds` | `null` (page tokens don't expire on time) |
| `refresh_token_encrypted` | null | null |
| Follower count field | `followers_count` from IG user | `fan_count` from Page details endpoint |
| API steps in callback | 5 steps (code → user token × 2 → pages → IG account → IG user) | 4 steps (code → user token × 2 → pages → page details) |

### Token Storage Strategy: Page Access Token

**Store the PAGE access token** (from `MetaPageResponse.Page.pageAccessToken()`), NOT the user access token.

Why: Page access tokens are what's needed for page content operations (posting, reading insights). Long-lived page access tokens **do not expire based on time** and only become invalid when:
- The user changes their Facebook password
- The user explicitly revokes app permissions
- The app is removed from the Page

Therefore: `token_expires_at = null` and `refresh_token_encrypted = null`.

### OAuth Flow — Four Steps

```
Creator → GET /platforms/facebook/auth/url
  → service generates UUID state
  → Redis: SET "oauth:fb:state:{state}" "{userId}" EX 600
  → returns https://www.facebook.com/v18.0/dialog/oauth?...&scope=pages_show_list,pages_read_engagement,pages_manage_posts

Creator → redirected to Meta consent screen
Meta → GET /platforms/facebook/callback?code=ABC&state={state}
  → STEP 1: POST code → short-lived user token
  → STEP 2: GET short-lived → long-lived user token (~60 days)
  → STEP 3: GET /me/accounts → list Facebook Pages; select first
  → STEP 4: GET /{page-id}?fields=id,name,fan_count,category → get page details
  → encrypt PAGE access token (from step 3), save to platform_connections
  → token_expires_at = null, refresh_token_encrypted = null
```

### Meta Graph API Endpoints Reference (Facebook Page flow)

| Step | Method | URL | Notes |
|------|--------|-----|-------|
| 1. Code → short-lived user token | POST | `https://graph.facebook.com/v18.0/oauth/access_token` | Form body: `client_id`, `client_secret`, `code`, `grant_type=authorization_code`, `redirect_uri` |
| 2. Short-lived → long-lived user token | GET | `https://graph.facebook.com/v18.0/oauth/access_token?grant_type=fb_exchange_token&client_id={id}&client_secret={secret}&fb_exchange_token={shortToken}` | Use `UriComponentsBuilder` to build URL |
| 3. Get pages | GET | `https://graph.facebook.com/v18.0/me/accounts?access_token={longUserToken}` | Returns list with embedded page access tokens |
| 4. Get page details | GET | `https://graph.facebook.com/v18.0/{page-id}?fields=id,name,fan_count,category&access_token={pageToken}` | Use page token from step 3 |

### Reusing Existing DTOs (DO NOT recreate)

- **`MetaPageResponse`** — already exists from Story 2.5; maps `/me/accounts` and includes `pageAccessToken`
  - Location: `src/main/java/com/samuel/app/platform/dto/MetaPageResponse.java`
- **`InstagramTokenResponse`** — already exists; use for both user token exchange steps (same JSON structure)
  - Location: `src/main/java/com/samuel/app/platform/dto/InstagramTokenResponse.java`
- **`PlatformConnectionResponse`** — already exists; use for all external responses
  - Location: `src/main/java/com/samuel/app/platform/dto/PlatformConnectionResponse.java`

### RestTemplate Token Exchange Pattern (Step 1 — POST)

```java
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
```

### RestTemplate Long-Lived Token Exchange Pattern (Step 2 — GET)

```java
String longLivedTokenUrl = UriComponentsBuilder
    .fromHttpUrl("https://graph.facebook.com/v18.0/oauth/access_token")
    .queryParam("grant_type", "fb_exchange_token")
    .queryParam("client_id", facebookProperties.getClientId())
    .queryParam("client_secret", facebookProperties.getClientSecret())
    .queryParam("fb_exchange_token", shortLivedToken)
    .build()
    .toUriString();

InstagramTokenResponse longLivedTokenResponse = restTemplate.getForObject(
    longLivedTokenUrl,
    InstagramTokenResponse.class
);
```

### PlatformConnection Mapping

```java
// Platform data stored:
connection.setPlatformType(PlatformType.FACEBOOK);
connection.setStatus(ConnectionStatus.CONNECTED);
connection.setPlatformUserId(pageId);                         // Facebook Page ID
connection.setPlatformName(pageDetails.name());               // Facebook Page name
connection.setFollowerCount(pageDetails.fanCount());          // fan_count from page endpoint
connection.setAccessTokenEncrypted(encryptedPageToken);       // Page access token (NOT user token)
connection.setRefreshTokenEncrypted(null);                    // No refresh for page tokens
connection.setTokenExpiresAt(null);                           // Page tokens don't expire on time
connection.setLastSyncAt(LocalDateTime.now());
```

### FacebookProperties — Correct Prefix and Field Names

```java
@Configuration
@ConfigurationProperties(prefix = "facebook")
public class FacebookProperties {
    private String clientId;   // maps to facebook.client-id in YAML (NOT client_key)
    private String clientSecret;
    private String redirectUri;
    // getters/setters
}
```

### Resilience4j Already Configured (DO NOT Add Again)

All Resilience4j configs for `facebook-api` already exist in `application-dev.yml` from Story 2.1:
- Circuit breaker: `facebook-api` with `base-config: default` ✅
- Rate limiter: `facebook-api` with `limit-for-period: 200`, `limit-refresh-period: 3600s` ✅
- Retry: `facebook-api` with `base-config: default` ✅

### FacebookAdapter Pattern (Mirror InstagramAdapter)

Follow `InstagramAdapter` exactly as the template. Key differences:
- `@Component("facebookAdapter")` (not `"instagramAdapter"`)
- All circuit breaker names: `"facebook-api"` (not `"instagram-api"`)
- `FACEBOOK_RATE_LIMIT = 200` with `FACEBOOK_RATE_LIMIT_WINDOW_SECONDS = 3600` (same as Instagram)
- `getPlatformType()` returns `PlatformType.FACEBOOK`
- File: `src/main/java/com/samuel/app/platform/adapter/FacebookAdapter.java`
- Architecture expects: `src/main/java/com/samuel/app/platform/adapter/FacebookAdapter.java` [Source: architecture.md#line 628]

### SecurityConfig Pattern (Follow Exactly)

Current SecurityConfig (line 70-72):
```java
.requestMatchers("/platforms/youtube/callback").permitAll()
.requestMatchers("/platforms/tiktok/callback").permitAll()
.requestMatchers("/platforms/instagram/callback").permitAll()
```
Add on line 73:
```java
.requestMatchers("/platforms/facebook/callback").permitAll()
```
No prefix `/api/v1` — Spring Security matches after context-path is stripped.

### Deferred Work Awareness

From `deferred-work.md`: Public OAuth callbacks have no rate limiting (flagged post-Story 2.3 fix). The `/platforms/facebook/callback` endpoint will inherit this same deferred concern — document it but do NOT implement it in this story.

### Test Pattern (Follow InstagramConnectionServiceTest)

- Use `@ExtendWith(MockitoExtension.class)` — NO Spring context loading
- Mock `StringRedisTemplate` via `when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)`
- For `MetaPageResponse`, construct like: `new MetaPageResponse(List.of(new MetaPageResponse.Page("page-123", "Test Page", "page-access-token")))`
- For `FacebookPageDetailsResponse`: `new FacebookPageDetailsResponse("page-123", "Test Page", 50000L, "Brand")`
- Use `MockMvcBuilders.standaloneSetup(controller)` in controller tests (no Spring context)

### Project Structure Alignment

```
New files to create:
src/main/java/com/samuel/app/platform/
  ├── config/
  │   └── FacebookProperties.java           (NEW)
  ├── adapter/
  │   └── FacebookAdapter.java              (NEW — mirrors InstagramAdapter)
  ├── dto/
  │   ├── FacebookAuthUrlResponse.java      (NEW — mirrors InstagramAuthUrlResponse)
  │   └── FacebookPageDetailsResponse.java  (NEW — unique to Facebook)
  └── service/
      └── FacebookConnectionService.java    (NEW — mirrors InstagramConnectionService with differences)

Files to modify:
src/main/java/com/samuel/app/platform/controller/
  └── PlatformConnectionController.java     (MODIFY — add facebook endpoints)
src/main/java/com/samuel/app/config/
  └── SecurityConfig.java                   (MODIFY — add /platforms/facebook/callback permitAll)
src/main/resources/
  └── application-dev.yml                   (MODIFY — add facebook: properties block)

New test files:
src/test/java/com/samuel/app/platform/
  ├── service/FacebookConnectionServiceTest.java
  ├── adapter/FacebookAdapterTest.java
  └── controller/PlatformConnectionControllerFacebookTest.java
```

### References

- [Source: `_bmad-output/planning-artifacts/architecture.md#line 628`] — `FacebookAdapter.java` in architecture file structure
- [Source: `src/main/java/com/samuel/app/platform/service/InstagramConnectionService.java`] — OAuth flow pattern to follow
- [Source: `src/main/java/com/samuel/app/platform/adapter/InstagramAdapter.java`] — Adapter pattern to follow
- [Source: `src/main/java/com/samuel/app/platform/dto/MetaPageResponse.java`] — Reuse this DTO for `/me/accounts` response
- [Source: `src/main/java/com/samuel/app/platform/dto/InstagramTokenResponse.java`] — Reuse for token exchange response
- [Source: `src/main/resources/application-dev.yml#lines 63,80,102`] — facebook-api Resilience4j configs already exist
- [Source: `https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived`] — Long-lived page access token do not have an expiration date
- [Source: `https://developers.facebook.com/docs/pages/access-tokens`] — Page access token obtained via `/me/accounts` endpoint includes `access_token` field per page

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5

### Debug Log References

### Completion Notes List

### File List
