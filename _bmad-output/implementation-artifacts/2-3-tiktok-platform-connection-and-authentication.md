# Story 2.3: TikTok Platform Connection and Authentication

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a creator,
I want to connect my TikTok account to the platform,
so that I can manage my TikTok content and track performance metrics.

## Acceptance Criteria

1. **AC1 — OAuth authorization URL generated for TikTok**
   **Given** I am an authenticated creator on the platform
   **When** I request a TikTok OAuth authorization URL via `GET /platforms/tiktok/auth/url`
   **Then** I receive a TikTok OAuth 2.0 authorization URL pointing to `https://www.tiktok.com/v2/auth/authorize/`
   **And** the URL includes a CSRF state token (UUID) stored in Redis with 10-minute TTL
   **And** the URL includes scopes `user.info.basic,user.info.profile,user.info.stats`
   **And** only authenticated users can call this endpoint

2. **AC2 — OAuth callback exchanges code and stores connection**
   **Given** I have granted TikTok permissions on the TikTok consent screen
   **When** TikTok redirects to `GET /platforms/tiktok/callback?code=...&state=...`
   **Then** the state parameter is validated against Redis (CSRF protection)
   **And** the authorization code is exchanged for access and refresh tokens via `https://open.tiktokapis.com/v2/oauth/token/`
   **And** my TikTok profile information (open_id, display_name, follower_count) is fetched from TikTok User Info API
   **And** the connection is stored in the `platform_connections` table with status `CONNECTED`
   **And** access and refresh tokens are AES-256-GCM encrypted before storage using existing `TokenEncryptionService`

3. **AC3 — Connection status reflects CONNECTED with TikTok profile details**
   **Given** I have successfully connected my TikTok account
   **When** I request my TikTok connection status via `GET /platforms/tiktok/connection`
   **Then** I receive the connection status as `CONNECTED`
   **And** the response includes my TikTok display name, follower count, and last sync timestamp

4. **AC4 — TikTok account can be disconnected**
   **Given** I have a connected TikTok account
   **When** I request disconnection via `DELETE /platforms/tiktok/disconnect`
   **Then** the platform connection status is set to `DISCONNECTED`
   **And** the encrypted tokens are cleared from the database
   **And** the response confirms the disconnection

5. **AC5 — TikTok API credentials are encrypted at rest**
   **Given** the token exchange produces an access token and refresh token
   **When** the tokens are persisted to `platform_connections.access_token_encrypted` and `platform_connections.refresh_token_encrypted`
   **Then** tokens are encrypted using the existing `TokenEncryptionService` (AES-256-GCM)
   **And** the same `${PLATFORM_TOKEN_ENCRYPTION_KEY}` env var used for YouTube is reused

6. **AC6 — Connection health monitored via circuit breaker**
   **Given** the `TikTokAdapter` methods are annotated with `@CircuitBreaker(name = "tiktok-api")`
   **When** the circuit breaker is OPEN (TikTok API unreachable)
   **Then** `getConnectionStatus()` returns `CIRCUIT_OPEN`
   **And** circuit breaker state is visible at `/actuator/health` (already configured from Story 2.1)

7. **AC7 — Circuit breaker opens on TikTok API failures**
   **Given** TikTok API is repeatedly failing with `PlatformApiException`
   **When** the failure rate exceeds 50% over the last 10 calls (inherited from Story 2.1 default config)
   **Then** the circuit breaker transitions to OPEN state
   **And** subsequent calls to `TikTokAdapter` immediately throw `PlatformConnectionException` without hitting the API
   **And** the platform connection status reflects `CIRCUIT_OPEN`

## Tasks / Subtasks

- [ ] **Task 1: Add TikTok OAuth configuration properties (AC: 1, 2, 5)**
  - [ ] Create `com.samuel.app.platform.config.TikTokProperties` using `@ConfigurationProperties(prefix = "tiktok")`:
    - `String clientKey` (from `${tiktok.client-key}`) — NOTE: TikTok calls it "client_key", NOT "client_id"
    - `String clientSecret` (from `${tiktok.client-secret}`)
    - `String redirectUri` (from `${tiktok.redirect-uri}`)
    - Annotate class with `@Configuration` and `@ConfigurationProperties(prefix = "tiktok")`
    - File: `src/main/java/com/samuel/app/platform/config/TikTokProperties.java`
  - [ ] Add to `application-dev.yml` under `tiktok:` section (append after existing `youtube:` section):
    ```yaml
    tiktok:
      client-key: ${TIKTOK_CLIENT_KEY:your-tiktok-client-key}
      client-secret: ${TIKTOK_CLIENT_SECRET:your-tiktok-client-secret}
      redirect-uri: ${TIKTOK_REDIRECT_URI:http://localhost:8080/api/v1/platforms/tiktok/callback}
    ```
    Note: `platform.token-encryption-key` was already added in Story 2.2 — DO NOT re-add it.

- [ ] **Task 2: Create TikTok-specific DTOs (AC: 1, 2, 3)**
  - [ ] Create `com.samuel.app.platform.dto.TikTokAuthUrlResponse` record:
    - `String authorizationUrl`
    - File: `src/main/java/com/samuel/app/platform/dto/TikTokAuthUrlResponse.java`
  - [ ] Create `com.samuel.app.platform.dto.TikTokTokenResponse` record (internal, maps TikTok token endpoint JSON):
    - `@JsonProperty("access_token") String accessToken`
    - `@JsonProperty("refresh_token") String refreshToken`
    - `@JsonProperty("expires_in") long expiresIn`
    - `@JsonProperty("refresh_expires_in") long refreshExpiresIn`
    - `@JsonProperty("open_id") String openId`
    - `@JsonProperty("scope") String scope`
    - `@JsonProperty("token_type") String tokenType`
    - File: `src/main/java/com/samuel/app/platform/dto/TikTokTokenResponse.java`
  - [ ] Create `com.samuel.app.platform.dto.TikTokUserInfoResponse` record (internal, maps TikTok User Info API response):
    - Nested structure reflecting TikTok's `{ "data": { "user": { ... } } }` format:
      - Top-level record: `Data data`
      - `Data` record: `User user`
      - `User` record: `@JsonProperty("open_id") String openId`, `@JsonProperty("display_name") String displayName`, `@JsonProperty("follower_count") Long followerCount`
    - File: `src/main/java/com/samuel/app/platform/dto/TikTokUserInfoResponse.java`
  - Note: Reuse existing `PlatformConnectionResponse` for all response DTOs — DO NOT create a new one.

- [ ] **Task 3: Create TikTokConnectionService (AC: 1, 2, 3, 4, 5)**
  - [ ] Create `com.samuel.app.platform.service.TikTokConnectionService`:
    - `@Service` with constructor injection: `TikTokProperties`, `StringRedisTemplate`, `RestTemplate`, `PlatformConnectionRepository`, `TokenEncryptionService`, `CreatorProfileRepository`
    - **`getAuthorizationUrl(String userId)`**:
      1. Generate UUID state token
      2. Store `"oauth:tt:state:{state}" → userId` in Redis with 10-minute TTL
         - Key prefix is `oauth:tt:state:` (consistent with `oauth:yt:state:` pattern from Story 2.2)
      3. Build TikTok OAuth authorization URL:
         - Base: `https://www.tiktok.com/v2/auth/authorize/`
         - Params: `client_key={clientKey}`, `redirect_uri={redirectUri}` (URL-encoded), `response_type=code`, `scope=user.info.basic,user.info.profile,user.info.stats`, `state={state}`
         - Use `UriComponentsBuilder` from Spring Web — same pattern as `YouTubeConnectionService`
         - **IMPORTANT**: TikTok uses `client_key` parameter name (not `client_id`)
      4. Return `TikTokAuthUrlResponse(authorizationUrl)`
    - **`handleCallback(String code, String state)`**:
      1. Validate state: look up `"oauth:tt:state:{state}"` in Redis; throw `PlatformConnectionException(TIKTOK, "Invalid or expired OAuth state")` if missing
      2. Delete state key from Redis (prevent replay)
      3. Retrieve `userId` from Redis value
      4. Find creator profile: `CreatorProfileRepository.findByUserId(userId)` — throw `ResourceNotFoundException` if missing
      5. Exchange authorization code for tokens: POST to `https://open.tiktokapis.com/v2/oauth/token/`
         - Request body (form-encoded `application/x-www-form-urlencoded`): `client_key`, `client_secret`, `code`, `grant_type=authorization_code`, `redirect_uri`
         - **IMPORTANT**: TikTok token endpoint uses `client_key` NOT `client_id`
         - Use `RestTemplate.postForObject` with `MultiValueMap<String, String>` same pattern as YouTube
         - Map response to `TikTokTokenResponse`
      6. Fetch user info: GET `https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,follower_count`
         - Header: `Authorization: Bearer {accessToken}`
         - Map response to `TikTokUserInfoResponse`
         - Extract: `openId = response.data().user().openId()`, `displayName = response.data().user().displayName()`, `followerCount = response.data().user().followerCount()`
      7. Encrypt access token and refresh token using `TokenEncryptionService.encrypt()`
      8. Find or create `PlatformConnection` for this creator + TIKTOK using `PlatformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.TIKTOK)`
      9. Set: `platformUserId = openId`, `platformName = displayName`, `followerCount`, `status = CONNECTED`, `accessTokenEncrypted`, `refreshTokenEncrypted`, `tokenExpiresAt = now + expiresIn seconds`, `lastSyncAt = now`
      10. Generate UUID id if new connection; save via repository
      11. Return `PlatformConnectionResponse` from saved entity
    - **`getConnectionStatus(String creatorProfileId)`**:
      1. Find connection via `PlatformConnectionRepository.findByCreatorProfileIdAndPlatformType(creatorProfileId, PlatformType.TIKTOK)`
      2. If not found, return `PlatformConnectionResponse` with status `DISCONNECTED`, all other fields null
      3. Otherwise map entity to `PlatformConnectionResponse` and return
    - **`disconnectTikTok(String creatorProfileId)`**:
      1. Find connection — throw `ResourceNotFoundException` if not found
      2. Set `status = DISCONNECTED`, `accessTokenEncrypted = null`, `refreshTokenEncrypted = null`, `tokenExpiresAt = null`
      3. Save and return updated `PlatformConnectionResponse`
    - File: `src/main/java/com/samuel/app/platform/service/TikTokConnectionService.java`

- [ ] **Task 4: Create TikTokAdapter implementing IPlatformAdapter (AC: 6, 7)**
  - [ ] Create `com.samuel.app.platform.adapter.TikTokAdapter` implementing `IPlatformAdapter`:
    - `@Component("tiktokAdapter")`
    - Constructor injection: `CircuitBreakerRegistry`, `RateLimiterRegistry`, `PlatformConnectionRepository`, `TokenEncryptionService`, `RestTemplate`
    - **`connect(CreatorProfile creator, PlatformCredentials creds)`**:
      - Annotated: `@CircuitBreaker(name = "tiktok-api", fallbackMethod = "connectFallback")`, `@RateLimiter(name = "tiktok-api")`, `@Retry(name = "tiktok-api")`
      - Validates existing connection is healthy by calling TikTok User Info API with decrypted access token
      - Fallback: `connectFallback(CreatorProfile, PlatformCredentials, Exception)` → throw `PlatformConnectionException(TIKTOK, "TikTok API unavailable - circuit breaker open")`
    - **`getConnectionStatus()`**:
      - Check circuit breaker state via `circuitBreakerRegistry.circuitBreaker("tiktok-api").getState()`
      - Map: `CLOSED` → `CONNECTED`, `OPEN` → `CIRCUIT_OPEN`, `HALF_OPEN` → `CIRCUIT_OPEN`, `DISABLED` → `DISCONNECTED`, `FORCED_OPEN` → `CIRCUIT_OPEN`
      - Mirror pattern from `YouTubeAdapter.getConnectionStatus()`
    - **`getRemainingQuota()`**:
      - Get remaining permissions from `rateLimiterRegistry.rateLimiter("tiktok-api").getMetrics().getAvailablePermissions()`
      - TikTok rate limit: 50 requests per 60s (per `application-dev.yml` config from Story 2.1)
      - Return `RateLimitInfo(availablePermissions, 50, resetAt, PlatformType.TIKTOK)` where `resetAt` = 60 seconds from now
    - **`getNextResetTime()`**:
      - Return `LocalDateTime.now(ZoneOffset.UTC).plusSeconds(60)` (TikTok rate limit window is 60s, not daily like YouTube)
    - **`fetchMetrics(String platformUserId)`**:
      - Annotated: `@CircuitBreaker(name = "tiktok-api", fallbackMethod = "fetchMetricsFallback")`, `@RateLimiter(name = "tiktok-api")`, `@Retry(name = "tiktok-api")`
      - GET `https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,follower_count`
      - Bearer token from decrypted `accessTokenEncrypted` in `PlatformConnection`
      - Map to `ContentMetrics` record
      - Fallback: `fetchMetricsFallback(String, Exception)` → throw `PlatformApiException(TIKTOK, 503, "CIRCUIT_OPEN", true)`
    - **`getRevenueData(String platformUserId, DateRange range)`**:
      - TikTok Creator Fund revenue data is not available via public API at this scope — return `Optional.empty()`
      - Still annotate with circuit breaker for consistency: `@CircuitBreaker(name = "tiktok-api", fallbackMethod = "getRevenueDataFallback")`
      - Fallback: `getRevenueDataFallback(String, DateRange, Exception)` → throw `PlatformApiException(TIKTOK, 503, "CIRCUIT_OPEN", true)`
    - **`getPlatformType()`**: return `PlatformType.TIKTOK`
    - File: `src/main/java/com/samuel/app/platform/adapter/TikTokAdapter.java`

- [ ] **Task 5: Add TikTok endpoints to PlatformConnectionController (AC: 1, 2, 3, 4)**
  - [ ] Modify `src/main/java/com/samuel/app/platform/controller/PlatformConnectionController.java`:
    - Add `TikTokConnectionService` to constructor injection (keep `YouTubeConnectionService` and `CreatorProfileRepository` unchanged)
    - Add `GET /platforms/tiktok/auth/url` — authenticated:
      - Get userId from `SecurityContextHolder.getContext().getAuthentication().getName()` (same pattern as YouTube endpoint)
      - Call `tikTokConnectionService.getAuthorizationUrl(userId)`
      - Return `ResponseEntity.ok(ApiResponse.ok(response))`
    - Add `GET /platforms/tiktok/callback` — NOT authenticated (permitAll):
      - `@RequestParam String code, @RequestParam String state`
      - Call `tikTokConnectionService.handleCallback(code, state)`
      - Return `ResponseEntity.ok(ApiResponse.ok(response))`
    - Add `GET /platforms/tiktok/connection` — authenticated:
      - Call `getCreatorProfileId()` helper (already exists, no change)
      - Call `tikTokConnectionService.getConnectionStatus(creatorProfileId)`
      - Return `ResponseEntity.ok(ApiResponse.ok(response))`
    - Add `DELETE /platforms/tiktok/disconnect` — authenticated:
      - Call `getCreatorProfileId()` helper (already exists, no change)
      - Call `tikTokConnectionService.disconnectTikTok(creatorProfileId)`
      - Return `ResponseEntity.ok(ApiResponse.ok(response))`
    - **DO NOT** change existing YouTube endpoints or `getCreatorProfileId()` helper

- [ ] **Task 6: Update SecurityConfig to permit TikTok OAuth callback URL (AC: 2)**
  - [ ] Modify `src/main/java/com/samuel/app/config/SecurityConfig.java`:
    - Add `/platforms/tiktok/callback` to the `requestMatchers(...).permitAll()` block alongside the existing `/platforms/youtube/callback`
    - Context path `/api/v1` is handled by `server.servlet.context-path` — Spring Security matches AFTER context path is stripped, so permit `/platforms/tiktok/callback` (without `/api/v1` prefix)

- [ ] **Task 7: Write unit tests (AC: 1-7)**
  - [ ] Create `com.samuel.app.platform.service.TikTokConnectionServiceTest`:
    - `@ExtendWith(MockitoExtension.class)` — no Spring context
    - Mocks: `TikTokProperties`, `StringRedisTemplate`, `RestTemplate`, `PlatformConnectionRepository`, `TokenEncryptionService`, `CreatorProfileRepository`
    - Tests for `getAuthorizationUrl()`:
      - `should_return_auth_url_when_valid_user_then_state_stored_in_redis()`
      - `should_build_url_with_client_key_param_not_client_id()`
    - Tests for `handleCallback()`:
      - `should_connect_tiktok_when_valid_callback_then_connection_saved_as_connected()`
      - `should_throw_when_oauth_state_is_invalid()`
      - `should_throw_when_oauth_state_is_expired_redis_returns_null()`
    - Tests for `getConnectionStatus()`:
      - `should_return_connected_status_when_connection_exists()`
      - `should_return_disconnected_when_no_connection_found()`
    - Tests for `disconnectTikTok()`:
      - `should_disconnect_when_connection_exists()`
      - `should_throw_resource_not_found_when_no_connection_to_disconnect()`
    - File: `src/test/java/com/samuel/app/platform/service/TikTokConnectionServiceTest.java`
  - [ ] Create `com.samuel.app.platform.adapter.TikTokAdapterTest`:
    - `@ExtendWith(MockitoExtension.class)`
    - Tests:
      - `should_return_circuit_open_when_circuit_breaker_state_is_open()`
      - `should_return_connected_when_circuit_breaker_closed()`
      - `should_return_tiktok_platform_type()`
      - `should_return_rate_limit_info_with_50_limit()`
      - `should_return_empty_revenue_data_for_tiktok()`
    - File: `src/test/java/com/samuel/app/platform/adapter/TikTokAdapterTest.java`
  - [ ] Create `com.samuel.app.platform.controller.PlatformConnectionControllerTikTokTest`:
    - `@ExtendWith(MockitoExtension.class)` with `MockMvcBuilders.standaloneSetup()`
    - Tests:
      - `should_return_tiktok_auth_url_when_authenticated_user_requests()`
      - `should_return_connection_status_when_user_requests_tiktok_status()`
      - `should_return_success_when_user_disconnects_tiktok()`
    - File: `src/test/java/com/samuel/app/platform/controller/PlatformConnectionControllerTikTokTest.java`

## Dev Notes

### Critical: TikTok OAuth 2.0 Differences from YouTube

TikTok uses a different OAuth parameter naming convention — this is the most common mistake when implementing TikTok after YouTube:

| Parameter | YouTube | TikTok |
|-----------|---------|--------|
| App identifier | `client_id` | `client_key` |
| Authorization URL | `https://accounts.google.com/o/oauth2/v2/auth` | `https://www.tiktok.com/v2/auth/authorize/` |
| Token URL | `https://oauth2.googleapis.com/token` | `https://open.tiktokapis.com/v2/oauth/token/` |
| User info URL | Google channels API | `https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,follower_count` |
| User identifier | `channel.id` | `open_id` |
| User display name | `channel.snippet.title` | `data.user.display_name` |
| Follower count | `channel.statistics.subscriberCount` | `data.user.follower_count` |

**The OAuth flow mirrors YouTube exactly** — CSRF state in Redis, code exchange, fetch user info, encrypt tokens:

```
Creator (authenticated) → GET /platforms/tiktok/auth/url
  → service generates UUID state
  → Redis: SET "oauth:tt:state:{state}" "{userId}" EX 600
  → returns TikTok OAuth URL with state param

Creator → redirected to TikTok consent screen
TikTok → GET /platforms/tiktok/callback?code=ABC&state={state}
  → controller receives (NOT authenticated - no JWT in redirect)
  → service: Redis GET "oauth:tt:state:{state}" → gets userId
  → Redis: DEL "oauth:tt:state:{state}" (prevent replay)
  → exchange code for tokens
  → fetch user info
  → store encrypted tokens in DB
```

### TikTok API Endpoints

| Purpose | Method | URL | Auth |
|---------|--------|-----|------|
| Token exchange | POST | `https://open.tiktokapis.com/v2/oauth/token/` | None (client creds in body) |
| User info | GET | `https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,follower_count` | Bearer token |

### Token Exchange Request (RestTemplate pattern — mirrors YouTube)

```java
// TikTok uses application/x-www-form-urlencoded same as Google
MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
params.add("client_key", tikTokProperties.getClientKey());   // NOT client_id!
params.add("client_secret", tikTokProperties.getClientSecret());
params.add("code", authorizationCode);
params.add("grant_type", "authorization_code");
params.add("redirect_uri", tikTokProperties.getRedirectUri());

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

TikTokTokenResponse tokenResponse = restTemplate.postForObject(
    "https://open.tiktokapis.com/v2/oauth/token/",
    request,
    TikTokTokenResponse.class
);
```

### TikTok User Info Response Structure

TikTok wraps user data in a `data.user` envelope:

```json
{
  "data": {
    "user": {
      "open_id": "abcd1234",
      "display_name": "Creator Name",
      "follower_count": 50000
    }
  },
  "error": {
    "code": "ok",
    "message": "",
    "log_id": "..."
  }
}
```

Model this with nested Java records:

```java
public record TikTokUserInfoResponse(Data data) {
    public record Data(User user) {
        public record User(
            @JsonProperty("open_id") String openId,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("follower_count") Long followerCount
        ) {}
    }
}
```

### TikTok Authorization URL Construction

```java
String authUrl = UriComponentsBuilder
    .fromHttpUrl("https://www.tiktok.com/v2/auth/authorize/")
    .queryParam("client_key", tikTokProperties.getClientKey())  // NOT client_id
    .queryParam("redirect_uri", tikTokProperties.getRedirectUri())
    .queryParam("response_type", "code")
    .queryParam("scope", "user.info.basic,user.info.profile,user.info.stats")
    .queryParam("state", state)
    .build()
    .toUriString();
```

### Reuse Existing Infrastructure — DO NOT Recreate

From Story 2.2, these are already in place and MUST be reused:

- `TokenEncryptionService` — reuse as-is for AES-256-GCM encryption (same `${PLATFORM_TOKEN_ENCRYPTION_KEY}`)
- `RestTemplate` bean in `PlatformWebConfig` — reuse (constructor inject, same as `YouTubeAdapter`)
- `PlatformConnectionRepository` — reuse (method `findByCreatorProfileIdAndPlatformType` already exists)
- `PlatformConnectionResponse` DTO — reuse for all responses
- `CircuitBreakerRegistry` and `RateLimiterRegistry` — reuse (already Spring beans)
- `getCreatorProfileId()` helper in `PlatformConnectionController` — reuse unchanged

### No New Flyway Migration Needed

The `platform_connections` table from Story 2.1 (`V5__create_platform_connections_table.sql`) already supports all platforms via the `platform_type` column (enum: `YOUTUBE`, `TIKTOK`, `INSTAGRAM`, `FACEBOOK`). The `TIKTOK` enum value is already in `PlatformType.java`.

**DO NOT create a new Flyway migration for this story.**

### Circuit Breaker Configuration Already in application-dev.yml

Story 2.1 already added the `tiktok-api` instances to the Resilience4j config:
- Circuit breaker: `tiktok-api` (inherits `default` config — 50% failure rate, 10 calls, OPEN for 30s)
- Rate limiter: `tiktok-api` (50 requests / 60s, 5s timeout)
- Retry: `tiktok-api` (3 attempts, 500ms wait, inherits `default` config)

**DO NOT add any new Resilience4j config.** Just use `@CircuitBreaker(name = "tiktok-api")` etc.

### Existing Source Structure (what exists after Story 2.2)

```
src/main/java/com/samuel/app/
├── config/
│   ├── SecurityConfig.java              ← MODIFY: add /platforms/tiktok/callback to permitAll
│   ├── CacheConfig.java                 ← DO NOT TOUCH
│   └── Resilience4jConfig.java          ← DO NOT TOUCH
├── platform/
│   ├── adapter/
│   │   ├── ConnectionStatus.java        ← DO NOT TOUCH
│   │   ├── IPlatformAdapter.java        ← DO NOT TOUCH
│   │   ├── PlatformType.java            ← DO NOT TOUCH (TIKTOK already defined)
│   │   ├── YouTubeAdapter.java          ← DO NOT TOUCH
│   │   └── [NEW] TikTokAdapter.java     ← Task 4
│   ├── config/
│   │   ├── PlatformWebConfig.java       ← DO NOT TOUCH (RestTemplate bean here)
│   │   ├── YouTubeProperties.java       ← DO NOT TOUCH
│   │   └── [NEW] TikTokProperties.java  ← Task 1
│   ├── controller/
│   │   └── PlatformConnectionController.java ← MODIFY: add TikTok endpoints (Task 5)
│   ├── dto/
│   │   ├── ContentMetrics.java          ← DO NOT TOUCH
│   │   ├── DateRange.java               ← DO NOT TOUCH
│   │   ├── PlatformConnectionResponse.java ← DO NOT TOUCH (reuse)
│   │   ├── PlatformCredentials.java     ← DO NOT TOUCH
│   │   ├── RateLimitInfo.java           ← DO NOT TOUCH
│   │   ├── RevenueData.java             ← DO NOT TOUCH
│   │   ├── YouTubeAuthUrlResponse.java  ← DO NOT TOUCH
│   │   ├── YouTubeChannelResponse.java  ← DO NOT TOUCH
│   │   ├── YouTubeTokenResponse.java    ← DO NOT TOUCH
│   │   ├── [NEW] TikTokAuthUrlResponse.java ← Task 2
│   │   ├── [NEW] TikTokTokenResponse.java   ← Task 2
│   │   └── [NEW] TikTokUserInfoResponse.java ← Task 2
│   ├── exception/                       ← DO NOT TOUCH
│   ├── model/
│   │   └── PlatformConnection.java      ← DO NOT TOUCH
│   ├── repository/
│   │   └── PlatformConnectionRepository.java ← DO NOT TOUCH
│   └── service/
│       ├── TokenEncryptionService.java  ← DO NOT TOUCH (reuse)
│       ├── YouTubeConnectionService.java ← DO NOT TOUCH
│       └── [NEW] TikTokConnectionService.java ← Task 3
```

### Controller Pattern: SecurityContext vs AuthenticationHelper

The existing `PlatformConnectionController` uses `SecurityContextHolder.getContext().getAuthentication().getName()` directly (not `AuthenticationHelper`). This was a pre-existing deviation from `project-context.md`. **Follow the existing pattern in this controller — do NOT refactor to use `AuthenticationHelper` in this story** (tracked in `deferred-work.md`).

### ApiResponse Factory Method

Story 2.2 used `ApiResponse.ok()`. Use the same method:
```java
return ResponseEntity.ok(ApiResponse.ok(response));
```
NOT `ApiResponse.success()` — check the actual `ApiResponse` class signature before writing.

### Deferred Work — Do NOT Fix in This Story

Do NOT address any items from `deferred-work.md` in this story:
- SecurityContext direct access in controller (Story 2.2 deviation) — tracked, fix later
- Redis auto-config not disabled in test profile
- CORS allowedOrigins hardcoded
- TOCTOU race in verifyEmail
- Token rotation not atomic in AuthService

### Test Setup: @ConfigurationProperties in Unit Tests

Since unit tests use `@ExtendWith(MockitoExtension.class)` (no Spring context), mock `TikTokProperties`:

```java
@Mock private TikTokProperties tikTokProperties;

@BeforeEach
void setUp() {
    when(tikTokProperties.getClientKey()).thenReturn("test-client-key");
    when(tikTokProperties.getClientSecret()).thenReturn("test-secret");
    when(tikTokProperties.getRedirectUri()).thenReturn("http://localhost/tiktok/callback");

    service = new TikTokConnectionService(
        tikTokProperties, redisTemplate, restTemplate,
        platformConnectionRepository, tokenEncryptionService, creatorProfileRepository
    );
}
```

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.3] — Acceptance criteria and user story
- [Source: _bmad-output/planning-artifacts/epics.md#Epic 2] — Epic context (Platform Integration & Connections)
- [Source: _bmad-output/planning-artifacts/architecture.md#Enhanced Platform Integration Patterns] — IPlatformAdapter pattern with circuit breaker annotations
- [Source: _bmad-output/planning-artifacts/architecture.md#Implementation Patterns & Consistency Rules] — Naming, structure, anti-patterns
- [Source: _bmad-output/project-context.md#Circuit Breaker Implementation Patterns] — Method-level annotations and fallback pattern
- [Source: _bmad-output/project-context.md#Test Coverage Requirements] — 100% line coverage, naming conventions
- [Source: _bmad-output/implementation-artifacts/2-2-youtube-platform-connection-and-authentication.md] — Complete YouTube implementation as the mirror pattern for TikTok
- [Source: src/main/resources/application-dev.yml] — Existing tiktok-api Resilience4j config (already configured)
- [Source: src/main/java/com/samuel/app/platform/controller/PlatformConnectionController.java] — Existing controller pattern to extend
- [Source: src/main/java/com/samuel/app/platform/adapter/YouTubeAdapter.java] — Mirror pattern for TikTokAdapter
- [Source: src/main/java/com/samuel/app/platform/service/YouTubeConnectionService.java] — Mirror pattern for TikTokConnectionService

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.6 (GitHub Copilot)

### Debug Log References

N/A — all tests passed on first build after fixing `UnnecessaryStubbingException` in `TikTokConnectionServiceTest.setUp()` (resolved by switching to `lenient().when(...)` for shared `@BeforeEach` stubs not consumed by every test).

### Completion Notes List

1. **`client_key` vs `client_id`** — TikTok uses `client_key` throughout the OAuth flow (authorization URL, token exchange request body). This differs from YouTube's `client_id` and is the most critical integration detail confirmed against the TikTok Login Kit for Web documentation.
2. **Redis key prefix** — Used `oauth:tt:state:` (consistent with `oauth:yt:state:` for YouTube). 10-minute TTL matches Story 2.2 pattern.
3. **`TikTokUserInfoResponse` nested records** — TikTok wraps user data in `data.user` envelope. Modelled as nested Java records: `TikTokUserInfoResponse(Data(User(...)))` with `@JsonProperty` annotations for snake_case field mapping.
4. **Rate limit window** — `getNextResetTime()` returns `now + 60s` (TikTok 60-second rolling window), not YouTube's midnight-UTC daily reset.
5. **Revenue data** — `getRevenueData()` returns `Optional.empty()` immediately; TikTok Creator Fund revenue is not available via the public API. Still annotated with `@CircuitBreaker` for interface consistency.
6. **No new Flyway migration** — `platform_connections` table from Story 2.1 (V5) already includes the `TIKTOK` enum value. Confirmed via H2 schema recreation in full test suite.
7. **`@ConfigurationProperties` class** — Annotated `TikTokProperties` with both `@Configuration` and `@ConfigurationProperties(prefix = "tiktok")` matching the story spec. No `@EnableConfigurationProperties` needed as class-level `@Configuration` registers the bean.
8. **SecurityConfig path** — Spring Security matches paths after context-path stripping, so permitted `/platforms/tiktok/callback` (without `/api/v1` prefix) consistent with the existing YouTube callback pattern.
9. **Controller test** — `PlatformConnectionControllerTikTokTest` instantiates the controller directly with all three constructor args (YouTube + TikTok services + repository) since the controller was modified to accept `TikTokConnectionService`.

### File List

**New files created:**
- `src/main/java/com/samuel/app/platform/config/TikTokProperties.java`
- `src/main/java/com/samuel/app/platform/dto/TikTokAuthUrlResponse.java`
- `src/main/java/com/samuel/app/platform/dto/TikTokTokenResponse.java`
- `src/main/java/com/samuel/app/platform/dto/TikTokUserInfoResponse.java`
- `src/main/java/com/samuel/app/platform/service/TikTokConnectionService.java`
- `src/main/java/com/samuel/app/platform/adapter/TikTokAdapter.java`
- `src/test/java/com/samuel/app/platform/service/TikTokConnectionServiceTest.java`
- `src/test/java/com/samuel/app/platform/adapter/TikTokAdapterTest.java`
- `src/test/java/com/samuel/app/platform/controller/PlatformConnectionControllerTikTokTest.java`

**Modified files:**
- `src/main/resources/application-dev.yml` — added `tiktok:` config block (client-key, client-secret, redirect-uri)
- `src/main/java/com/samuel/app/platform/controller/PlatformConnectionController.java` — added `TikTokConnectionService` injection and 4 TikTok endpoints
- `src/main/java/com/samuel/app/config/SecurityConfig.java` — added `permitAll()` for `/platforms/tiktok/callback`

**Test results:** 194 tests total, 0 failures, 0 errors (19 new TikTok tests + 175 pre-existing)
