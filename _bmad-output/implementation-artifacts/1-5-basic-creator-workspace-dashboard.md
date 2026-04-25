# Story 1.5: Basic Creator Workspace Dashboard

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an authenticated creator with a profile,
I want to access my basic creator workspace dashboard,
So that I can view my account overview and navigate to platform features.

## Acceptance Criteria

1. **AC1 — Dashboard loads with creator context**
   **Given** I am logged in as an authenticated creator with a completed profile
   **When** I access `GET /api/v1/dashboard`
   **Then** the response is HTTP 200 with `ApiResponse` containing `DashboardResponse`
   **And** the dashboard displays my creator name from the profile `displayName`
   **And** the dashboard loads within 2 seconds

2. **AC2 — Profile summary information displayed**
   **Given** I am logged in with a creator profile
   **When** I view the dashboard
   **Then** I see my profile summary: `displayName`, `creatorCategory`, `profileImageUrl` (null if not set), `profileCompletionStatus`
   **And** I see my account creation date (`memberSince`) in ISO-8601 UTC format
   **And** if `profileImageUrl` is null, the field is returned as null in the response; the client is responsible for rendering a placeholder

3. **AC3 — Navigation options reflect current system capabilities**
   **Given** I am viewing my dashboard
   **When** I see the navigation menu
   **Then** I see "Connect Platforms" navigation option with status "Coming in Epic 2"
   **And** I see "Content Management" navigation option with status "Coming in Epic 3"
   **And** I see "Revenue Analytics" navigation option with status "Coming in Epic 4"
   **And** I can access "Profile Management" as an active navigation item (`enabled: true`)
   **And** "Account Settings" is present as an active navigation item (`enabled: true`) with `url: /api/v1/settings` (stub endpoint, returns HTTP 200 for future implementation)
   **And** disabled navigation items have `enabled: false` and include a non-empty `statusMessage`

4. **AC4 — Revenue metrics placeholders displayed**
   **Given** I am viewing my dashboard
   **When** I look at the revenue section
   **Then** I see placeholder sections for "Total Revenue", "Platform Breakdown", "Monthly Trends"
   **And** each placeholder shows "Data will be available when you connect platforms (Epic 4)"
   **And** placeholder areas maintain responsive layout for future data integration

5. **AC5 — Account settings and profile editing access**
   **Given** I am logged in with a creator profile
   **When** I request the URL from `accountActions.accountSettingsUrl`
   **Then** the endpoint `/api/v1/settings` returns HTTP 200 with a stub response indicating future implementation
   **And** when I use `accountActions.editProfileUrl`, I reach the profile editing endpoint from Story 1.4
   **And** after a profile update, the next dashboard request returns fresh profile data (cache is evicted on profile update)

6. **AC6 — Dashboard response is layout-agnostic and client-renderable**
   **Given** I access `GET /api/v1/dashboard`
   **When** I receive the `DashboardResponse`
   **Then** the response contains structured, layout-neutral JSON with no embedded markup or inline styles
   **And** each `NavigationItem` includes `enabled`, `statusMessage`, and `icon` fields to support rendering on any client or viewport
   **And** the API response contract does not vary based on the caller's device or screen size

7. **AC7 — Secure logout functionality**
   **Given** I am logged in and viewing the dashboard
   **When** I click "Log Out" 
   **Then** my JWT tokens are invalidated (calling existing logout endpoint from Story 1.3)
   **And** subsequent requests to dashboard endpoints return HTTP 401

8. **AC8 — Security and authentication validation**
   **Given** I attempt to access the dashboard
   **When** I am not authenticated (no valid JWT token)
   **Then** the response is HTTP 401 with authentication error
   **And** when my profile is missing, the response is HTTP 404 with message "Profile setup required"

9. **AC9 — Welcome message personalization**
   **Given** I have a completed creator profile
   **When** I view my dashboard
   **Then** I see a personalized welcome message: "Welcome back, [displayName]!"
   **And** the response includes `currentDateTime` as an ISO-8601 UTC timestamp
   **And** if I'm a new user (profile created < 7 days), I see "Welcome to your new creator workspace, [displayName]!"

10. **AC10 — Navigation and action icons are constrained to a defined set**
    **Given** I receive the dashboard response
    **When** I inspect navigation items
    **Then** each `NavigationItem` includes an `icon` field with a value from the allowed set: `PROFILE`, `SETTINGS`, `LOGOUT`, `CONNECT_PLATFORMS`, `CONTENT`, `REVENUE`
    **And** no value outside this defined set is permitted in the `icon` field
    **And** the icon identifiers are case-sensitive strings; the client is responsible for mapping them to visual icons

## Tasks / Subtasks

- [x] **Task 1: Create Dashboard DTOs (AC: 1, 2, 4, 9)**
  - [x] Create `com.samuel.app.creator.dto.DashboardResponse` record:
    - `String welcomeMessage` (personalized greeting)
    - `String currentDateTime` (ISO-8601 UTC timestamp, e.g. `Instant.now().toString()`)
    - `ProfileSummary profileSummary` (nested object)
    - `NavigationMenu navigationMenu` (nested object)
    - `RevenuePlaceholder revenuePlaceholder` (nested object)
    - `AccountActions accountActions` (nested object)
  - [x] Create `com.samuel.app.creator.dto.ProfileSummary` record:
    - `String displayName`, `String creatorCategory`, `String profileImageUrl`
    - `String memberSince` (ISO-8601 UTC), `boolean isNewUser`, `String profileCompletionStatus`
  - [x] Create `com.samuel.app.creator.dto.NavigationMenu` record:
    - `List<NavigationItem> items` 
  - [x] Create `com.samuel.app.creator.dto.NavigationItem` record:
    - `String label`, `String url`, `boolean enabled`, `String statusMessage`, `String icon`
    - `icon` must be one of: `PROFILE`, `SETTINGS`, `LOGOUT`, `CONNECT_PLATFORMS`, `CONTENT`, `REVENUE` (enforce via `NavigationIcon` enum or `@Pattern` validation)
  - [x] Create `com.samuel.app.creator.dto.RevenuePlaceholder` record:
    - `String totalRevenueMessage`, `String platformBreakdownMessage`, `String trendsMessage`
  - [x] Create `com.samuel.app.creator.dto.AccountActions` record:
    - `String accountSettingsUrl`, `String editProfileUrl`, `String logoutUrl`

- [x] **Task 2: Create DashboardService (AC: 1, 2, 3, 4, 5, 9)**
  - [x] Create `com.samuel.app.creator.service.DashboardService`
  - [x] Inject: `CreatorProfileService` (to get profile data)
  - [x] `DashboardResponse getDashboardData(String userId)`:
    - Fetch creator profile via `CreatorProfileService.getProfile(userId)`
    - Build `ProfileSummary` from profile data
    - Determine if user is new (profile created < 7 days ago)
    - Generate personalized welcome message
    - Build navigation menu with current Epic 1 capabilities (active) and future epics (disabled)
    - Create revenue placeholders with "Coming in Epic 4" messaging
    - Build account actions with appropriate URLs
    - Return complete `DashboardResponse`
  - [x] Handle cases where profile might be missing (throw `ResourceNotFoundException`)
  - [x] Add caching optimization for dashboard data (5-minute cache; cache must be evicted when the creator profile is updated via Story 1.4 `CreatorProfileService`)

- [x] **Task 3: Create DashboardController (AC: 1, 6, 7, 8)**
  - [x] Create `com.samuel.app.creator.controller.DashboardController`
  - [x] Inject: `DashboardService`
  - [x] `@GetMapping("/api/v1/dashboard")` → `ResponseEntity<ApiResponse<DashboardResponse>>`
    - Get authenticated user ID from JWT context (via `@AuthenticationPrincipal UserDetails`)
    - Call `DashboardService.getDashboardData(userId)`
    - Return HTTP 200 with `ApiResponse.success(dashboardData)`
    - Handle `ResourceNotFoundException` for missing profile → HTTP 404 with "Profile setup required"
  - [x] Add security annotations: `@PreAuthorize("isAuthenticated()")` — handled via `anyRequest().authenticated()` in SecurityConfig
  - [x] Add correlation ID logging for dashboard requests
  - [x] Add request/response logging for debugging

- [x] **Task 4: Update SecurityConfig for dashboard endpoint (AC: 7, 8)**
  - [x] Update `com.samuel.app.config.SecurityConfig`
  - [x] Ensure `/api/v1/dashboard` requires authentication (should work with existing JWT security)
  - [x] Verify dashboard endpoint is not in permitAll() list
  - [x] Test that unauthenticated requests to dashboard return 401

- [x] **Task 5: Add dashboard integration tests (AC: 1, 2, 3, 4, 5, 8)**
  - [x] Create `com.samuel.app.creator.controller.DashboardControllerTest`
  - [x] Test authenticated dashboard access returns 200 with correct data structure
  - [x] Test unauthenticated access returns 401
  - [x] Test dashboard with missing profile returns 404 "Profile setup required"
  - [x] Test welcome message personalization for new vs existing users
  - [x] Test navigation menu contains correct Epic status messaging
  - [x] Test revenue placeholders contain appropriate "Coming in Epic 4" messages
  - [x] Test profile summary contains expected creator profile data including `profileCompletionStatus`
  - [x] Test all `NavigationItem` `icon` values are from the allowed set (PROFILE, SETTINGS, LOGOUT, CONNECT_PLATFORMS, CONTENT, REVENUE)
  - [x] Mock `DashboardService` and test controller logic in isolation

- [x] **Task 6: Add dashboard service unit tests (AC: 2, 9)**
  - [x] Create `com.samuel.app.creator.service.DashboardServiceTest` 
  - [x] Test dashboard data building with complete profile
  - [x] Test new user detection (< 7 days) and welcome message generation
  - [x] Test existing user welcome message generation
  - [x] Test navigation menu building with correct Epic statuses and valid `icon` values
  - [x] Test that all icon values are from the allowed set (enforced via NavigationIcon enum — invalid values are compile-time impossible)
  - [x] Test revenue placeholder generation
  - [x] Test exception handling for missing profile
  - [x] Mock `CreatorProfileService` and verify proper interaction

## Dev Notes

- **Dashboard serves as entry point**: This dashboard is the central hub that creators see after login, providing orientation and navigation to all platform capabilities
- **Forward-compatible design**: Navigation and placeholders are designed to accommodate future Epic features without breaking changes
- **Security integration**: Leverages existing JWT authentication from Story 1.3 and profile management from Story 1.4
- **Performance considerations**: Dashboard data should be cacheable since profile information changes infrequently

### Project Structure Notes

- **Alignment with unified project structure**: Follows established `com.samuel.app.creator.{controller|service|dto}` pattern from Stories 1.2-1.4
- **Consistent with REST API patterns**: Uses same `ApiResponse` wrapper and HTTP status code conventions as previous stories
- **Integration points**: Builds on `CreatorProfileService` from Story 1.4, uses authentication context from Story 1.3
- **Testing patterns**: Follows same unit and integration testing patterns established in previous stories

### References

- [Source: epics.md#Story 1.5] - Complete acceptance criteria and user story definition
- [Source: architecture.md#Technical Stack] - Java 17, Spring Boot 3, JWT authentication patterns
- [Source: architecture.md#API Patterns] - REST endpoint structure and ApiResponse wrapper requirements
- [Source: 1-3-user-authentication-with-jwt-token-management.md#Security Context] - JWT authentication and user context extraction
- [Source: 1-4-creator-profile-creation-and-management.md#CreatorProfileService] - Profile data retrieval and DTOs
- [Source: prd.md#Success Criteria SC1] - Platform connection dashboard requirements and user experience goals

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.6 (GitHub Copilot)

### Debug Log References

- All 73 unit tests passed: `DashboardControllerTest` (10), `DashboardServiceTest` (16), plus all prior story tests.
- Pre-existing `AuthenticationApiContractTest` failures (3) are `@SpringBootTest` integration tests requiring live MySQL/Redis; unrelated to Story 1.5.
- Cache implementation: Caffeine (in-process, 5-min TTL) instead of Redis to avoid infrastructure dependency in tests — consistent with project's H2 test strategy.
- `NavigationIcon` enum enforces icon constraint at compile time; no runtime validation needed.

### Completion Notes List

- `NavigationIcon` enum created to enforce icon constraint (AC10) — compile-time safety over `@Pattern` string validation.
- `CacheConfig` added with Caffeine backend (5-min TTL per `dashboard` cache); `@EnableCaching` is in `CacheConfig` not `AppApplication`.
- `CreatorProfileService.updateProfile()` annotated with `@CacheEvict(value = DASHBOARD_CACHE, key = "#userId")` — satisfies AC5 cache eviction requirement.
- `GET /api/v1/settings` stub endpoint returns HTTP 200 in same `DashboardController` — satisfies AC3/AC5.
- Dashboard security covered by existing `anyRequest().authenticated()` in `SecurityConfig` — no modification needed (AC8).
- Profile completion logic: COMPLETE = bio + category + image; IN_PROGRESS = bio or category; INCOMPLETE = neither.

### File List

**New files:**
- `src/main/java/com/samuel/app/creator/dto/DashboardResponse.java`
- `src/main/java/com/samuel/app/creator/dto/ProfileSummary.java`
- `src/main/java/com/samuel/app/creator/dto/NavigationMenu.java`
- `src/main/java/com/samuel/app/creator/dto/NavigationItem.java`
- `src/main/java/com/samuel/app/creator/dto/NavigationIcon.java`
- `src/main/java/com/samuel/app/creator/dto/RevenuePlaceholder.java`
- `src/main/java/com/samuel/app/creator/dto/AccountActions.java`
- `src/main/java/com/samuel/app/creator/service/DashboardService.java`
- `src/main/java/com/samuel/app/creator/controller/DashboardController.java`
- `src/main/java/com/samuel/app/config/CacheConfig.java`
- `src/test/java/com/samuel/app/creator/controller/DashboardControllerTest.java`
- `src/test/java/com/samuel/app/creator/service/DashboardServiceTest.java`

**Modified files:**
- `pom.xml` — added `spring-boot-starter-cache` + `caffeine` dependencies
- `src/main/java/com/samuel/app/creator/service/CreatorProfileService.java` — added `@CacheEvict` on `updateProfile()`