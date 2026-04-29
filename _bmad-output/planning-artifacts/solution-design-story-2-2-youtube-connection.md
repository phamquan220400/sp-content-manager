---
story: "2.2"
title: "YouTube Platform Connection and Authentication"
author: "Winston (System Architect)"
date: "2026-04-29"
status: "approved"
storyFile: "_bmad-output/implementation-artifacts/2-2-youtube-platform-connection-and-authentication.md"
---

# Solution Design: Story 2.2 — YouTube Platform Connection and Authentication

_Architectural blueprint for implementing YouTube OAuth 2.0 integration on top of the platform adapter infrastructure established in Story 2.1._

---

## 1. Context and Foundations

### What Story 2.1 Already Gave Us

Story 2.1 delivered the load-bearing infrastructure this story builds on. All of the following are in place and must not be re-implemented:

| Component | Package | Role in 2.2 |
|-----------|---------|-------------|
| `IPlatformAdapter` | `platform.adapter` | Contract `YouTubeAdapter` must implement |
| `PlatformConnection` | `platform.model` | JPA entity for persisting connection + encrypted tokens |
| `PlatformConnectionRepository` | `platform.repository` | Finder: `findByCreatorProfileIdAndPlatformType()` already exists |
| `ConnectionStatus` | `platform.adapter` | `CONNECTED`, `DISCONNECTED`, `CIRCUIT_OPEN` — do not add new values |
| `PlatformType` | `platform.adapter` | `YOUTUBE` enum value already present |
| `PlatformConnectionException` | `platform.exception` | Throw for OAuth/connection failures |
| `PlatformApiException` | `platform.exception` | Throw for adapter API call failures |
| `Resilience4jConfig` | `config` | Lazy CB registration; `youtube-api` CB config in `application-dev.yml` |
| `GlobalExceptionHandler` | `shared.controller` | Already maps platform exceptions → HTTP 503/429 |

### Established Project Conventions This Design Follows

- **Package root**: `com.samuel.app`
- **Controller pattern**: `@RestController` + `@RequestMapping("/api/v1/...")` — controllers get `userId` via `SecurityContextHolder.getContext().getAuthentication().getName()`
- **Response wrapper**: `ApiResponse.ok(data)` — never return raw objects
- **Constructor injection only** — no `@Autowired` fields
- **Redis key pattern** (from `AuthService`): namespaced strings with TTL, e.g., `"oauth:yt:state:{uuid}"`
- **Exception path**: domain exceptions propagate up; `GlobalExceptionHandler` converts to JSON — do not `try/catch` in controllers

---

## 2. New Components to Build

Story 2.2 introduces eight new components across three logical layers. The dependency graph runs strictly downward.

```
HTTP Layer
  └── PlatformConnectionController
        │
Service Layer
  ├── YouTubeConnectionService
  │     ├── TokenEncryptionService
  │     ├── StringRedisTemplate (existing)
  │     ├── RestTemplate (new bean)
  │     ├── YouTubeProperties (config)
  │     ├── PlatformConnectionRepository (existing)
  │     └── CreatorProfileRepository (existing)
  │
Adapter Layer
  └── YouTubeAdapter  implements IPlatformAdapter
        ├── CircuitBreakerRegistry (existing)
        ├── RateLimiterRegistry (existing)
        ├── PlatformConnectionRepository (existing)
        ├── TokenEncryptionService
        └── RestTemplate

Configuration
  ├── YouTubeProperties (@ConfigurationProperties)
  └── PlatformWebConfig (RestTemplate bean)

DTOs (records)
  ├── YouTubeAuthUrlResponse
  ├── PlatformConnectionResponse
  ├── YouTubeTokenResponse     [internal — Google token endpoint]
  └── YouTubeChannelResponse   [internal — YouTube Data API v3]
```

---

## 3. OAuth 2.0 Flow Architecture

### Why Redis for CSRF State (Not HTTP Session)

The application is **stateless JWT** — there is no server-side HTTP session. The Google redirect back to `/callback` carries no JWT (it is a browser redirect), so we cannot authenticate the callback request. The CSRF `state` parameter must be stored server-side in Redis and retrieved by the value alone.

```
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Authenticated creator requests auth URL                  │
│                                                                  │
│  Creator (JWT) → GET /api/v1/platforms/youtube/auth/url          │
│       │                                                          │
│       ▼                                                          │
│  YouTubeConnectionService.getAuthorizationUrl(userId)            │
│       │                                                          │
│       ├── state = UUID.randomUUID().toString()                   │
│       ├── Redis: SET "oauth:yt:state:{state}" "{userId}" EX 600  │
│       └── Build Google OAuth URL with state + scopes             │
│              → return YouTubeAuthUrlResponse { authorizationUrl }│
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Creator authorises on Google consent screen             │
│         (outside our system)                                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Google redirects to our callback — NO JWT available      │
│                                                                  │
│  Browser → GET /api/v1/platforms/youtube/callback?code=X&state=Y │
│       │  (endpoint is permitAll in SecurityConfig)               │
│       ▼                                                          │
│  YouTubeConnectionService.handleCallback(code, state)            │
│       │                                                          │
│       ├── Redis GET "oauth:yt:state:{state}" → userId (or 400)   │
│       ├── Redis DEL "oauth:yt:state:{state}"  [prevent replay]   │
│       ├── CreatorProfileRepository.findByUserId(userId)          │
│       ├── POST google token endpoint → access + refresh tokens   │
│       ├── GET youtube/v3/channels?part=snippet,statistics&mine=true │
│       ├── TokenEncryptionService.encrypt(accessToken)            │
│       ├── TokenEncryptionService.encrypt(refreshToken)           │
│       └── PlatformConnectionRepository.save(connection)          │
│              → return PlatformConnectionResponse { CONNECTED }   │
└─────────────────────────────────────────────────────────────────┘
```

### OAuth URL Construction

Use `UriComponentsBuilder` — never string concatenation for URLs:

```java
String authUrl = UriComponentsBuilder
    .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
    .queryParam("client_id", properties.getClientId())
    .queryParam("redirect_uri", properties.getRedirectUri())
    .queryParam("response_type", "code")
    .queryParam("scope", "https://www.googleapis.com/auth/youtube.readonly " +
                         "https://www.googleapis.com/auth/yt-analytics.readonly")
    .queryParam("access_type", "offline")
    .queryParam("prompt", "consent")
    .queryParam("state", state)
    .toUriString();
```

---

## 4. Token Encryption Architecture (AES-256-GCM)

### Decision: No New Library

Java 17 `javax.crypto` provides AES-GCM natively. Adding a third-party encryption library for this use case would be over-engineering and add supply-chain risk.

### Encryption Format

```
Storage format: Base64( IV[12 bytes] + Ciphertext + GCM_AuthTag[16 bytes] )
```

The GCM auth tag is automatically appended to the ciphertext by the JCE provider. The decrypt side splits on byte offset 12 to recover the IV.

```
┌──────────────────────────────────────────────────────┐
│ encrypt(plaintext):                                   │
│   1. Generate 12 random bytes (IV)                   │
│   2. AES-256-GCM encrypt with IV + keyBytes           │
│   3. combined = IV + cipherBytes (tag already in it)  │
│   4. return Base64.getEncoder().encodeToString(combined) │
│                                                       │
│ decrypt(encoded):                                     │
│   1. Base64.decode(encoded) → combined               │
│   2. iv = combined[0..11]                             │
│   3. cipherBytes = combined[12..]                     │
│   4. AES-256-GCM decrypt with iv + keyBytes           │
│   5. return new String(plaintext, UTF_8)              │
└──────────────────────────────────────────────────────┘
```

### Key Source

```yaml
# application-dev.yml addition
platform:
  token-encryption-key: ${PLATFORM_TOKEN_ENCRYPTION_KEY:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=}
```

`TokenEncryptionService` constructor:
```java
public TokenEncryptionService(@Value("${platform.token-encryption-key}") String keyBase64) {
    this.keyBytes = Base64.getDecoder().decode(keyBase64); // must be 32 bytes
}
```

**Production gate**: the zero-byte default key MUST be replaced via environment variable. This is a placeholder that should fail loudly if accidentally used in production — consider adding a `@PostConstruct` validation that checks `keyBytes.length == 32`.

---

## 5. Component Design Details

### 5.1 YouTubeProperties

```
Package: com.samuel.app.platform.config
File: YouTubeProperties.java
```

| Field | Binding | Source |
|-------|---------|--------|
| `clientId` | `youtube.client-id` | `${YOUTUBE_CLIENT_ID}` |
| `clientSecret` | `youtube.client-secret` | `${YOUTUBE_CLIENT_SECRET}` |
| `redirectUri` | `youtube.redirect-uri` | `${YOUTUBE_REDIRECT_URI}` |

Annotate `@Configuration @ConfigurationProperties(prefix = "youtube")`. Register with `@EnableConfigurationProperties(YouTubeProperties.class)` on `AppApplication`.

---

### 5.2 PlatformWebConfig

```
Package: com.samuel.app.platform.config
File: PlatformWebConfig.java
```

Produces a single `RestTemplate` bean with timeouts tuned for external API calls:

| Setting | Value | Rationale |
|---------|-------|-----------|
| Connection timeout | 5 s | Fail fast on unreachable Google endpoints |
| Read timeout | 10 s | Token exchange may be slow under load |

Use `RestTemplateBuilder` — Spring Boot auto-configures message converters and error handlers through it.

---

### 5.3 TokenEncryptionService

```
Package: com.samuel.app.platform.service
File: TokenEncryptionService.java
```

**Security requirements:**
- IV must be generated fresh per encryption (use `SecureRandom`) — never reuse IV with the same key
- GCM tag length: 128 bits
- Key: `SecretKeySpec(keyBytes, "AES")` where `keyBytes` is exactly 32 bytes
- Throw `IllegalStateException` on decryption failure (auth tag mismatch = tampering)

---

### 5.4 DTOs

All DTOs are Java records (immutable, no boilerplate).

```
Package: com.samuel.app.platform.dto
```

| Record | Direction | Fields |
|--------|-----------|--------|
| `YouTubeAuthUrlResponse` | API response | `String authorizationUrl` |
| `PlatformConnectionResponse` | API response | `platformType, status, platformUserId, platformName, followerCount, lastSyncAt, connectedAt` |
| `YouTubeTokenResponse` | Internal (Google token endpoint) | `accessToken, refreshToken, expiresIn, tokenType` — use `@JsonProperty` for snake_case mapping |
| `YouTubeChannelResponse` | Internal (YouTube Data API) | Nested: `List<Item> items` → `Item(id, Snippet(title), Statistics(subscriberCount))` |

`YouTubeTokenResponse` and `YouTubeChannelResponse` are internal parsing types — mark package-private if desired to signal they are not API contracts.

---

### 5.5 YouTubeConnectionService

```
Package: com.samuel.app.platform.service
File: YouTubeConnectionService.java
```

**Constructor dependencies** (all final, injected):
1. `YouTubeProperties`
2. `StringRedisTemplate`
3. `RestTemplate`
4. `PlatformConnectionRepository`
5. `TokenEncryptionService`
6. `CreatorProfileRepository`

**Method responsibilities:**

| Method | AC | Key design notes |
|--------|----|-----------------|
| `getAuthorizationUrl(String userId)` | AC1 | Generate UUID state; Redis SET with 10-min TTL; UriComponentsBuilder for URL |
| `handleCallback(String code, String state)` | AC2, AC5 | Redis GET → validate state → DEL → exchange code → fetch channel → encrypt tokens → upsert PlatformConnection |
| `getConnectionStatus(String creatorProfileId)` | AC3 | Find by creatorProfileId + YOUTUBE; return DISCONNECTED response if absent |
| `disconnectYouTube(String creatorProfileId)` | AC4 | Set DISCONNECTED + null tokens; save; return updated response |

**Upsert logic in `handleCallback`:**

```java
PlatformConnection conn = platformConnectionRepository
    .findByCreatorProfileIdAndPlatformType(creatorProfile.getId(), PlatformType.YOUTUBE)
    .orElseGet(() -> {
        PlatformConnection c = new PlatformConnection();
        c.setId(UUID.randomUUID().toString());
        c.setCreatorProfileId(creatorProfile.getId());
        c.setPlatformType(PlatformType.YOUTUBE);
        return c;
    });
// Set all fields, save, return response
```

**Token exchange — HTTP call:**
```java
MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
params.add("code", code);
params.add("client_id", properties.getClientId());
params.add("client_secret", properties.getClientSecret());
params.add("redirect_uri", properties.getRedirectUri());
params.add("grant_type", "authorization_code");

YouTubeTokenResponse tokenResponse = restTemplate.postForObject(
    "https://oauth2.googleapis.com/token", params, YouTubeTokenResponse.class);
```

**Channel info — HTTP call:**
```java
HttpHeaders headers = new HttpHeaders();
headers.setBearerAuth(tokenResponse.accessToken());
HttpEntity<Void> entity = new HttpEntity<>(headers);

YouTubeChannelResponse channelResponse = restTemplate.exchange(
    "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true",
    HttpMethod.GET, entity, YouTubeChannelResponse.class).getBody();
```

---

### 5.6 YouTubeAdapter

```
Package: com.samuel.app.platform.adapter
File: YouTubeAdapter.java
```

**Constructor dependencies:**
1. `CircuitBreakerRegistry`
2. `RateLimiterRegistry`
3. `PlatformConnectionRepository`
4. `TokenEncryptionService`
5. `RestTemplate`

**Implementation contract:**

| Method | Resilience4j decorators | Fallback |
|--------|------------------------|---------|
| `connect()` | `@CircuitBreaker`, `@RateLimiter`, `@Retry` (all `youtube-api`) | `connectFallback` → throw `PlatformConnectionException` |
| `fetchMetrics()` | `@CircuitBreaker`, `@RateLimiter`, `@Retry` | `fetchMetricsFallback` → throw `PlatformApiException(YOUTUBE, 503, "CIRCUIT_OPEN", true)` |
| `getRevenueData()` | `@CircuitBreaker`, `@RateLimiter`, `@Retry` | `getRevenueDataFallback` → throw `PlatformApiException` |
| `getConnectionStatus()` | None | CB state map: CLOSED→CONNECTED, OPEN/HALF_OPEN→CIRCUIT_OPEN |
| `getRemainingQuota()` | None | Returns `RateLimitInfo` with `availablePermissions` from rate limiter metrics |
| `getNextResetTime()` | None | Next midnight UTC: `LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay()` |
| `getPlatformType()` | None | Returns `PlatformType.YOUTUBE` |

**Important:** `getConnectionStatus()` in the adapter reflects **circuit breaker state only**. The controller must cross-reference DB status when exposing connection status to the creator.

---

### 5.7 PlatformConnectionController

```
Package: com.samuel.app.platform.controller
File: PlatformConnectionController.java
@RestController @RequestMapping("/api/v1/platforms")
```

**Endpoints:**

| Method | Path | Auth | How userId/profileId obtained |
|--------|------|------|-------------------------------|
| `GET` | `/youtube/auth/url` | Required (JWT) | `SecurityContextHolder...getName()` → `userId` |
| `GET` | `/youtube/callback` | `permitAll` (OAuth redirect) | `code`, `state` from `@RequestParam` |
| `GET` | `/youtube/connection` | Required (JWT) | `userId` → look up `creatorProfileId` via service |
| `DELETE` | `/youtube/disconnect` | Required (JWT) | `userId` → look up `creatorProfileId` via service |

**Design note on `creatorProfileId`:** The controller receives a JWT subject (`userId`). The `getConnectionStatus` and `disconnectYouTube` service methods on `YouTubeConnectionService` need `creatorProfileId`. Options:

- **Option A (recommended)**: Pass `userId` to service; service resolves `creatorProfileId` internally using `CreatorProfileRepository.findByUserId(userId)`. Keeps the controller thin.
- **Option B**: Controller resolves profile separately. Adds unnecessary coupling.

Use Option A for consistency with how `CreatorProfileService` already works.

**Callback endpoint note:** This endpoint must NOT require a JWT because the Google browser redirect carries no Authorization header. Add to `SecurityConfig`:

```java
.requestMatchers("/platforms/youtube/callback").permitAll()
```

Note: Spring Security matches post-context-path-stripping, so use `/platforms/youtube/callback` (not `/api/v1/...`).

---

## 6. Security Configuration Change

```
File: src/main/java/com/samuel/app/config/SecurityConfig.java
Change: Add .requestMatchers("/platforms/youtube/callback").permitAll() before .anyRequest().authenticated()
```

**Risk:** This endpoint is open. It is protected by the time-limited Redis CSRF state token — any request with an invalid or expired state is rejected with 400. The 10-minute TTL ensures the window is narrow.

---

## 7. Application Configuration Additions

```yaml
# application-dev.yml additions

youtube:
  client-id: ${YOUTUBE_CLIENT_ID:your-client-id}
  client-secret: ${YOUTUBE_CLIENT_SECRET:your-client-secret}
  redirect-uri: ${YOUTUBE_REDIRECT_URI:http://localhost:8080/api/v1/platforms/youtube/callback}

platform:
  token-encryption-key: ${PLATFORM_TOKEN_ENCRYPTION_KEY:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=}
```

No new Maven dependencies are needed. The encryption uses built-in Java 17. `RestTemplate` is already on the classpath via `spring-boot-starter-web`.

---

## 8. Key Architectural Decisions

### ADR-1: Stateless CSRF via Redis (not Spring Session)

**Problem**: The OAuth callback has no JWT — it's a browser redirect with `?code=...&state=...`.

**Decision**: Store state → userId mapping in Redis with 10-min TTL. Delete key immediately after first use.

**Consequence**: Requires Redis to be available during the OAuth flow. Given Redis is already a required dependency (JWT refresh tokens), this adds no new infrastructure risk.

---

### ADR-2: AES-256-GCM over AES-CBC

**Decision**: GCM mode (authenticated encryption) over CBC.

**Rationale**: GCM provides both confidentiality and integrity. An adversary who tampers with the ciphertext will cause decryption to fail at authentication-tag validation — we detect tampering, not just decrypt garbage. CBC without MAC is vulnerable to padding oracle attacks.

---

### ADR-3: YouTubeAdapter.connect() as Infrastructure Health Check, Not OAuth Entry Point

**Problem**: `IPlatformAdapter.connect()` exists to establish a connection — but for OAuth 2.0, the actual exchange happens through a browser redirect flow, not a direct service call.

**Decision**: `YouTubeConnectionService` owns the OAuth flow. `YouTubeAdapter.connect()` serves as a **health validation** — it verifies an existing connection is usable by attempting a basic API call with the stored decrypted token.

**Consequence**: The circuit breaker wraps this health check, not the initial OAuth authorization step. This is the correct design — you only trigger circuit breaker counting on API calls that are expected to be stable, not on the one-time OAuth setup.

---

### ADR-4: Upsert Pattern for PlatformConnection

**Decision**: `handleCallback()` finds or creates the `PlatformConnection` entity (upsert).

**Rationale**: A creator might re-connect YouTube (after disconnecting). If we only created, re-connection would fail with a unique constraint violation. If we only updated, the first connection would fail. Upsert handles both cases cleanly.

---

### ADR-5: Controller Receives userId, Services Resolve creatorProfileId

**Decision**: Controllers extract `userId` from the JWT subject. Service methods that need `creatorProfileId` resolve it internally.

**Rationale**: Consistent with established `CreatorProfileService` and `DashboardService` patterns. Keeps controllers as thin delegation layers.

---

## 9. Test Strategy

| Test Class | Scope | Key Coverage |
|------------|-------|-------------|
| `TokenEncryptionServiceTest` | Unit | Encrypt/decrypt round-trip; IV randomness; tamper detection |
| `YouTubeConnectionServiceTest` | Unit (Mockito) | `getAuthorizationUrl` — Redis state; `handleCallback` — valid/invalid/expired state; connection status; disconnect |
| `YouTubeAdapterTest` | Unit (Mockito) | CB state mapping; `getRemainingQuota`; fallback throws; `getPlatformType` |
| `PlatformConnectionControllerTest` | Unit (MockMvc standalone) | Auth URL returns 200; connection status; disconnect; callback delegates state validation |

**Testing note on circuit breaker**: Use `CircuitBreakerRegistry.ofDefaults()` in unit tests — no Spring context needed. Call `circuitBreaker.transitionToOpenState()` programmatically to test the OPEN state path.

---

## 10. Implementation Sequence

The tasks in the story file are already correctly sequenced. This order minimizes broken-state time:

```
1. YouTubeProperties + application-dev.yml
2. TokenEncryptionService          ← no dependencies on story 2.2 components
3. PlatformWebConfig (RestTemplate bean)
4. DTOs (YouTubeAuthUrlResponse, PlatformConnectionResponse, YouTubeTokenResponse, YouTubeChannelResponse)
5. YouTubeConnectionService        ← depends on 1-4 + existing repos
6. YouTubeAdapter                  ← depends on 3, existing CB/RL registries
7. PlatformConnectionController    ← depends on 5
8. SecurityConfig update           ← can be done anytime; needed for integration
9. Unit tests                      ← written alongside or after each component
```

---

## 11. Integration Checklist (Before Dev Complete)

- [ ] `PLATFORM_TOKEN_ENCRYPTION_KEY` env var documented in `docker-compose.yml` and `.env.example`
- [ ] `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, `YOUTUBE_REDIRECT_URI` documented similarly
- [ ] Redirect URI registered in Google Cloud Console matches `youtube.redirect-uri` config exactly
- [ ] `/actuator/health` shows `youtube-api` circuit breaker after first adapter call
- [ ] Token round-trip verified: encrypt → store → retrieve → decrypt → API call succeeds
- [ ] Disconnect clears tokens (access + refresh set to null in DB)

---

_Solution design authored by Winston. Ready for implementation by the dev agent via `bmad-dev-story`._
