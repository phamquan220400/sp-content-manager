# Deferred Work

## Deferred from: code review of 1-1-jangular-cli-project-setup-and-database-configuration (2026-04-21)

- **Redis auto-config not disabled in test profile** — `spring-boot-starter-data-redis` is on the classpath; `application-test.yml` points to `localhost:6379` without disabling auto-config. In CI without a Redis sidecar this may cause flaky context loads. Fix by adding `spring.data.redis.repositories.enabled=false` or mocking the Redis connection in tests.
- **User entity missing `created_at`/`updated_at` mapping** — V1 migration creates these columns but the `User` entity has no corresponding fields. `ddl-auto=validate` won't fail, but entity/schema are misaligned. Story 1.2 should add `@CreationTimestamp`/`@UpdateTimestamp` annotations when extending the User entity.
- **CORS `allowedOrigins` hardcoded to `localhost:3000`** — `SecurityConfig.corsConfigurationSource()` uses a hardcoded origin list. This will block all non-local frontends. Needs to be backed by an env var (e.g., `${CORS_ALLOWED_ORIGINS:http://localhost:3000}`) before any staging or production deployment.
- **`GlobalExceptionHandler` missing Spring validation exception handlers** — `MethodArgumentNotValidException` and `HttpMessageNotReadableException` are caught by the generic `Exception` handler and return HTTP 500 instead of 400. Add specific `@ExceptionHandler` cases as form/request validation is introduced in later stories.

## Deferred from: code review of 1-2-user-registration-with-email-verification (2026-04-21)

- **TOCTOU race in `verifyEmail`** — Under `READ_COMMITTED` isolation, two concurrent requests with the same token can both pass the expiry check and both return HTTP 200. Requires serializable isolation or optimistic locking (e.g., `@Version` on `EmailVerification`). Low priority for single-server dev; revisit before multi-instance deployment.
- **`EmailVerification.createdAt` set manually in service** — The `createdAt` field is manually assigned in `UserRegistrationService.register()`. If the entity is ever constructed elsewhere without setting this field, the `NOT NULL` DB constraint will throw with no application-level guard. Consider adding `@PrePersist` or relying on `@Column(insertable = false, updatable = false)` with DB default.

## Deferred from: code review of 1-3-user-authentication-with-jwt-token-management (2026-04-25)

- **`handleRuntimeException` uses fragile string matching for ServiceUnavailable** — `GlobalExceptionHandler` detects Redis errors by checking if `ex.getMessage()` contains "service temporarily unavailable". A dedicated `ServiceUnavailableException` class would be type-safe and more maintainable.
- **Token rotation not atomic** — In `AuthService.refreshToken()`, if `redisTemplate.delete(oldKey)` fails silently (exception swallowed), both the old and new refresh tokens remain valid simultaneously, undermining token rotation security. Requires a distributed transaction, Lua script, or circuit breaker pattern to make this operation atomic.
