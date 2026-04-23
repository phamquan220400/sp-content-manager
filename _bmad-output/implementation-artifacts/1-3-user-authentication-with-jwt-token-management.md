# Story 1.3: User Authentication with JWT Token Management

Status: review

## Story

As a registered creator,
I want to securely log in and log out of the platform,
so that I can access my creator workspace with proper authentication.

## Acceptance Criteria

<!-- Refined by party mode (Winston/Amelia/John) on 2026-04-21. Epics AC gaps filled. -->

1. **AC1 — Login success (ACTIVE user)**
   **Given** I have a registered account with `status = ACTIVE`
   **When** I submit `POST /api/v1/auth/login` with valid `{ email, password }`
   **Then** the response is HTTP 200 with `ApiResponse` containing `{ accessToken, refreshToken, expiresIn }` where `expiresIn` is the number of seconds until the access token expires
   **And** the access token is a signed JWT containing `{ sub: userId, email, iat, exp }`
   **And** the refresh token is an opaque UUID stored in Redis as `refresh:{refreshToken}` with a configurable TTL
   **And** `failed_login_attempts` is reset to `0` and `locked_until` is set to `null` for this user

2. **AC2 — PENDING (unverified) user login**
   **Given** I have registered but not verified my email (`status = PENDING`)
   **When** I submit `POST /api/v1/auth/login`
   **Then** the response is HTTP 403 with `ApiResponse.error("Please verify your email address before logging in.")`
   **And** no tokens are issued

3. **AC3 — Invalid credentials**
   **Given** I submit `POST /api/v1/auth/login` with a non-existent email or incorrect password
   **Then** the response is HTTP 401 with `ApiResponse.error("Invalid email or password.")`
   **And** the same message is used for both cases to prevent user enumeration
   **And** `failed_login_attempts` is incremented for the matching user (if the email exists)

4. **AC4 — Account lockout**
   **Given** I have reached the configurable `app.auth.lockout-threshold` (default: 5) consecutive failed login attempts
   **When** I submit another `POST /api/v1/auth/login` for this email
   **Then** `locked_until` is set to `now + app.auth.lockout-duration` (default: 15 minutes)
   **And** the response is HTTP 401 with `ApiResponse.error("Account temporarily locked. Please try again later.")`
   **And** subsequent login attempts while locked return the same 401 message without incrementing the counter further

5. **AC5 — Refresh access token**
   **Given** I have a valid, unexpired refresh token
   **When** I submit `POST /api/v1/auth/refresh` with `{ refreshToken }` in the request body
   **Then** the response is HTTP 200 with `ApiResponse` containing a new `{ accessToken, refreshToken, expiresIn }`
   **And** the old refresh token is deleted from Redis
   **And** the new refresh token is stored in Redis with a fresh TTL (token rotation)

6. **AC6 — Refresh with invalid or expired token**
   **Given** I submit `POST /api/v1/auth/refresh` with an unknown or expired refresh token
   **Then** the response is HTTP 401 with `ApiResponse.error("Invalid or expired refresh token.")`

7. **AC7 — Logout**
   **Given** I am authenticated (valid JWT in `Authorization: Bearer` header)
   **When** I submit `POST /api/v1/auth/logout` with `{ refreshToken }` in the request body
   **Then** the refresh token is deleted from Redis
   **And** the response is HTTP 200 with `ApiResponse.ok(null)` and message `"Logged out successfully."`
   **And** the access token remains valid until its natural expiry (stateless — not blocklisted in this story)

8. **AC8 — JWT filter — protected routes**
   **Given** a request is made to any route NOT in the permit-list (`/auth/**`, `/actuator/health`, `/actuator/info`)
   **When** the request carries a valid, unexpired JWT in `Authorization: Bearer <token>`
   **Then** the `SecurityContext` is populated and the request proceeds
   **When** the token is absent, malformed, or expired
   **Then** the response is HTTP 401 with no redirect (stateless API)

9. **AC9 — Input validation**
   **Given** I submit `POST /api/v1/auth/login` with a blank or missing email or password
   **Then** the response is HTTP 400 with a descriptive `ApiResponse` validation error message (consistent with registration)

## Tasks / Subtasks

- [x] **Task 1: Add pom.xml dependencies (AC: 1, 5, 8)**
  - [x] Add `io.jsonwebtoken:jjwt-api:0.12.6`
  - [x] Add `io.jsonwebtoken:jjwt-impl:0.12.6` (scope: runtime)
  - [x] Add `io.jsonwebtoken:jjwt-jackson:0.12.6` (scope: runtime)
  - [x] Add `org.springframework.boot:spring-boot-starter-data-redis`

- [x] **Task 2: Add V3 Flyway migration for lockout columns (AC: 4)**
  - [x] Create `src/main/resources/db/migration/V3__add_login_lockout_to_users.sql`
  - [x] Schema: `ALTER TABLE users ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0, ADD COLUMN locked_until DATETIME NULL`

- [x] **Task 3: Update User entity for lockout fields (AC: 4)**
  - [x] Add `int failedLoginAttempts` field to `com.samuel.app.creator.model.User` with `@Column(name = "failed_login_attempts")`
  - [x] Add `LocalDateTime lockedUntil` field with `@Column(name = "locked_until")`
  - [x] Add getters/setters for both new fields

- [x] **Task 4: Add auth config properties (AC: 1, 4, 5, 9)**
  - [x] In `application-dev.yml` add under `app.auth:` section:
    - `access-token-expiry: 900` (seconds = 15 min)
    - `refresh-token-expiry: 604800` (seconds = 7 days)
    - `lockout-threshold: 5`
    - `lockout-duration: 15` (minutes)
  - [x] In `src/test/resources/application-test.yml` add same `app.auth` block

- [x] **Task 5: Create DTOs (AC: 1, 5, 7)**
  - [x] Create `com.samuel.app.creator.dto.LoginRequest` (Java record): `@Email @NotBlank String email`, `@NotBlank String password`
  - [x] Create `com.samuel.app.creator.dto.AuthResponse` (Java record): `String accessToken`, `String refreshToken`, `long expiresIn`
  - [x] Create `com.samuel.app.creator.dto.RefreshRequest` (Java record): `@NotBlank String refreshToken`
  - [x] Create `com.samuel.app.creator.dto.LogoutRequest` (Java record): `@NotBlank String refreshToken`

- [x] **Task 6: Create JwtUtil (AC: 1, 5, 8)**
  - [x] Create `com.samuel.app.shared.util.JwtUtil`
  - [x] Inject `@Value("${jwt.secret}") String secret` and `@Value("${app.auth.access-token-expiry}") long accessTokenExpirySeconds`
  - [x] Method: `String generateAccessToken(String userId, String email)` — signs with HS256 using `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`
  - [x] Method: `Claims parseToken(String token)` — throws `JwtException` on invalid/expired
  - [x] Method: `String extractUserId(String token)` — delegates to `parseToken`
  - [x] NOTE: jjwt 0.12.x API — use `Jwts.builder()...signWith(key).compact()` and `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)`

- [x] **Task 7: Create UserDetailsServiceImpl (AC: 1, 2, 4, 8)**
  - [x] Create `com.samuel.app.creator.service.UserDetailsServiceImpl implements UserDetailsService`
  - [x] Inject `UserRepository`, `Clock` (see Dev Notes — inject clock bean, don't call `LocalDateTime.now()` directly)
  - [x] `loadUserByUsername(String email)`: load user by email or throw `UsernameNotFoundException`
  - [x] If `user.status == PENDING` → throw `DisabledException("Please verify your email address before logging in.")`
  - [x] If `user.lockedUntil != null && user.lockedUntil.isAfter(LocalDateTime.now(clock))` → throw `LockedException("Account temporarily locked. Please try again later.")`
  - [x] Return `org.springframework.security.core.userdetails.User.builder().username(user.getId()).password(user.getPassword()).roles("CREATOR").build()`

- [x] **Task 8: Create AuthService (AC: 1, 2, 3, 4, 5, 6, 7)**
  - [x] Create `com.samuel.app.creator.service.AuthService`
  - [x] Inject: `AuthenticationManager`, `UserRepository`, `JwtUtil`, `StringRedisTemplate`, `Clock`
  - [x] Inject config values: `@Value("${app.auth.refresh-token-expiry}") long refreshTokenExpirySeconds`, `@Value("${app.auth.lockout-threshold}") int lockoutThreshold`, `@Value("${app.auth.lockout-duration}") int lockoutDurationMinutes`
  - [x] `AuthResponse login(LoginRequest request)`:
    - Validate lockout FIRST: if `user.lockedUntil != null && isAfter(now)` → throw `LockedException`
    - Call `authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))`
    - On `BadCredentialsException`: increment `failed_login_attempts` in DB; if `>= lockoutThreshold` set `lockedUntil = now + lockoutDurationMinutes`; save user; re-throw
    - On success: reset `failedLoginAttempts = 0`, `lockedUntil = null`; save user
    - Generate JWT via `JwtUtil.generateAccessToken(userId, email)`
    - Generate refresh token: `UUID.randomUUID().toString()`; store in Redis key `refresh:{refreshToken}` → value: `userId`, TTL: `refreshTokenExpirySeconds` seconds
    - Return `new AuthResponse(accessToken, refreshToken, accessTokenExpirySeconds)`
  - [x] `AuthResponse refreshToken(RefreshRequest request)`:
    - Lookup `refresh:{request.refreshToken}` in Redis; if absent → throw `InvalidTokenException`
    - Delete old Redis key; generate new JWT + new refresh token UUID; store new Redis key; return new `AuthResponse`
  - [x] `void logout(LogoutRequest request)`:
    - Delete `refresh:{request.refreshToken}` from Redis (idempotent — no error if already gone)

- [x] **Task 9: Create JwtAuthenticationFilter (AC: 8)**
  - [x] Create `com.samuel.app.shared.filter.JwtAuthenticationFilter extends OncePerRequestFilter`
  - [x] Inject `JwtUtil`, `UserDetailsServiceImpl`
  - [x] Extract `Authorization: Bearer <token>` header; if absent, call `filterChain.doFilter()` and return
  - [x] Call `JwtUtil.parseToken(token)`; on `JwtException` → `response.sendError(HttpServletResponse.SC_UNAUTHORIZED)`; return
  - [x] Load `UserDetails` from `UserDetailsServiceImpl.loadUserById(userId from token)`
  - [x] Set `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`
  - [x] Continue filter chain

- [x] **Task 10: Update SecurityConfig (AC: 1, 8)**
  - [x] Add `@Bean AuthenticationManager authenticationManager(UserDetailsServiceImpl uds, BCryptPasswordEncoder encoder)` using `DaoAuthenticationProvider`
  - [x] Add `JwtAuthenticationFilter` to filter chain: `.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
  - [x] Change `.anyRequest().denyAll()` to `.anyRequest().authenticated()`
  - [x] Add `SessionCreationPolicy.STATELESS` to session management
  - [x] Add `@Bean Clock clock()` returning `Clock.systemDefaultZone()` — required for testable lockout logic

- [x] **Task 11: Update AuthController (AC: 1, 2, 3, 4, 5, 6, 7, 9)**
  - [x] Add `POST /auth/login` — `@Valid @RequestBody LoginRequest` → delegates to `AuthService.login()` → HTTP 200 `ApiResponse.ok(authResponse)`
  - [x] Add `POST /auth/refresh` — `@Valid @RequestBody RefreshRequest` → delegates to `AuthService.refreshToken()` → HTTP 200
  - [x] Add `POST /auth/logout` — requires authentication (JWT filter), `@Valid @RequestBody LogoutRequest` → delegates to `AuthService.logout()` → HTTP 200
  - [x] Exception handling: `DisabledException` and `LockedException` → handled in `GlobalExceptionHandler` (see Task 12)

- [x] **Task 12: Update GlobalExceptionHandler (AC: 2, 4)**
  - [x] Add handler for `org.springframework.security.authentication.DisabledException` → HTTP 403 `ApiResponse.error(e.getMessage())`
  - [x] Add handler for `org.springframework.security.authentication.LockedException` → HTTP 401 `ApiResponse.error(e.getMessage())`
  - [x] Add handler for `org.springframework.security.authentication.BadCredentialsException` → HTTP 401 `ApiResponse.error("Invalid email or password.")`
  - [x] NOTE: `InvalidTokenException` (refresh token not found) — add to `com.samuel.app.exceptions` package, handle → HTTP 401 `ApiResponse.error("Invalid or expired refresh token.")`

- [x] **Task 13: Write tests (All ACs)**
  - [x] `AuthServiceTest` (`@ExtendWith(MockitoExtension.class)`) — mock all deps, inject `Clock` via `@InjectMocks` with fixed-time clock:
    - `login_validCredentials_returnsAuthResponse`
    - `login_pendingUser_throwsDisabledException`
    - `login_invalidPassword_throwsBadCredentialsExceptionAndIncrementsCount`
    - `login_exceedsLockoutThreshold_setsLockedUntilAndThrowsLockedException`
    - `login_lockedAccount_throwsLockedExceptionWithoutIncrement`
    - `login_successResetsFailedAttempts`
    - `refreshToken_validToken_returnsNewAuthResponse`
    - `refreshToken_invalidToken_throwsInvalidTokenException`
    - `logout_validToken_deletesFromRedis`
  - [x] `JwtUtilTest` (`@ExtendWith(MockitoExtension.class)`) — use real jjwt (no mocking):
    - `generateAccessToken_validInputs_returnsSignedJwt`
    - `parseToken_validToken_returnsClaims`
    - `parseToken_expiredToken_throwsJwtException`
    - `parseToken_tamperedToken_throwsJwtException`
  - [x] `JwtAuthenticationFilterTest` (`@ExtendWith(MockitoExtension.class)`):
    - `doFilterInternal_validToken_setsSecurityContext`
    - `doFilterInternal_noToken_continuesChainWithoutAuth`
    - `doFilterInternal_invalidToken_sends401`
  - [x] `AuthControllerTest` (`@WebMvcTest(AuthController.class)` + `@AutoConfigureMockMvc(addFilters = false)`):
    - `login_validRequest_returns200WithTokens`
    - `login_blankEmail_returns400`
    - `login_disabledUser_returns403`
    - `login_lockedUser_returns401`
    - `refresh_validToken_returns200`
    - `refresh_invalidToken_returns401`
    - `logout_validRequest_returns200`

## Dev Notes

### Critical Patterns from Story 1.2 (MUST follow)

- `User.id` has NO `@GeneratedValue` — always call `user.setId(UUID.randomUUID().toString())` manually when creating new users. Do NOT add `@GeneratedValue` now.
- `User.password` field is named `password` in Java but maps to `password` column in DB — no `@Column` annotation needed (JPA default works).
- Tests use H2 in-memory DB (`application-test.yml`) with `ddl-auto: create-drop` and Flyway DISABLED.
- `@WebMvcTest` tests MUST use `@AutoConfigureMockMvc(addFilters = false)` — the security filter chain blocks all requests in test context without this.
- `ApiResponse` is in `com.samuel.app.shared.controller` — do NOT create a new response wrapper.
- Exceptions go in `com.samuel.app.exceptions` package — add `InvalidTokenException` here.
- `EmailAlreadyExistsException` extends `RuntimeException` (infer pattern — match it for `InvalidTokenException`).

### JWT Implementation — jjwt 0.12.x API (BREAKING CHANGE from 0.11.x)

The project will use jjwt `0.12.6`. The API changed significantly from 0.11.x — do NOT use old API patterns.

**Correct 0.12.x token generation:**
```java
SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
String token = Jwts.builder()
    .subject(userId)
    .claim("email", email)
    .issuedAt(Date.from(Instant.now()))
    .expiration(Date.from(Instant.now().plusSeconds(expirySeconds)))
    .signWith(key)
    .compact();
```

**Correct 0.12.x token parsing:**
```java
SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
Claims claims = Jwts.parser()
    .verifyWith(key)
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

JWT secret is already configured: `jwt.secret: ${JWT_SECRET:changeme-in-production-use-env-var}` in `application-dev.yml`. In `application-test.yml`: `jwt.secret: test-secret-key-for-unit-tests-only`. Do NOT change these keys.

### Clock Injection — Required for Testable Lockout Logic

**Do NOT call `LocalDateTime.now()` directly in `AuthService` or `UserDetailsServiceImpl`.** This makes lockout tests non-deterministic and flaky.

Instead, declare a `@Bean` in `SecurityConfig`:
```java
@Bean
public Clock clock() {
    return Clock.systemDefaultZone();
}
```

Inject in `AuthService` and `UserDetailsServiceImpl`:
```java
@Autowired private Clock clock;
// Usage:
LocalDateTime.now(clock)
```

In tests, inject a fixed clock:
```java
Clock fixedClock = Clock.fixed(Instant.parse("2026-04-21T10:00:00Z"), ZoneOffset.UTC);
```

### Redis Refresh Token Storage Pattern

Refresh tokens are stored in Redis as:
- **Key**: `refresh:{refreshTokenUUID}` (e.g., `refresh:abc123-def456-...`)
- **Value**: the `userId` (String)
- **TTL**: `app.auth.refresh-token-expiry` seconds (default: 604800 = 7 days)

Use `StringRedisTemplate` (already provided by Spring Data Redis autoconfiguration):
```java
// Store
redisTemplate.opsForValue().set("refresh:" + refreshToken, userId, Duration.ofSeconds(refreshTokenExpirySeconds));
// Lookup
String userId = redisTemplate.opsForValue().get("refresh:" + refreshToken);
// Delete
redisTemplate.delete("refresh:" + refreshToken);
```

`spring-boot-starter-data-redis` needs to be added to `pom.xml` — it's NOT currently present.
Redis config is implicit for dev (Lettuce default: `localhost:6379`). No `application-dev.yml` changes needed for Redis defaults unless the dev environment uses a non-default port. For tests, mock `StringRedisTemplate` with `@MockBean`.

### Redis Availability Risk

If Redis is unavailable, refresh token operations will throw `RedisConnectionFailureException`. `AuthService` should let this propagate naturally — it will be caught by `GlobalExceptionHandler`'s generic `Exception` handler and return HTTP 500. This is acceptable behavior (access tokens still work for 15 min). Do NOT add silent fallback.

### SecurityConfig Changes — Exact Pattern

The current `securityFilterChain` has `.anyRequest().denyAll()`. Change to `.anyRequest().authenticated()`.

Add session management and exception handling:
```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((req, res, e) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
)
```

`AuthenticationManager` bean using `DaoAuthenticationProvider`:
```java
@Bean
public AuthenticationManager authenticationManager(
        UserDetailsServiceImpl userDetailsService,
        BCryptPasswordEncoder encoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
}
```

### Lockout Product Decision (from party review)

The PM raised a valid concern: hard lockout at an early-stage product may harm creator activation. This story implements configurable hard lockout (default: 5 attempts / 15 min) as the baseline. The threshold and duration are externalized via `app.auth.lockout-threshold` and `app.auth.lockout-duration` so they can be tuned without a code change. A future story can introduce progressive delays if activation metrics show drop-off.

### V3 Migration — H2 Compatibility

The test environment uses H2 with `MODE=MySQL` and Flyway **disabled** (`flyway.enabled: false`). The V3 migration will NOT run in tests — H2 creates tables from JPA entity annotations (`ddl-auto: create-drop`). The new `failedLoginAttempts` and `lockedUntil` fields on `User.java` will be auto-created by H2 in tests. No test-specific migration SQL is needed.

### UserDetailsServiceImpl — UserDetails contract

`UserDetailsService.loadUserByUsername(String username)` — Spring Security calls this with whatever was passed to `UsernamePasswordAuthenticationToken` as the "username". For login, AuthService must pass `email` (not userId) to the `UsernamePasswordAuthenticationToken`. However, the returned `UserDetails.getUsername()` should return `userId` (not email) so that `JwtAuthenticationFilter` can use it as the JWT subject. Set both in the builder:

```java
return org.springframework.security.core.userdetails.User.builder()
    .username(user.getId())     // userId used as JWT subject
    .password(user.getPassword())
    .roles("CREATOR")
    .build();
```

In `UserDetailsServiceImpl.loadUserByUsername(email)`: look up by email (since that's how login works), but `AuthService.login()` must load the user by email separately to do the lockout pre-check before calling `authenticationManager.authenticate()`.

### Project Structure Notes

- Alignment with existing structure:
  - `src/main/java/com/samuel/app/creator/dto/` — new DTOs here
  - `src/main/java/com/samuel/app/creator/service/` — `AuthService`, `UserDetailsServiceImpl`
  - `src/main/java/com/samuel/app/shared/util/` — `JwtUtil` (create `util` subfolder — it does not exist yet)
  - `src/main/java/com/samuel/app/shared/filter/` — `JwtAuthenticationFilter` (alongside existing `CorrelationIdFilter`)
  - `src/main/java/com/samuel/app/exceptions/` — `InvalidTokenException`
  - `src/main/resources/db/migration/` — `V3__add_login_lockout_to_users.sql`
- Tests mirror source structure in `src/test/java/com/samuel/app/`

### "Redirect to creator dashboard" — Out of Scope

The epics AC mentioning redirect is a **frontend concern**. This backend story is done when the API returns a correctly shaped `AuthResponse`. The Angular frontend (not yet started) will handle the redirect. Do NOT implement any redirect logic in the Spring Boot application.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 1.3]
- [Source: _bmad-output/planning-artifacts/architecture.md#Authentication System]
- [Source: _bmad-output/implementation-artifacts/1-2-user-registration-with-email-verification.md#Dev Notes]
- [Source: _bmad/bmm/config.yaml]
- [Party mode review: Winston (Architect), Amelia (Developer), John (PM) — 2026-04-21]

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.6

### Debug Log References

- `UserDetailsServiceImpl.loadUserByUsername()` is called by Spring's `DaoAuthenticationProvider` with email during login; added `loadUserById()` for the JWT filter which uses userId (JWT subject) instead of email.
- jjwt 0.12.x API breaking change confirmed: used `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` — the 0.11.x `parserBuilder()` path was intentionally avoided.
- `@WebMvcTest` auto-configures a minimal Spring Security context; `@AutoConfigureMockMvc(addFilters = false)` correctly disables the full filter chain so controller tests are not blocked.

### Completion Notes List

- All 13 tasks implemented; 44 tests pass (0 failures, 0 errors).
- JWT access tokens: HS256-signed, 15 min TTL, subject = userId, claim "email".
- Refresh tokens: opaque UUID, stored in Redis as `refresh:{token}` → userId, 7-day TTL. Token rotation on refresh.
- Lockout: configurable threshold (default 5) and duration (default 15 min) via `app.auth.*`; pre-checked in `AuthService.login()` before `AuthenticationManager.authenticate()` to ensure lockout fires without touching the auth provider.
- `Clock` bean injected in `SecurityConfig`; fixed `Clock` used in all tests for deterministic lockout assertions.
- `GlobalExceptionHandler` extended with handlers for `DisabledException` (403), `LockedException` (401), `BadCredentialsException` (401 with generic message), `InvalidTokenException` (401).

### File List

- `pom.xml`
- `src/main/resources/db/migration/V3__add_login_lockout_to_users.sql`
- `src/main/java/com/samuel/app/creator/model/User.java`
- `src/main/resources/application-dev.yml`
- `src/test/resources/application-test.yml`
- `src/main/java/com/samuel/app/creator/dto/LoginRequest.java`
- `src/main/java/com/samuel/app/creator/dto/AuthResponse.java`
- `src/main/java/com/samuel/app/creator/dto/RefreshRequest.java`
- `src/main/java/com/samuel/app/creator/dto/LogoutRequest.java`
- `src/main/java/com/samuel/app/shared/util/JwtUtil.java`
- `src/main/java/com/samuel/app/creator/service/UserDetailsServiceImpl.java`
- `src/main/java/com/samuel/app/exceptions/InvalidTokenException.java`
- `src/main/java/com/samuel/app/creator/service/AuthService.java`
- `src/main/java/com/samuel/app/shared/filter/JwtAuthenticationFilter.java`
- `src/main/java/com/samuel/app/config/SecurityConfig.java`
- `src/main/java/com/samuel/app/creator/controller/AuthController.java`
- `src/main/java/com/samuel/app/shared/controller/GlobalExceptionHandler.java`
- `src/test/java/com/samuel/app/creator/service/AuthServiceTest.java`
- `src/test/java/com/samuel/app/shared/util/JwtUtilTest.java`
- `src/test/java/com/samuel/app/shared/filter/JwtAuthenticationFilterTest.java`
- `src/test/java/com/samuel/app/creator/controller/AuthControllerTest.java`

### Change Log

- Implemented JWT-based authentication: login, refresh, logout endpoints (Date: 2026-04-23)
- Added jjwt 0.12.6 and spring-boot-starter-data-redis dependencies to pom.xml (Date: 2026-04-23)
- Added V3 Flyway migration for failed_login_attempts and locked_until columns (Date: 2026-04-23)
- Extended User entity with lockout fields and SecurityConfig with AuthenticationManager, JwtAuthenticationFilter, Clock bean (Date: 2026-04-23)
- Added GlobalExceptionHandler entries for DisabledException, LockedException, BadCredentialsException, InvalidTokenException (Date: 2026-04-23)
- 44 tests pass: AuthServiceTest (9), JwtUtilTest (5), JwtAuthenticationFilterTest (3), AuthControllerTest (14), plus all prior tests (Date: 2026-04-23)
