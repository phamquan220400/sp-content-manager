# Story 2.2: YouTube Platform Connection and Authentication

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a creator,
I want to connect my YouTube channel to the platform,
so that I can manage my YouTube content and track revenue from the unified dashboard.

## Acceptance Criteria

1. **AC1 — OAuth authorization URL generated for YouTube**
   **Given** I am an authenticated creator on the platform
   **When** I request a YouTube OAuth authorization URL via `GET /platforms/youtube/auth/url`
   **Then** I receive a Google OAuth 2.0 authorization URL with correct scopes (`youtube.readonly`, `yt-analytics.readonly`)
   **And** the URL includes a CSRF state token (UUID) stored in Redis with 10-minute TTL
   **And** the URL includes `access_type=offline` to obtain a refresh token
   **And** only authenticated users can call this endpoint

2. **AC2 — OAuth callback exchanges code and stores connection**
   **Given** I have granted YouTube permissions on the Google consent screen
   **When** Google redirects to `GET /platforms/youtube/callback?code=...&state=...`
   **Then** the state parameter is validated against Redis (CSRF protection)
   **And** the authorization code is exchanged for access and refresh tokens via Google token endpoint
   **And** my YouTube channel information (channel ID, channel name, subscriber count) is fetched from YouTube Data API v3
   **And** the connection is stored in the `platform_connections` table with status `CONNECTED`
   **And** access and refresh tokens are AES-256-GCM encrypted before storage

3. **AC3 — Connection status reflects CONNECTED with channel details**
   **Given** I have successfully connected my YouTube channel
   **When** I request my YouTube connection status via `GET /platforms/youtube/connection`
   **Then** I receive the connection status as `CONNECTED`
   **And** the response includes my YouTube channel name, subscriber count, and last sync timestamp

4. **AC4 — YouTube channel can be disconnected**
   **Given** I have a connected YouTube channel
   **When** I request disconnection via `DELETE /platforms/youtube/disconnect`
   **Then** the platform connection status is set to `DISCONNECTED`
   **And** the encrypted tokens are cleared from the database
   **And** the response confirms the disconnection

5. **AC5 — YouTube API credentials are encrypted at rest**
   **Given** the token exchange produces an access token and refresh token
   **When** the tokens are persisted to `platform_connections.access_token_encrypted` and `platform_connections.refresh_token_encrypted`
   **Then** tokens are encrypted using AES-256-GCM before storage
   **And** tokens can be decrypted for subsequent API calls
   **And** encryption key is sourced from `${PLATFORM_TOKEN_ENCRYPTION_KEY}` environment variable (base64-encoded 32-byte key)

6. **AC6 — Connection health monitored via circuit breaker**
   **Given** the `YouTubeAdapter` is annotated with `@CircuitBreaker(name = "youtube-api")`
   **When** the circuit breaker is OPEN (YouTube API unreachable)
   **Then** `getConnectionStatus()` returns `CIRCUIT_OPEN`
   **And** subsequent API calls to the adapter fall back gracefully
   **And** circuit breaker state is visible at `/actuator/health` (already configured from Story 2.1)

7. **AC7 — Circuit breaker opens on YouTube API failures**
   **Given** YouTube API is repeatedly failing with `PlatformApiException`
   **When** the failure rate exceeds 50% over the last 10 calls (configured in Story 2.1)
   **Then** the circuit breaker transitions to OPEN state
   **And** subsequent calls to `YouTubeAdapter` immediately throw `PlatformConnectionException` without hitting the API
   **And** the platform connection status reflects `CIRCUIT_OPEN`

## Tasks / Subtasks

- [ ] **Task 1: Add YouTube OAuth and encryption configuration properties (AC: 1, 2, 5)**
  - [ ] Create `com.samuel.app.platform.config.YouTubeProperties` using `@ConfigurationProperties(prefix = "youtube")`:
    - `String clientId` (from `${youtube.client-id}`)
    - `String clientSecret` (from `${youtube.client-secret}`)
    - `String redirectUri` (from `${youtube.redirect-uri}`)
    - Annotate class with `@Configuration` and `@ConfigurationProperties(prefix = "youtube")`
    - File: `src/main/java/com/samuel/app/platform/config/YouTubeProperties.java`
  - [ ] Add to `application-dev.yml` under `youtube:` section:
    ```yaml
    youtube:
      client-id: ${YOUTUBE_CLIENT_ID:your-client-id}
      client-secret: ${YOUTUBE_CLIENT_SECRET:your-client-secret}
      redirect-uri: ${YOUTUBE_REDIRECT_URI:http://localhost:8080/api/v1/platforms/youtube/callback}
    platform:
      token-encryption-key: ${PLATFORM_TOKEN_ENCRYPTION_KEY:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=}
    ```
    Note: The default encryption key value is a placeholder (44-char base64 = 32 bytes of zeroes). MUST be overridden in production via env var.
  - [ ] Add `@EnableConfigurationProperties(YouTubeProperties.class)` to `AppApplication.java` or a `@Configuration` class

- [ ] **Task 2: Create TokenEncryptionService for AES-256-GCM encryption (AC: 5)**
  - [ ] Create `com.samuel.app.platform.service.TokenEncryptionService`:
    - `@Service` with constructor injection of encryption key from `@Value("${platform.token-encryption-key}")`
    - `encrypt(String plaintext)` → generates 12-byte random IV, encrypts with AES-256-GCM, returns `Base64(iv + ciphertext)` as String
    - `decrypt(String encrypted)` → decodes base64, extracts IV (first 12 bytes), decrypts remainder, returns plaintext
    - Uses `javax.crypto.Cipher` with `"AES/GCM/NoPadding"` — no new dependencies needed (built-in Java 17)
    - GCM tag length: 128 bits
    - Key: parse from base64-encoded environment variable using `java.util.Base64.getDecoder().decode(keyBase64)`
    - File: `src/main/java/com/samuel/app/platform/service/TokenEncryptionService.java`

- [ ] **Task 3: Create RestTemplate bean in a platform config class (AC: 2)**
  - [ ] Create `com.samuel.app.platform.config.PlatformWebConfig`:
    - `@Configuration` class
    - `@Bean public RestTemplate restTemplate(RestTemplateBuilder builder)` — uses Spring Boot's `RestTemplateBuilder` for proper timeout/error handling configuration
    - Set connection timeout 5 seconds, read timeout 10 seconds
    - File: `src/main/java/com/samuel/app/platform/config/PlatformWebConfig.java`

- [ ] **Task 4: Create DTOs for YouTube connection responses (AC: 1, 3)**
  - [ ] Create `com.samuel.app.platform.dto.YouTubeAuthUrlResponse` record:
    - `String authorizationUrl`
    - File: `src/main/java/com/samuel/app/platform/dto/YouTubeAuthUrlResponse.java`
  - [ ] Create `com.samuel.app.platform.dto.PlatformConnectionResponse` record:
    - `String platformType`, `String status`, `String platformUserId`, `String platformName`, `Long followerCount`, `java.time.LocalDateTime lastSyncAt`, `java.time.LocalDateTime connectedAt`
    - File: `src/main/java/com/samuel/app/platform/dto/PlatformConnectionResponse.java`
  - [ ] Create `com.samuel.app.platform.dto.YouTubeTokenResponse` record (internal, maps Google token endpoint JSON):
    - `@JsonProperty("access_token") String accessToken`, `@JsonProperty("refresh_token") String refreshToken`, `@JsonProperty("expires_in") long expiresIn`, `@JsonProperty("token_type") String tokenType`
    - File: `src/main/java/com/samuel/app/platform/dto/YouTubeTokenResponse.java`
  - [ ] Create `com.samuel.app.platform.dto.YouTubeChannelResponse` record (internal, maps YouTube Data API v3 channels.list response):
    - Nested structure: `List<Item> items` where `Item` has `String id`, `Snippet snippet`, `Statistics statistics`
    - `Snippet` has: `String title` (channel name)
    - `Statistics` has: `@JsonProperty("subscriberCount") Long subscriberCount`
    - File: `src/main/java/com/samuel/app/platform/dto/YouTubeChannelResponse.java`

- [ ] **Task 5: Create YouTubeConnectionService (AC: 1, 2, 3, 4, 5)**
  - [ ] Create `com.samuel.app.platform.service.YouTubeConnectionService`:
    - `@Service` with constructor injection: `YouTubeProperties`, `StringRedisTemplate`, `RestTemplate`, `PlatformConnectionRepository`, `TokenEncryptionService`, `CreatorProfileRepository`
    - **`getAuthorizationUrl(String userId)`**:
      1. Generate UUID state token
      2. Store `"oauth:yt:state:{state}" → userId` in Redis with 10-minute TTL
      3. Build Google OAuth authorization URL:
         - Base: `https://accounts.google.com/o/oauth2/v2/auth`
         - Params: `client_id`, `redirect_uri`, `response_type=code`, `scope=https://www.googleapis.com/auth/youtube.readonly%20https://www.googleapis.com/auth/yt-analytics.readonly`, `access_type=offline`, `prompt=consent`, `state={state}`
         - Use `UriComponentsBuilder` from Spring Web to construct URL safely
      4. Return `YouTubeAuthUrlResponse(authorizationUrl)`
    - **`handleCallback(String code, String state)`**:
      1. Validate state: look up `"oauth:yt:state:{state}"` in Redis; throw `PlatformConnectionException(YOUTUBE, "Invalid or expired OAuth state")` if missing
      2. Delete state key from Redis (prevent replay)
      3. Retrieve `userId` from Redis value
      4. Find creator profile by userId using `CreatorProfileRepository.findByUserId(userId)` — throw `ResourceNotFoundException` if missing
      5. Exchange authorization code for tokens: POST to `https://oauth2.googleapis.com/token` with `code`, `client_id`, `client_secret`, `redirect_uri`, `grant_type=authorization_code` using `RestTemplate.postForObject`
      6. Fetch channel info: GET `https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true` with `Authorization: Bearer {accessToken}` header
      7. Extract channel name (`items[0].snippet.title`), channel ID (`items[0].id`), subscriber count (`items[0].statistics.subscriberCount`)
      8. Encrypt access token and refresh token using `TokenEncryptionService.encrypt()`
      9. Find or create `PlatformConnection` for this creator + YOUTUBE using `PlatformConnectionRepository.findByCreatorProfileIdAndPlatformType()`
      10. Set: `platformUserId = channelId`, `platformName = channelTitle`, `followerCount = subscriberCount`, `status = CONNECTED`, `accessTokenEncrypted = encrypted`, `refreshTokenEncrypted = encrypted`, `tokenExpiresAt = now + expiresIn seconds`, `lastSyncAt = now`
      11. Generate UUID id if new connection; save via repository
      12. Return `PlatformConnectionResponse` from the saved entity
    - **`getConnectionStatus(String creatorProfileId)`**:
      1. Find connection via `PlatformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.YOUTUBE)`
      2. If not found, return `PlatformConnectionResponse` with status `DISCONNECTED`, all other fields null
      3. Otherwise map entity to `PlatformConnectionResponse` and return
    - **`disconnectYouTube(String creatorProfileId)`**:
      1. Find connection — throw `ResourceNotFoundException` if not found
      2. Set `status = DISCONNECTED`, `accessTokenEncrypted = null`, `refreshTokenEncrypted = null`, `tokenExpiresAt = null`
      3. Save and return updated `PlatformConnectionResponse`
    - File: `src/main/java/com/samuel/app/platform/service/YouTubeConnectionService.java`

- [ ] **Task 6: Create YouTubeAdapter implementing IPlatformAdapter (AC: 6, 7)**
  - [ ] Create `com.samuel.app.platform.adapter.YouTubeAdapter` implementing `IPlatformAdapter`:
    - `@Component("youtubeAdapter")`
    - Constructor injection: `CircuitBreakerRegistry`, `RateLimiterRegistry`, `PlatformConnectionRepository`, `TokenEncryptionService`, `RestTemplate`
    - **Class-level annotation**: `@CircuitBreaker(name = "youtube-api", fallbackMethod = "connectFallback")` applied at method level (see note below)
    - **`connect(CreatorProfile creator, PlatformCredentials creds)`**:
      - Annotated: `@CircuitBreaker(name = "youtube-api", fallbackMethod = "connectFallback")`, `@RateLimiter(name = "youtube-api")`, `@Retry(name = "youtube-api")`
      - IMPORTANT: This adapter's `connect()` is primarily wired for circuit breaker test validation. The actual OAuth flow (code exchange) is handled by `YouTubeConnectionService`. This method validates that an existing connection is healthy and can retrieve basic channel data.
      - Uses decrypted access token from `PlatformConnection` to call YouTube API and verify channel access
      - Fallback: `connectFallback(CreatorProfile, PlatformCredentials, Exception)` → throw `PlatformConnectionException(YOUTUBE, "YouTube API unavailable - circuit breaker open")`
    - **`getConnectionStatus()`**:
      - Check circuit breaker state via `circuitBreakerRegistry.circuitBreaker("youtube-api").getState()`
      - Map: `CLOSED` → `CONNECTED`, `OPEN` → `CIRCUIT_OPEN`, `HALF_OPEN` → `CIRCUIT_OPEN`, `DISABLED` → `DISCONNECTED`, `FORCED_OPEN` → `CIRCUIT_OPEN`
      - NOTE: This reflects circuit breaker state only, not DB connection status. Controller layer should check both.
    - **`getRemainingQuota()`**:
      - Get remaining permissions from `rateLimiterRegistry.rateLimiter("youtube-api").getMetrics().getAvailablePermissions()`
      - YouTube Data API v3 quota: 10,000 units/day. Return `RateLimitInfo(availablePermissions, 10000, resetAt, PlatformType.YOUTUBE)` where `resetAt` is next midnight UTC
    - **`getNextResetTime()`**:
      - Return next midnight UTC: `LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay()`
    - **`fetchMetrics(String platformUserId)`**:
      - Annotated: `@CircuitBreaker(name = "youtube-api", fallbackMethod = "fetchMetricsFallback")`, `@RateLimiter(name = "youtube-api")`, `@Retry(name = "youtube-api")`
      - GET `https://www.googleapis.com/youtube/v3/channels?part=statistics&id={platformUserId}` with Bearer token
      - Map to `ContentMetrics` record
      - Fallback: `fetchMetricsFallback(String, Exception)` → throw `PlatformApiException(YOUTUBE, 503, "CIRCUIT_OPEN", true)`
    - **`getRevenueData(String platformUserId, DateRange range)`**:
      - Annotated: `@CircuitBreaker(name = "youtube-api", fallbackMethod = "getRevenueDataFallback")`, `@RateLimiter(name = "youtube-api")`, `@Retry(name = "youtube-api")`
      - For MVP: YouTube Analytics API requires OAuth scope `yt-analytics.readonly`
      - GET `https://youtubeanalytics.googleapis.com/v2/reports?ids=channel=={channelId}&startDate={startDate}&endDate={endDate}&metrics=estimatedRevenue&dimensions=day`
      - If response has no data, return `Optional.empty()`
      - Fallback: `getRevenueDataFallback(String, DateRange, Exception)` → throw `PlatformApiException(YOUTUBE, 503, "CIRCUIT_OPEN", true)`
    - **`getPlatformType()`**: return `PlatformType.YOUTUBE`
    - File: `src/main/java/com/samuel/app/platform/adapter/YouTubeAdapter.java`

- [ ] **Task 7: Create PlatformConnectionController (AC: 1, 2, 3, 4)**
  - [ ] Create `com.samuel.app.platform.controller.PlatformConnectionController`:
    - `@RestController @RequestMapping("/platforms")`
    - Constructor injection: `YouTubeConnectionService`, `AuthenticationHelper` (existing in `com.samuel.app.shared.filter` or create per project-context.md pattern — check actual location)
    - `GET /platforms/youtube/auth/url` → `@PreAuthorize` authenticated only:
      - Get current userId via `SecurityContextHolder.getContext().getAuthentication().getName()`
      - Actually: follow project pattern — check existing controllers for how they get userId from SecurityContext
      - Call `youTubeConnectionService.getAuthorizationUrl(userId)`
      - Return `ResponseEntity.ok(ApiResponse.success(response))`
    - `GET /platforms/youtube/callback?code=...&state=...` → NOT authenticated (OAuth redirect):
      - `@RequestParam String code, @RequestParam String state`
      - Call `youTubeConnectionService.handleCallback(code, state)`
      - Return `ResponseEntity.ok(ApiResponse.success(response))`
    - `GET /platforms/youtube/connection` → authenticated:
      - Get current creatorProfileId from security context
      - Call `youTubeConnectionService.getConnectionStatus(creatorProfileId)`
      - Return `ResponseEntity.ok(ApiResponse.success(response))`
    - `DELETE /platforms/youtube/disconnect` → authenticated:
      - Call `youTubeConnectionService.disconnectYouTube(creatorProfileId)`
      - Return `ResponseEntity.ok(ApiResponse.success(response))`
    - File: `src/main/java/com/samuel/app/platform/controller/PlatformConnectionController.java`

- [ ] **Task 8: Update SecurityConfig to permit OAuth callback URL (AC: 2)**
  - [ ] Modify `src/main/java/com/samuel/app/config/SecurityConfig.java`:
    - Add to `requestMatchers(...).permitAll()`: `"/platforms/youtube/callback"`
    - IMPORTANT: Context path is `/api/v1` in application.yml — Spring Security matches AFTER context path is stripped. So permit `/platforms/youtube/callback` (not `/api/v1/platforms/youtube/callback`)
    - File: `src/main/java/com/samuel/app/config/SecurityConfig.java` (modify existing)

- [ ] **Task 9: Verify Creator Profile lookup pattern and fix if needed (AC: 2)**
  - [ ] Verify `CreatorProfileRepository` has a method to find by `userId` (not `creatorProfileId`):
    - Check `src/main/java/com/samuel/app/creator/repository/CreatorProfileRepository.java`
    - If `findByUserId(String userId)` doesn't exist, add it
    - The `handleCallback()` method needs to map `userId` (from Redis state) to `creatorProfileId` (for `PlatformConnection`)
    - Check how existing `DashboardService` or `CreatorProfileController` retrieves creatorProfile for logged-in user

- [ ] **Task 10: Write unit tests (AC: 1-7)**
  - [ ] Create `com.samuel.app.platform.service.TokenEncryptionServiceTest`:
    - `@ExtendWith(MockitoExtension.class)` — no Spring context
    - Tests: `should_encrypt_and_decrypt_when_valid_plaintext_then_round_trip_succeeds()`
    - Tests: `should_throw_when_decrypt_invalid_ciphertext()`
    - Tests: `should_produce_different_ciphertext_when_same_plaintext_encrypted_twice()` (IV randomness)
    - File: `src/test/java/com/samuel/app/platform/service/TokenEncryptionServiceTest.java`
  - [ ] Create `com.samuel.app.platform.service.YouTubeConnectionServiceTest`:
    - `@ExtendWith(MockitoExtension.class)`
    - Mocks: `YouTubeProperties`, `StringRedisTemplate`, `RestTemplate`, `PlatformConnectionRepository`, `TokenEncryptionService`, `CreatorProfileRepository`
    - Tests for `getAuthorizationUrl()`: verifies state stored in Redis, returns URL with expected scopes
    - Tests for `handleCallback()`:
      - Happy path: valid state, tokens exchanged, channel info fetched, connection saved
      - Invalid state: `PlatformConnectionException` thrown
      - Expired state (Redis returns null): `PlatformConnectionException` thrown
    - Tests for `getConnectionStatus()`: connected case, disconnected case, not found case
    - Tests for `disconnectYouTube()`: success case, not found throws `ResourceNotFoundException`
    - File: `src/test/java/com/samuel/app/platform/service/YouTubeConnectionServiceTest.java`
  - [ ] Create `com.samuel.app.platform.adapter.YouTubeAdapterTest`:
    - `@ExtendWith(MockitoExtension.class)`
    - Tests: `should_return_circuit_open_when_circuit_breaker_state_is_open()`
    - Tests: `should_return_connected_when_circuit_breaker_closed()`
    - Tests: `should_return_youtube_platform_type()`
    - Tests for `getRemainingQuota()`: verifies `RateLimitInfo` fields
    - Tests for circuit breaker failure (fallback invoked): verify `PlatformApiException` thrown
    - File: `src/test/java/com/samuel/app/platform/adapter/YouTubeAdapterTest.java`
  - [ ] Create `com.samuel.app.platform.controller.PlatformConnectionControllerTest`:
    - `@ExtendWith(MockitoExtension.class)` with `@WebMvcTest` or pure unit test with `MockMvcBuilders.standaloneSetup()`
    - Tests: `should_return_auth_url_when_authenticated_user_requests_youtube_auth()`
    - Tests: `should_return_connection_status_when_authenticated_user_requests_status()`
    - Tests: `should_return_disconnected_when_authenticated_user_disconnects()`
    - Note: callback endpoint testing is complex (no auth) — test with MockMvc, verify `state` validation delegates to service
    - File: `src/test/java/com/samuel/app/platform/controller/PlatformConnectionControllerTest.java`

## Dev Notes

### Critical: How YouTube OAuth 2.0 Flow Works in This Architecture

Since this project uses stateless JWT authentication (no sessions), the YouTube OAuth CSRF state is stored in **Redis** (not HTTP session). The flow:

```
Creator (authenticated) → GET /platforms/youtube/auth/url
  → service generates UUID state
  → Redis: SET "oauth:yt:state:{state}" "{userId}" EX 600
  → returns Google OAuth URL with state param

Creator → redirected to Google consent screen
Google → GET /platforms/youtube/callback?code=ABC&state={state}
  → controller receives (NOT authenticated - no JWT in redirect)
  → service: Redis GET "oauth:yt:state:{state}" → gets userId
  → Redis: DEL "oauth:yt:state:{state}" (prevent replay)
  → exchange code for tokens
  → fetch channel info
  → store encrypted tokens in DB
```

**Redis Key Pattern** (follow `AuthService` convention):
```java
String stateKey = "oauth:yt:state:" + state;
redisTemplate.opsForValue().set(stateKey, userId, Duration.ofMinutes(10));
String userId = redisTemplate.opsForValue().get(stateKey);
redisTemplate.delete(stateKey);
```

### Token Encryption: AES-256-GCM Implementation

No new library needed — use Java 17 built-in `javax.crypto`:

```java
// Encrypt
byte[] iv = new byte[12]; // 12 bytes for GCM
new SecureRandom().nextBytes(iv);
SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES"); // keyBytes = 32 bytes
GCMParameterSpec paramSpec = new GCMParameterSpec(128, iv); // 128-bit tag
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec);
byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
// Combine IV + ciphertext (cipherText already includes the 16-byte GCM auth tag appended by Java)
byte[] combined = new byte[iv.length + cipherText.length];
System.arraycopy(iv, 0, combined, 0, iv.length);
System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
return Base64.getEncoder().encodeToString(combined);

// Decrypt (reverse process):
byte[] combined = Base64.getDecoder().decode(encryptedBase64);
byte[] iv = Arrays.copyOfRange(combined, 0, 12);
byte[] cipherText = Arrays.copyOfRange(combined, 12, combined.length);
// ... cipher.init(DECRYPT_MODE), cipher.doFinal(cipherText)
```

**Key loading from base64 env var:**
```java
@Service
public class TokenEncryptionService {
    private final byte[] keyBytes;
    
    public TokenEncryptionService(@Value("${platform.token-encryption-key}") String keyBase64) {
        this.keyBytes = Base64.getDecoder().decode(keyBase64);
        if (this.keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 32 bytes (256 bits)");
        }
    }
}
```

### YouTube API Endpoints Used

| Purpose | Method | URL | Auth |
|---------|--------|-----|------|
| Token exchange | POST | `https://oauth2.googleapis.com/token` | None (client creds in body) |
| Channel info | GET | `https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true` | Bearer token |
| Channel metrics by ID | GET | `https://www.googleapis.com/youtube/v3/channels?part=statistics&id={channelId}` | Bearer token |
| YouTube Analytics | GET | `https://youtubeanalytics.googleapis.com/v2/reports?ids=channel=={channelId}&metrics=estimatedRevenue&startDate=...&endDate=...` | Bearer token |

### Token Exchange Request (RestTemplate pattern)

```java
// Use MultiValueMap for form-encoded POST body (Google token endpoint requires application/x-www-form-urlencoded)
MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
params.add("code", authorizationCode);
params.add("client_id", youtubeProperties.getClientId());
params.add("client_secret", youtubeProperties.getClientSecret());
params.add("redirect_uri", youtubeProperties.getRedirectUri());
params.add("grant_type", "authorization_code");

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

YouTubeTokenResponse tokenResponse = restTemplate.postForObject(
    "https://oauth2.googleapis.com/token",
    request,
    YouTubeTokenResponse.class
);
```

### Fetching Channel Info (RestTemplate pattern)

```java
HttpHeaders headers = new HttpHeaders();
headers.setBearerAuth(accessToken);
HttpEntity<Void> request = new HttpEntity<>(headers);

ResponseEntity<YouTubeChannelResponse> response = restTemplate.exchange(
    "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true",
    HttpMethod.GET,
    request,
    YouTubeChannelResponse.class
);
```

### UriComponentsBuilder for Auth URL

```java
String authUrl = UriComponentsBuilder
    .fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
    .queryParam("client_id", youtubeProperties.getClientId())
    .queryParam("redirect_uri", youtubeProperties.getRedirectUri())
    .queryParam("response_type", "code")
    .queryParam("scope", "https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/yt-analytics.readonly")
    .queryParam("access_type", "offline")
    .queryParam("prompt", "consent") // Force refresh token on re-auth
    .queryParam("state", state)
    .build()
    .toUriString();
```

### CreatorProfile → UserId Mapping

In `handleCallback()`, the Redis state value is the `userId` (from JWT subject, which is the User's email or User ID). Check how existing controllers/services retrieve the creator profile for a logged-in user:
- `DashboardController` calls `creatorProfileService.getProfile(userId)` where userId comes from `SecurityContextHolder`
- `CreatorProfileRepository` should have `findByUserId(String userId)` — verify this exists before writing `YouTubeConnectionService`
- The `PlatformConnection` entity uses `creatorProfileId` (the UUID primary key of `creator_profiles` table), NOT `userId` (the UUID from `users` table)

**IMPORTANT**: Do NOT store userId in `platform_connections.creator_profile_id` — it must be the UUID from the `creator_profiles` table.

### Getting Current User in Controller

Follow the pattern used in existing controllers (check `CreatorProfileController` for exact approach):

```java
// DO NOT access SecurityContext directly in services (per project-context.md)
// In controller, get username from authentication:
String userId = authentication.getName(); // JWT subject = user ID (UUID)
// OR with @AuthenticationPrincipal:
// public ResponseEntity<...> endpoint(@AuthenticationPrincipal UserDetails userDetails) { ... }
```

Check `CreatorProfileController` to see which exact approach the codebase uses before implementing.

### CircuitBreaker Annotation Position

Per `project-context.md`, `@CircuitBreaker`, `@RateLimiter`, `@Retry` are **method-level** annotations (not class-level) in this codebase pattern:

```java
@Component("youtubeAdapter")
public class YouTubeAdapter implements IPlatformAdapter {
    
    @Override
    @CircuitBreaker(name = "youtube-api", fallbackMethod = "connectFallback")
    @RateLimiter(name = "youtube-api")
    @Retry(name = "youtube-api")
    public PlatformConnection connect(CreatorProfile creator, PlatformCredentials creds) {
        // implementation
    }
    
    // Fallback must have same parameters PLUS the Exception:
    public PlatformConnection connectFallback(CreatorProfile creator, PlatformCredentials creds, Exception ex) {
        throw new PlatformConnectionException(PlatformType.YOUTUBE, "YouTube API unavailable: " + ex.getMessage());
    }
}
```

**FALLBACK SIGNATURE RULE**: The fallback method MUST have the exact same parameters as the annotated method, plus an additional `Exception` (or specific subclass) parameter as the last argument. Fallback must be in the **same class**.

### No New Flyway Migration Needed

The `V5__create_platform_connections_table.sql` (from Story 2.1) already has all required columns:
- `access_token_encrypted TEXT` — stores encrypted YouTube access token
- `refresh_token_encrypted TEXT` — stores encrypted YouTube refresh token
- `platform_user_id VARCHAR(255)` — stores YouTube channel ID
- `platform_name VARCHAR(255)` — stores YouTube channel name
- `follower_count BIGINT` — stores subscriber count
- `token_expires_at DATETIME(6)` — stores token expiry
- `last_sync_at DATETIME(6)` — updated on each sync

**DO NOT create a V6 migration in this story** unless a schema change is truly needed. V6 is reserved for future stories.

### Security Config: Callback URL Permit

Add to `SecurityConfig.securityFilterChain()`:
```java
.authorizeHttpRequests(authorizeRequests -> authorizeRequests
    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
    .requestMatchers("/auth/**").permitAll()
    .requestMatchers("/platforms/youtube/callback").permitAll()  // ← ADD THIS
    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
    .anyRequest().authenticated()
)
```

**Context path `/api/v1` is handled by `server.servlet.context-path` in `application.yml` — Spring Security's `requestMatchers` matches the path AFTER the context path is stripped.**

### Existing Source Structure (what exists after Story 2.1)

```
src/main/java/com/samuel/app/
├── AppApplication.java                         ← Add @EnableConfigurationProperties if needed
├── config/
│   ├── CacheConfig.java                        ← DO NOT TOUCH
│   ├── SecurityConfig.java                     ← MODIFY: add callback URL to permitAll
│   └── Resilience4jConfig.java                 ← DO NOT TOUCH
├── creator/
│   ├── controller/  (AuthController, CreatorProfileController, DashboardController)
│   ├── dto/         (existing DTOs)
│   ├── model/       (CreatorProfile, EmailVerification, User)
│   ├── repository/  (CreatorProfileRepository, UserRepository, EmailVerificationRepository)
│   └── service/     (AuthService, UserRegistrationService, CreatorProfileService, etc.)
├── platform/
│   ├── adapter/
│   │   ├── ConnectionStatus.java               ← DO NOT TOUCH (established in 2.1)
│   │   ├── IPlatformAdapter.java               ← DO NOT TOUCH
│   │   ├── PlatformType.java                   ← DO NOT TOUCH
│   │   └── [NEW] YouTubeAdapter.java           ← Task 6
│   ├── config/                                 ← NEW package
│   │   ├── [NEW] YouTubeProperties.java        ← Task 1
│   │   └── [NEW] PlatformWebConfig.java        ← Task 3
│   ├── controller/                             ← NEW package
│   │   └── [NEW] PlatformConnectionController.java  ← Task 7
│   ├── dto/
│   │   ├── (existing DTOs from 2.1)            ← DO NOT TOUCH
│   │   ├── [NEW] YouTubeAuthUrlResponse.java   ← Task 4
│   │   ├── [NEW] PlatformConnectionResponse.java  ← Task 4
│   │   ├── [NEW] YouTubeTokenResponse.java     ← Task 4
│   │   └── [NEW] YouTubeChannelResponse.java   ← Task 4
│   ├── exception/   (4 exception classes)      ← DO NOT TOUCH
│   ├── model/
│   │   └── PlatformConnection.java             ← DO NOT TOUCH (do NOT change schema)
│   ├── repository/
│   │   └── PlatformConnectionRepository.java   ← DO NOT TOUCH (unless findByUserId missing)
│   └── service/                                ← NEW package (first service in platform module)
│       ├── [NEW] TokenEncryptionService.java   ← Task 2
│       └── [NEW] YouTubeConnectionService.java ← Task 5
├── shared/
│   ├── controller/  (ApiResponse, GlobalExceptionHandler)  ← DO NOT TOUCH
│   ├── filter/      (CorrelationIdFilter, JwtAuthenticationFilter)  ← DO NOT TOUCH
│   ├── service/     (EmailService)             ← DO NOT TOUCH
│   └── util/        (JwtUtil)                  ← DO NOT TOUCH
```

### Deferred Work — Do NOT Fix in This Story

These are from `deferred-work.md` and previous code reviews — do NOT address in Story 2.2:
- Redis auto-config not disabled in test profile (Story 1.1 issue)
- User entity missing created_at/updated_at mapping
- CORS allowedOrigins hardcoded
- TOCTOU race in verifyEmail
- Old profile image files not deleted on re-upload
- Token rotation not atomic in AuthService
- `handleRuntimeException` fragile string matching

### Previous Story Learnings (from Story 2.1 Dev Agent Record)

- **Resilience4j is now in pom.xml** — `resilience4j-spring-boot3:2.2.0`, `spring-boot-starter-aop`, `resilience4j-micrometer:2.2.0` all present. Do NOT re-add them.
- **Circuit breaker config already in application-dev.yml** — `youtube-api` instances for circuitbreaker, ratelimiter, and retry are all configured. Do NOT duplicate.
- **No TenantAwareEntity** — `PlatformConnection` entity does not extend it. Do NOT introduce it.
- **H2 test database** — `flyway.enabled: false` in `application-test.yml`, H2 with `ddl-auto: create-drop`. New entity `YouTubeAdapter` is not an entity, so no impact. New `@ConfigurationProperties` class needs `@EnableConfigurationProperties` in test context or use `@TestPropertySource`.
- **No `@SpringBootTest` for unit tests** — use `@ExtendWith(MockitoExtension.class)` for all unit tests. Integration tests are noted as "known limitations" in this project.
- **ApiResponse wrapper** — all controllers return `ApiResponse<T>` from `com.samuel.app.shared.controller.ApiResponse`. Use `.success()` and `.error()` factory methods.
- **GlobalExceptionHandler already handles PlatformConnectionException** — returns HTTP 503 with `PLATFORM_CONNECTION_ERROR`. The callback endpoint will propagate correctly.
- **Test application-test.yml** — if `YouTubeProperties` is needed in test context, mock it or add `@TestPropertySource(properties = {"youtube.client-id=test", ...})`.

### Test Context: How to Handle @ConfigurationProperties in Unit Tests

Since unit tests use `@ExtendWith(MockitoExtension.class)` and NOT Spring context:

```java
// In YouTubeConnectionServiceTest — construct YouTubeProperties directly:
@Mock private YouTubeProperties youtubeProperties;

@BeforeEach
void setUp() {
    when(youtubeProperties.getClientId()).thenReturn("test-client-id");
    when(youtubeProperties.getClientSecret()).thenReturn("test-secret");
    when(youtubeProperties.getRedirectUri()).thenReturn("http://localhost/callback");
    
    service = new YouTubeConnectionService(
        youtubeProperties, redisTemplate, restTemplate, 
        platformConnectionRepository, tokenEncryptionService, creatorProfileRepository
    );
}
```

For `TokenEncryptionService`, pass a test key directly:
```java
// Valid 32-byte base64 key for testing:
private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);
TokenEncryptionService service = new TokenEncryptionService(TEST_KEY);
```

### API Response Patterns — Follow Exactly

```java
// Success (200):
return ResponseEntity.ok(ApiResponse.success(responseDto));

// Error is handled by GlobalExceptionHandler — do NOT manually return error responses
// Just throw the appropriate exception (PlatformConnectionException, ResourceNotFoundException, etc.)
```

### RestTemplate Bean — Check if Conflict With Existing

Before creating `PlatformWebConfig` with `RestTemplate` bean, verify no existing `RestTemplate` bean exists in the codebase (unlikely but verify). The `CacheConfig`, `SecurityConfig`, and `Resilience4jConfig` don't define RestTemplate. `EmailService` uses `JavaMailSender`, not RestTemplate. Safe to create new bean.

### Project Structure Notes

- **Package `platform/service/`** is new in this story — Story 2.1 only created `platform/adapter/`, `platform/dto/`, `platform/exception/`, `platform/model/`, `platform/repository/`
- **Package `platform/controller/`** is new in this story
- **Package `platform/config/`** is new in this story
- All new packages follow the `com.samuel.app.platform.{layer}` convention established in Story 2.1
- `YouTubeAdapter` resides in `platform/adapter/` (singular), consistent with existing `IPlatformAdapter`, `ConnectionStatus`, `PlatformType`

### References

- [Source: epics.md#Story 2.2] — Acceptance criteria and user story
- [Source: epics.md#Epic 2] — Epic context (Platform Integration & Connections)
- [Source: architecture.md#Enhanced Platform Integration Patterns] — YouTubeAdapter pattern with @CircuitBreaker, @RateLimiter
- [Source: architecture.md#Implementation Patterns & Consistency Rules] — Naming, structure, anti-patterns
- [Source: project-context.md#Circuit Breaker Implementation Patterns] — Required annotations and fallback pattern
- [Source: project-context.md#Test Coverage Requirements] — 100% line coverage, naming conventions
- [Source: 2-1-platform-adapter-interface-and-circuit-breaker-infrastructure.md#Dev Notes] — Previous story learnings, existing files, do-not-modify list
- [Source: 2-1-platform-adapter-interface-and-circuit-breaker-infrastructure.md#File List] — Comprehensive list of what story 2.1 created
- [Source: SecurityConfig.java] — Current permitAll rules (add callback URL)
- [Source: AuthService.java] — StringRedisTemplate usage pattern (redis key format, TTL pattern)
- [Source: application-dev.yml] — Existing Resilience4j config for youtube-api (already configured, do not duplicate)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.6 (GitHub Copilot)

### Debug Log References

### Completion Notes List

### File List
