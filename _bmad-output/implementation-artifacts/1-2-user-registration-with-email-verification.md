# Story 1.2: User Registration with Email Verification

Status: done

## Story

As a content creator,
I want to register for an account with email verification,
so that I can securely create my creator profile and access the platform.

## Acceptance Criteria

1. **Given** I am a new user visiting the registration endpoint
   **When** I submit a valid registration payload `{ email, password, confirmPassword }` to `POST /api/v1/auth/register`
   **Then** a new user record is created in the `users` table with `status = PENDING`
   **And** the password is encrypted using BCrypt before storage
   **And** a verification token (UUID) is stored in the `email_verifications` table with a 24-hour expiry
   **And** a verification email is sent to the provided address via JavaMailSender (Mailhog in dev)
   **And** the response is HTTP 201 with `ApiResponse<Void>` body `{ success: true, message: "Registration successful. Please check your email to verify your account." }`

2. **Given** I submit a registration request with an email that already exists
   **When** the service checks uniqueness in the `users` table
   **Then** the response is HTTP 409 with `ApiResponse<Void>` body `{ success: false, message: "Email address is already registered." }`

3. **Given** I submit a registration request with invalid data (blank email, invalid email format, password < 8 chars, or passwords do not match)
   **When** Spring Validation processes the request body
   **Then** the response is HTTP 400 with `ApiResponse<Void>` containing a descriptive validation error message

4. **Given** I have received a verification email
   **When** I call `GET /api/v1/auth/verify?token={token}` with a valid, unexpired token
   **Then** the associated user's `status` is updated from `PENDING` to `ACTIVE`
   **And** the token record is deleted from `email_verifications`
   **And** the response is HTTP 200 with `ApiResponse<Void>` body `{ success: true, message: "Email verified successfully. You can now log in." }`

5. **Given** I attempt email verification with an invalid or already-used token
   **When** the service looks up the token in `email_verifications`
   **Then** the response is HTTP 400 with `ApiResponse<Void>` body `{ success: false, message: "Invalid or expired verification token." }`

6. **Given** I attempt email verification with an expired token (older than 24 hours)
   **When** the service checks the `expires_at` column
   **Then** the expired token record is deleted
   **And** the response is HTTP 400 with `ApiResponse<Void>` body `{ success: false, message: "Invalid or expired verification token." }`

## Tasks / Subtasks

- [x] **Task 1: Add missing pom.xml dependencies (AC: 1, 3)**
  - [x] Add `spring-boot-starter-mail` to pom.xml (for `JavaMailSender`)
  - [x] Add `spring-boot-starter-validation` to pom.xml (for `@Valid`, `@Email`, `@NotBlank`)

- [x] **Task 2: Add mail configuration to application-dev.yml (AC: 1)**
  - [x] Add `spring.mail.host: localhost`, `spring.mail.port: 1025`, `spring.mail.properties.mail.smtp.auth: false`, `spring.mail.properties.mail.smtp.starttls.enable: false`
  - [x] Add `app.mail.from: noreply@spring-project.local` (used as sender address)
  - [x] Add `app.frontend.base-url: http://localhost:4200` (used to build verify link in email body)

- [x] **Task 3: Add BCryptPasswordEncoder bean to SecurityConfig.java (AC: 1)**
  - [x] Add `@Bean public BCryptPasswordEncoder passwordEncoder()` to `com.samuel.app.config.SecurityConfig`
  - [x] NOTE: Do NOT add `AuthenticationManager` or `UserDetailsService` beans here — those belong in Story 1.3

- [x] **Task 4: Add V2 Flyway migration (AC: 1)**
  - [x] Create `src/main/resources/db/migration/V2__create_email_verifications_table.sql`
  - [x] Schema: `id VARCHAR(36) PK`, `user_id VARCHAR(36) NOT NULL FK → users(id) ON DELETE CASCADE`, `token VARCHAR(36) NOT NULL UNIQUE`, `expires_at DATETIME(6) NOT NULL`, `created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`

- [x] **Task 5: Create DTOs (AC: 1, 3)**
  - [x] Create `com.samuel.app.creator.dto.RegistrationRequest` (Java record) with fields: `@Email @NotBlank String email`, `@NotBlank @Size(min=8) String password`, `@NotBlank String confirmPassword`
  - [x] Custom cross-field validation: confirm passwords match — validate in service layer (throw `IllegalArgumentException`)

- [x] **Task 6: Create EmailVerification entity (AC: 1, 4, 5, 6)**
  - [x] Create `com.samuel.app.creator.model.EmailVerification` JPA entity mapping to `email_verifications` table
  - [x] Fields: `String id`, `String userId`, `String token`, `LocalDateTime expiresAt`, `LocalDateTime createdAt`
  - [x] Use `@Column` annotations to match exact DB column names (`user_id`, `expires_at`, `created_at`)

- [x] **Task 7: Create UserRepository (AC: 1, 2)**
  - [x] Create `com.samuel.app.creator.repository.UserRepository extends JpaRepository<User, String>`
  - [x] Add method: `Optional<User> findByEmail(String email)`
  - [x] Add method: `boolean existsByEmail(String email)`

- [x] **Task 8: Create EmailVerificationRepository (AC: 4, 5, 6)**
  - [x] Create `com.samuel.app.creator.repository.EmailVerificationRepository extends JpaRepository<EmailVerification, String>`
  - [x] Add method: `Optional<EmailVerification> findByToken(String token)`
  - [x] Add method: `void deleteByToken(String token)`

- [x] **Task 9: Create EmailService (AC: 1)**
  - [x] Create `com.samuel.app.shared.service.EmailService` (injectable Spring `@Service`)
  - [x] Inject `JavaMailSender` and `@Value("${app.mail.from}") String fromAddress`, `@Value("${app.frontend.base-url}") String frontendBaseUrl`
  - [x] Method: `void sendVerificationEmail(String toEmail, String token)` — builds plain-text email with verification link: `{frontendBaseUrl}/auth/verify?token={token}`
  - [x] Use `SimpleMailMessage` (not MimeMessage) for this story — no HTML email needed yet

- [x] **Task 10: Create UserRegistrationService (AC: 1–6)**
  - [x] Create `com.samuel.app.creator.service.UserRegistrationService`
  - [x] Inject: `UserRepository`, `EmailVerificationRepository`, `BCryptPasswordEncoder`, `EmailService`
  - [x] `register(RegistrationRequest request)`:
    1. Validate `password.equals(confirmPassword)`, else throw `IllegalArgumentException("Passwords do not match.")`
    2. `if (userRepository.existsByEmail(email))` throw new custom `EmailAlreadyExistsException` (HTTP 409) OR throw `IllegalStateException` (caught by a new handler)
    3. Generate `String userId = UUID.randomUUID().toString()`
    4. Create `User`, set `id=userId`, `email`, `password=bCrypt.encode(password)`, `status=PENDING`
    5. `userRepository.save(user)`
    6. Generate `String token = UUID.randomUUID().toString()`
    7. Create `EmailVerification`, set all fields, `expiresAt = LocalDateTime.now().plusHours(24)`
    8. `emailVerificationRepository.save(verification)`
    9. `emailService.sendVerificationEmail(email, token)`
  - [x] `verifyEmail(String token)`:
    1. Find token: `emailVerificationRepository.findByToken(token)` — if empty throw `IllegalArgumentException("Invalid or expired verification token.")`
    2. If `verification.getExpiresAt().isBefore(LocalDateTime.now())`: delete it, throw `IllegalArgumentException("Invalid or expired verification token.")`
    3. Load user by `verification.getUserId()`
    4. Set `user.setStatus(UserStatus.ACTIVE)`, `userRepository.save(user)`
    5. `emailVerificationRepository.deleteByToken(token)`

- [x] **Task 11: Add EmailAlreadyExistsException and its handler (AC: 2)**
  - [x] Create `com.samuel.app.exceptions.EmailAlreadyExistsException extends RuntimeException`
  - [x] Add handler in `GlobalExceptionHandler`: `@ExceptionHandler(EmailAlreadyExistsException.class)` returning HTTP 409 `ApiResponse.error("Email address is already registered.")`

- [x] **Task 12: Add MethodArgumentNotValidException handler to GlobalExceptionHandler (AC: 3)**
  - [x] Add `@ExceptionHandler(MethodArgumentNotValidException.class)` in `GlobalExceptionHandler`
  - [x] Extract first `FieldError` message and return HTTP 400 `ApiResponse.error(fieldError.getDefaultMessage())`

- [x] **Task 13: Create AuthController (AC: 1–6)**
  - [x] Create `com.samuel.app.creator.controller.AuthController`
  - [x] `@RequestMapping("/auth")` — already allowed by SecurityConfig (`/auth/**` is `permitAll`)
  - [x] `POST /auth/register` — accepts `@Valid @RequestBody RegistrationRequest`, returns `ResponseEntity<ApiResponse<Void>>`
    - Calls `userRegistrationService.register(request)`, returns HTTP 201 `ApiResponse.ok(null)` with message
  - [x] `GET /auth/verify` — accepts `@RequestParam String token`, returns `ResponseEntity<ApiResponse<Void>>`
    - Calls `userRegistrationService.verifyEmail(token)`, returns HTTP 200 `ApiResponse.ok(null)` with success message

- [x] **Task 14: Write unit tests (AC: 1–6)**
  - [x] `src/test/java/com/samuel/app/creator/service/UserRegistrationServiceTest.java`
    - Mock: `UserRepository`, `EmailVerificationRepository`, `BCryptPasswordEncoder`, `EmailService`
    - Tests: successful registration, duplicate email → exception, password mismatch → exception, successful verify, invalid token → exception, expired token → exception + deleted
  - [x] `src/test/java/com/samuel/app/creator/controller/AuthControllerTest.java`
    - Use `@WebMvcTest(AuthController.class)` + `@MockBean UserRegistrationService`
    - Tests: POST /auth/register valid payload → 201, invalid payload → 400, duplicate email → 409, GET /auth/verify valid token → 200, invalid token → 400

## Dev Notes

### Critical: Missing pom.xml Dependencies

Add to pom.xml **before** any other task — the project will not compile without these:

```xml
<!-- Bean Validation for @Valid, @Email, @NotBlank, @Size -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- JavaMailSender for email verification -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

### Critical: BCryptPasswordEncoder Bean

Story 1.1 left the SecurityConfig without a `PasswordEncoder` bean — this MUST be added in this story:

```java
// In com.samuel.app.config.SecurityConfig
@Bean
public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Do NOT add `AuthenticationManager`, `UserDetailsService`, or `AuthenticationProvider` beans here — those are Story 1.3's scope. Story 1.2 only needs BCryptPasswordEncoder.

### Critical: SecurityConfig context path

The server context path is `/api/v1` (set in `application.yml`). The `SecurityConfig` rule `.requestMatchers("/auth/**").permitAll()` therefore permits all requests starting at `/api/v1/auth/**`. The `AuthController` must be mapped to `/auth` (not `/api/v1/auth`), as Spring Security matcher paths are relative to the servlet context path.

### User Entity Mapping

`User.java` in `com.samuel.app.creator.model` is already present with the correct `@Entity @Table(name = "users")` and `UserStatus` enum. **Do not recreate it.** It currently lacks `@Column` annotations — columns map by JPA default naming (fieldName → field_name), which already matches the DB schema. You may add `@Column` annotations for clarity but it's optional for this story.

The `User.java` does NOT have `@GeneratedValue` on `id` — the service must manually set `user.setId(UUID.randomUUID().toString())` before saving.

### EmailVerification Entity

Must map to the new `V2__create_email_verifications_table.sql` migration. Key column mappings:

| Java field     | DB column     | Type                |
|----------------|---------------|---------------------|
| `id`           | `id`          | `VARCHAR(36)` PK    |
| `userId`       | `user_id`     | `VARCHAR(36)` FK    |
| `token`        | `token`       | `VARCHAR(36)` UNIQUE|
| `expiresAt`    | `expires_at`  | `DATETIME(6)`       |
| `createdAt`    | `created_at`  | `DATETIME(6)`       |

Use `@Column(name = "user_id")` etc. since Java camelCase → snake_case mapping requires explicit annotation in this project.

### GlobalExceptionHandler Additions

The existing `GlobalExceptionHandler` must have **two new handlers added** for this story to work correctly:

1. `EmailAlreadyExistsException` → HTTP 409
2. `MethodArgumentNotValidException` → HTTP 400 (extract first field error message)

The existing `IllegalArgumentException` handler returning HTTP 400 covers the password mismatch and invalid/expired token cases.

### Email Verification Flow (Mailhog in dev)

Mailhog is already in docker-compose (`smtp: 1025`, `web UI: 8025`). The `application-dev.yml` needs the mail stanza added:

```yaml
spring:
  mail:
    host: localhost
    port: 1025
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false

app:
  mail:
    from: noreply@spring-project.local
  frontend:
    base-url: http://localhost:4200
```

The verification email body should contain a human-readable link:
```
Please verify your email by clicking: http://localhost:4200/auth/verify?token={uuid}
This link expires in 24 hours.
```

### Test Profile Considerations

`application-test.yml` uses H2 with `ddl-auto: create-drop` and `flyway.enabled: false`. This means:
- JPA auto-creates tables from entities in tests — no Flyway migration runs
- H2 `MODE=MySQL` is already set — compatible with `ENUM` types IF you use `@Enumerated(EnumType.STRING)` (the existing `User.java` already does this)
- For `@WebMvcTest` controller tests: Spring Security auto-configures, so you may need `@WithMockUser` or disable security for the `/auth/**` endpoints using `@AutoConfigureMockMvc(addFilters = false)` to avoid 403s in isolation tests

### Package Structure for New Files

```
src/main/java/com/samuel/app/
├── config/
│   └── SecurityConfig.java              ← MODIFY: add BCryptPasswordEncoder bean
├── creator/
│   ├── controller/
│   │   ├── UserController.java           ← DO NOT TOUCH (stub, unrelated)
│   │   └── AuthController.java           ← CREATE NEW
│   ├── dto/
│   │   └── RegistrationRequest.java      ← CREATE NEW
│   ├── model/
│   │   ├── User.java                     ← DO NOT TOUCH (already exists)
│   │   └── EmailVerification.java        ← CREATE NEW
│   ├── repository/
│   │   ├── UserRepository.java           ← CREATE NEW
│   │   └── EmailVerificationRepository.java ← CREATE NEW
│   └── service/
│       └── UserRegistrationService.java  ← CREATE NEW
├── exceptions/
│   ├── ResourceNotFoundException.java   ← DO NOT TOUCH
│   ├── ValidationException.java         ← DO NOT TOUCH
│   └── EmailAlreadyExistsException.java ← CREATE NEW
└── shared/
    ├── controller/
    │   ├── ApiResponse.java              ← DO NOT TOUCH
    │   └── GlobalExceptionHandler.java  ← MODIFY: add 2 new exception handlers
    └── service/
        └── EmailService.java            ← CREATE NEW

src/main/resources/
├── application-dev.yml                  ← MODIFY: add mail stanza
└── db/migration/
    └── V2__create_email_verifications_table.sql ← CREATE NEW

pom.xml                                  ← MODIFY: add 2 dependencies
```

### Story 1.3 Forward Compatibility

Story 1.3 will implement full JWT token management and `UserDetailsServiceImpl`. This story must NOT:
- Implement `UserDetailsService` or `UserDetailsServiceImpl`
- Add `AuthenticationManager` bean to SecurityConfig
- Add login endpoint (`POST /auth/login`) — that's 1.3
- Add JWT token generation logic — that's 1.3

The `JwtAuthenticationFilter.java` stub (`shared/security/JwtAuthenticationFilter.java`) should be left untouched — it is a pass-through stub and will not interfere.

### API Endpoint Summary

| Method | Path                    | Auth    | Request Body              | Response                          |
|--------|-------------------------|---------|---------------------------|-----------------------------------|
| POST   | `/api/v1/auth/register` | Public  | `RegistrationRequest` JSON| 201 `ApiResponse<Void>` (success) |
| GET    | `/api/v1/auth/verify`   | Public  | `?token=<uuid>`           | 200 `ApiResponse<Void>` (success) |

### Project Structure Notes

- All new files follow the feature-module pattern: `com.samuel.app.{module}.{layer}`
- Creator-domain classes go under `com.samuel.app.creator.*`
- Shared/cross-cutting services go under `com.samuel.app.shared.service.*`
- No existing files are broken: `UserController.java` (stub) and `JwtAuthenticationFilter.java` (stub) remain untouched

### References

- [Source: architecture.md#Project Structure Patterns] — Feature module organization
- [Source: architecture.md#Authentication & Security] — JWT + BCrypt patterns
- [Source: architecture.md#API Response Pattern] — `ApiResponse<T>` wrapper
- [Source: architecture.md#Naming Patterns] — REST endpoint conventions (`/api/v1/auth/register`)
- [Source: architecture.md#Requirements to Structure Mapping#Cross-Cutting Authentication] — `shared/security/`, `config/SecurityConfig.java`
- [Source: epics.md#Story 1.2] — Acceptance criteria and user story
- [Source: planning-artifacts/implementation-artifacts/1-1-jangular-cli-project-setup-and-database-configuration.md] — Completed story tasks and patterns established

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.6

### Debug Log References

_No blockers encountered._

### Completion Notes List

- Implemented full user registration flow: BCrypt password hashing, PENDING status, UUID-based verification token with 24h expiry, plain-text email via JavaMailSender (Mailhog in dev).
- Implemented email verification flow: token lookup, expiry check (expired token deleted), user status updated to ACTIVE, token deleted on success.
- Added `EmailAlreadyExistsException` (HTTP 409) and `MethodArgumentNotValidException` handler (HTTP 400 first field error) to `GlobalExceptionHandler`.
- `AuthControllerTest` required `@AutoConfigureMockMvc(addFilters = false)` to bypass Spring Security in `@WebMvcTest` isolation — consistent with Dev Notes guidance.
- Fixed `application-test.yml` duplicate `spring:` key and added `spring.mail` + `app.mail`/`app.frontend` properties for full context load in `AppApplicationTests`.
- 19 tests pass, 0 failures, 0 errors. BUILD SUCCESS.

### File List

- `pom.xml` (modified — added `spring-boot-starter-validation`, `spring-boot-starter-mail`)
- `src/main/resources/application-dev.yml` (modified — added `spring.mail` stanza and `app.mail`/`app.frontend` properties)
- `src/main/java/com/samuel/app/config/SecurityConfig.java` (modified — added `BCryptPasswordEncoder` bean)
- `src/main/resources/db/migration/V2__create_email_verifications_table.sql` (created)
- `src/main/java/com/samuel/app/creator/dto/RegistrationRequest.java` (created)
- `src/main/java/com/samuel/app/creator/model/EmailVerification.java` (created)
- `src/main/java/com/samuel/app/creator/repository/UserRepository.java` (created)
- `src/main/java/com/samuel/app/creator/repository/EmailVerificationRepository.java` (created)
- `src/main/java/com/samuel/app/shared/service/EmailService.java` (created)
- `src/main/java/com/samuel/app/exceptions/EmailAlreadyExistsException.java` (created)
- `src/main/java/com/samuel/app/creator/service/UserRegistrationService.java` (created)
- `src/main/java/com/samuel/app/shared/controller/GlobalExceptionHandler.java` (modified — added `EmailAlreadyExistsException` and `MethodArgumentNotValidException` handlers)
- `src/main/java/com/samuel/app/creator/controller/AuthController.java` (created)
- `src/test/java/com/samuel/app/creator/service/UserRegistrationServiceTest.java` (created)
- `src/test/java/com/samuel/app/creator/controller/AuthControllerTest.java` (created)
- `src/test/resources/application-test.yml` (modified — added `spring.mail` and `app.mail`/`app.frontend` properties)

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-04-21 | Implemented Story 1.2: User Registration with Email Verification. Created AuthController, UserRegistrationService, EmailService, EmailVerification entity, UserRepository, EmailVerificationRepository, RegistrationRequest DTO, EmailAlreadyExistsException. Added V2 Flyway migration, mail config, BCryptPasswordEncoder bean, and two new GlobalExceptionHandler handlers. 19 tests pass. | Dev Agent (Claude Sonnet 4.6) |

### Review Findings

- [x] [Review][Patch] Remove ahead-of-scope pom.xml dependencies — remove JWT (`jjwt-*`), Resilience4j (`resilience4j-spring-boot3`), and Redis (`spring-boot-starter-data-redis`); defer to Stories 1.3 / Epic 2 [`pom.xml`]
- [x] [Review][Patch] Remove `JwtAuthenticationFilter` stub — out of story scope per Dev Notes; remove until Story 1.3 [`src/main/java/com/samuel/app/shared/security/JwtAuthenticationFilter.java`]
- [x] [Review][Patch] CORS allowed origin `localhost:3000` does not match frontend base URL `localhost:4200` [`src/main/java/com/samuel/app/config/SecurityConfig.java`]
- [x] [Review][Patch] `UserController` leftover test endpoints in production code — `/users/testDenied` and `/users/testPermit` are not part of story scope [`src/main/java/com/samuel/app/creator/controller/UserController.java`]
- [x] [Review][Patch] Dockerfile copies surefire test reports into production image — leaks class and test method names [`Dockerfile:24`]
- [x] [Review][Patch] `GlobalExceptionHandler` catchall returns HTTP 500 for framework errors — malformed JSON body and missing `?token=` param both return 500 instead of 400 [`src/main/java/com/samuel/app/shared/controller/GlobalExceptionHandler.java`]
- [x] [Review][Patch] `docker-compose.yml` missing `SPRING_PROFILES_ACTIVE=dev` — `application-dev.yml` is never loaded without it, so mail config and Redis settings are absent [`docker-compose.yml`]
- [x] [Review][Defer] TOCTOU race in `verifyEmail` — same token usable concurrently under `READ_COMMITTED` isolation [`src/main/java/com/samuel/app/creator/service/UserRegistrationService.java`] — deferred, pre-existing
- [x] [Review][Defer] `EmailVerification.createdAt` set manually in service — fragile if entity is reused without setting field [`src/main/java/com/samuel/app/creator/model/EmailVerification.java`] — deferred, pre-existing
