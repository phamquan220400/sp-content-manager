---
title: 'Fix TikTok OAuth Callback Returning HTTP 400'
type: 'bugfix'
created: '2026-04-29'
status: 'ready-for-dev'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The TikTok OAuth callback endpoint (`/api/v1/platforms/tiktok/callback`) returns HTTP 400. Three stacked bugs prevent the OAuth flow from completing: the callback is not in SecurityConfig's permitAll, the controller has a double `/api/v1` prefix that misroutes all platform endpoints, and the callback handler has no error path for TikTok error redirects (e.g. denied scope), causing Spring to throw `MissingServletRequestParameterException` → 400. A deprecated scope also triggers error callbacks unnecessarily.

**Approach:** Fix the security permit list, correct the controller base mapping to remove the duplicated context-path prefix, add `required = false` error handling to the callback handler, and remove the deprecated `user.info.basic` scope from the auth URL.

## Boundaries & Constraints

**Always:**
- Keep `user.info.profile` and `user.info.stats` scopes (needed for `display_name` and `follower_count`).
- Security matchers must use paths as seen by Spring Security (after context-path `/api/v1` is stripped).
- Update all affected MockMvc test paths to reflect the corrected controller mapping.
- Do not modify DashboardController or CreatorProfileController — deferred separately.

**Ask First:** Any change to the TikTok token exchange or user info API call beyond removing the deprecated scope.

**Never:** Change YouTube's OAuth flow behaviour — only apply path/security fixes that mirror the same pattern.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path callback | `?code=abc&state=xyz` (valid state in Redis) | 200 with `PlatformConnectionResponse` | N/A |
| TikTok error callback (denied/invalid scope) | `?error=access_denied&error_description=...` (no `code`) | 400 with message from TikTok `error_description` | Return `ApiResponse.error(errorDescription)` |
| Expired/invalid state | `?code=abc&state=expired` | 503 via `PlatformConnectionException` handler | Existing handler unchanged |
| Missing both code and error | `?` (no params) | 400 with descriptive message | Return `ApiResponse.error("Missing OAuth parameters")` |

</frozen-after-approval>

## Code Map

- `src/main/java/com/samuel/app/config/SecurityConfig.java` -- add `/platforms/tiktok/callback` and `/platforms/youtube/callback` to permitAll
- `src/main/java/com/samuel/app/platform/controller/PlatformConnectionController.java` -- fix `@RequestMapping("/api/v1/platforms")` → `@RequestMapping("/platforms")`; add error handling to TikTok (and YouTube) callback params
- `src/main/java/com/samuel/app/platform/service/TikTokConnectionService.java` -- remove deprecated `user.info.basic` scope from auth URL
- `src/test/java/com/samuel/app/platform/controller/PlatformConnectionControllerTikTokTest.java` -- update MockMvc paths from `/api/v1/platforms/...` → `/platforms/...`; add test for error callback
- `src/test/java/com/samuel/app/platform/controller/PlatformConnectionControllerTest.java` -- update MockMvc paths from `/api/v1/platforms/...` → `/platforms/...`

## Tasks & Acceptance

**Execution:**
- [ ] `src/main/java/com/samuel/app/config/SecurityConfig.java` -- Add `.requestMatchers("/platforms/tiktok/callback", "/platforms/youtube/callback").permitAll()` -- callback endpoints receive no JWT from TikTok/Google redirect
- [ ] `src/main/java/com/samuel/app/platform/controller/PlatformConnectionController.java` -- Change `@RequestMapping("/api/v1/platforms")` → `@RequestMapping("/platforms")`; make `code` and `state` `required = false` in TikTok callback; add `@RequestParam(required = false) String error` and `@RequestParam(required = false) String errorDescription`; return 400 with error detail when `code` is absent; mirror `required = false` for YouTube callback -- eliminates double context-path routing bug and surfaces TikTok error callbacks gracefully
- [ ] `src/main/java/com/samuel/app/platform/service/TikTokConnectionService.java` -- Remove `user.info.basic` from scope string; keep `user.info.profile,user.info.stats` -- `user.info.basic` is deprecated in TikTok v2 API and causes scope rejection
- [ ] `src/test/java/com/samuel/app/platform/controller/PlatformConnectionControllerTikTokTest.java` -- Replace all `/api/v1/platforms/` with `/platforms/` in perform() calls; add test for error callback (no code, has error param) expecting 400 -- tests must reflect corrected controller mapping
- [ ] `src/test/java/com/samuel/app/platform/controller/PlatformConnectionControllerTest.java` -- Replace all `/api/v1/platforms/` with `/platforms/` in perform() calls -- same as above

**Acceptance Criteria:**
- Given the TikTok OAuth redirect sends `?code=X&state=Y`, when callback is hit without a JWT, then the request is not rejected by security (no 401).
- Given the TikTok OAuth redirect sends `?error=access_denied&error_description=User denied`, when callback is hit, then the response is HTTP 400 with the error description in the body (not a generic Spring 400).
- Given the controller mapping is changed, when all existing MockMvc tests are run, then all tests pass with no regressions.

## Verification

**Commands:**
- `./mvnw test -Dtest=PlatformConnectionControllerTikTokTest,PlatformConnectionControllerTest -pl . -q` -- expected: BUILD SUCCESS, all tests pass
- `./mvnw test -q` -- expected: BUILD SUCCESS (full suite green)
