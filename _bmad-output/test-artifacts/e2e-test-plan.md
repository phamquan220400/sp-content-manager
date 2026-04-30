# E2E API Test Plan — spring_project

**Author:** Murat (Master Test Architect)  
**Date:** 2026-04-30  
**Scope:** All completed REST API endpoints across Epics 1 & 2  
**Test Type:** API E2E (service-layer, no browser)  
**Framework:** Playwright (API project) + `@seontechnologies/playwright-utils`

---

## 1. Scope & API Surface

All four controllers are in scope. No frontend/UI exists yet — tests are pure HTTP API tests.

| Domain              | Controller                    | Base Path          | Auth Required |
|---------------------|-------------------------------|--------------------|---------------|
| Auth                | `AuthController`              | `/auth`            | No (public)   |
| Creator Profile     | `CreatorProfileController`    | `/api/v1/profile`  | Yes (JWT)     |
| Dashboard           | `DashboardController`         | `/api/v1`          | Yes (JWT)     |
| Platform Connection | `PlatformConnectionController`| `/platforms`       | Yes (JWT)*    |

> *OAuth callback endpoints (`/youtube/callback`, `/tiktok/callback`) are `permitAll`.

---

## 2. Risk Assessment

Using `P × I` scoring (1=Low, 2=Med, 3=High). Scores ≥ 6 require mitigation; score = 9 is a gate blocker.

| # | Area                                  | P | I | Score | Level    | Mitigation                                      |
|---|---------------------------------------|---|---|-------|----------|-------------------------------------------------|
| R1| Unauthenticated access to protected routes | 2 | 3 | **6** | HIGH  | Mandatory 401 tests on every protected endpoint |
| R2| JWT token acceptance after logout     | 2 | 3 | **6** | HIGH     | Token blacklist / revocation validation tests   |
| R3| OAuth state CSRF bypass               | 2 | 3 | **6** | HIGH     | CSRF state mismatch → 400 tests                 |
| R4| Email verification token replay       | 2 | 3 | **6** | HIGH     | Reuse verification token → error tests          |
| R5| Duplicate user registration           | 3 | 2 | **6** | HIGH     | Register same email twice → 409/400 tests       |
| R6| Profile creation without verification | 2 | 2 | **4** | MEDIUM   | Unverified users blocked at profile creation    |
| R7| Profile image arbitrary file upload   | 2 | 3 | **6** | HIGH     | Reject non-image MIME types                     |
| R8| Creator profile data leakage          | 1 | 3 | **3** | LOW      | Each user sees only their own profile           |
| R9| OAuth callback log injection           | 1 | 2 | **2** | LOW      | `error_description` truncated to 200 chars ✓   |

---

## 3. Test Priority Matrix

### P0 — Critical (gate blockers, run on every PR)

| ID     | Journey                                        | Endpoint(s)                                  |
|--------|------------------------------------------------|----------------------------------------------|
| P0-A01 | Full registration → verify → login → logout    | `POST /auth/register` → `GET /auth/verify` → `POST /auth/login` → `POST /auth/logout` |
| P0-A02 | Login with unverified email blocked            | `POST /auth/login` → 403/401 expected        |
| P0-A03 | Login with wrong password                      | `POST /auth/login` → 401 expected            |
| P0-A04 | JWT refresh with valid refresh token           | `POST /auth/refresh`                         |
| P0-A05 | JWT refresh with invalid/expired token         | `POST /auth/refresh` → 401 expected          |
| P0-A06 | Revoked JWT still rejected after logout        | Use access token post-logout → 401           |
| P0-S01 | Protected route blocked without token         | `GET /api/v1/profile` without `Authorization` → 401 |
| P0-S02 | Protected route blocked with malformed token  | Garbage `Authorization: Bearer xxx` → 401   |
| P0-O01 | OAuth callback rejects missing state/code     | `GET /platforms/youtube/callback` (no params) → 400 |
| P0-O02 | OAuth callback rejects error param from provider | `GET /platforms/youtube/callback?error=access_denied` → 400 |

### P1 — High (run on every PR)

| ID     | Journey                                        | Endpoint(s)                                  |
|--------|------------------------------------------------|----------------------------------------------|
| P1-P01 | Create creator profile (happy path)            | `POST /api/v1/profile`                       |
| P1-P02 | Get creator profile (owner)                    | `GET /api/v1/profile`                        |
| P1-P03 | Update creator profile                         | `PUT /api/v1/profile`                        |
| P1-P04 | Get profile returns 404 when none exists       | `GET /api/v1/profile` before creation → 404  |
| P1-P05 | Create profile twice → conflict error          | `POST /api/v1/profile` × 2 → 409/400        |
| P1-D01 | Dashboard returns data for verified creator    | `GET /api/v1/dashboard`                      |
| P1-D02 | Settings stub returns expected response        | `GET /api/v1/settings`                       |
| P1-YT1 | Get YouTube auth URL (authenticated)           | `GET /platforms/youtube/auth/url`            |
| P1-YT2 | Get YouTube connection status (disconnected)   | `GET /platforms/youtube/connection`          |
| P1-TK1 | Get TikTok auth URL (authenticated)            | `GET /platforms/tiktok/auth/url`             |
| P1-TK2 | Get TikTok connection status (disconnected)    | `GET /platforms/tiktok/connection`           |

### P2 — Medium (run nightly)

| ID     | Journey                                        | Endpoint(s)                                  |
|--------|------------------------------------------------|----------------------------------------------|
| P2-P01 | Upload valid profile image (JPEG/PNG)          | `POST /api/v1/profile/image`                 |
| P2-P02 | Upload non-image file → error                  | `POST /api/v1/profile/image` (PDF/EXE)       |
| P2-P03 | Validation errors on profile creation          | `POST /api/v1/profile` missing required fields |
| P2-P04 | Validation errors on registration              | `POST /auth/register` missing fields / invalid email |
| P2-YT3 | Disconnect YouTube (connected state)           | `DELETE /platforms/youtube/disconnect`       |
| P2-TK3 | Disconnect TikTok (connected state)            | `DELETE /platforms/tiktok/disconnect`        |
| P2-TK4 | TikTok callback rejects missing params        | `GET /platforms/tiktok/callback` (no params) → 400 |
| P2-EC1 | `error_description` > 200 chars is truncated  | `GET /platforms/youtube/callback?error=x&error_description=<501chars>` |
| P2-EC2 | Email verification token replay blocked       | Re-use `GET /auth/verify?token=<used>` → 4xx |

### P3 — Low (run weekly or on-demand)

| ID     | Journey                                        |
|--------|------------------------------------------------|
| P3-N01 | Response time < 500 ms on all P0 endpoints (soak: 50 sequential calls) |
| P3-N02 | Concurrent registration (10 parallel same email) — only one succeeds   |
| P3-N03 | Actuator health endpoint accessible            |

---

## 4. Test Architecture

### 4.1 Recommended Stack

```
/tests/
  api/
    auth/
      register.spec.ts
      verify-email.spec.ts
      login.spec.ts
      refresh-token.spec.ts
      logout.spec.ts
    profile/
      create-profile.spec.ts
      get-profile.spec.ts
      update-profile.spec.ts
      profile-image.spec.ts
    dashboard/
      dashboard.spec.ts
    platforms/
      youtube-auth.spec.ts
      tiktok-auth.spec.ts
      oauth-callback-error.spec.ts
  fixtures/
    auth.fixture.ts          # JWT token provider
    users.fixture.ts         # factory: createUser(overrides)
    profiles.fixture.ts      # factory: createProfile(overrides)
  global-setup.ts
  playwright.config.ts
```

### 4.2 Playwright Project Config

```typescript
// playwright.config.ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/api',
  fullyParallel: true,
  workers: 4,
  timeout: 15_000,
  retries: process.env.CI ? 2 : 0,
  reporter: [['html', { outputFolder: 'playwright-report' }], ['list']],
  projects: [
    {
      name: 'api-p0',              // P0 critical — every PR
      testMatch: /p0-.*\.spec\.ts/,
      use: { baseURL: process.env.API_BASE_URL ?? 'http://localhost:8080' },
    },
    {
      name: 'api-p1',              // P1 high — every PR
      testMatch: /p1-.*\.spec\.ts/,
      use: { baseURL: process.env.API_BASE_URL ?? 'http://localhost:8080' },
    },
    {
      name: 'api-nightly',         // P2/P3 — nightly
      testMatch: /(p2|p3)-.*\.spec\.ts/,
      use: { baseURL: process.env.API_BASE_URL ?? 'http://localhost:8080' },
    },
  ],
  globalSetup: './tests/global-setup.ts',
});
```

### 4.3 Auth Fixture

```typescript
// tests/fixtures/auth.fixture.ts
import { test as base, expect } from '@playwright/test';

type AuthFixtures = {
  accessToken: string;
  verifiedUserCredentials: { email: string; password: string };
};

export const test = base.extend<AuthFixtures>({
  verifiedUserCredentials: async ({ request }, use) => {
    const email = `test-${Date.now()}@example.com`;
    const password = 'P@ssword1234!';
    // 1. Register
    await request.post('/auth/register', { data: { email, password, displayName: 'Test User' } });
    // 2. Retrieve token from test mail service (e.g., Mailosaur / GreenMail)
    //    OR inject token via back-door test endpoint (preferred for speed)
    // See: email-auth knowledge fragment for Mailosaur pattern
    await use({ email, password });
  },

  accessToken: async ({ request, verifiedUserCredentials }, use) => {
    const resp = await request.post('/auth/login', {
      data: verifiedUserCredentials,
    });
    expect(resp.status()).toBe(200);
    const body = await resp.json();
    await use(body.data.accessToken);
  },
});
export { expect } from '@playwright/test';
```

### 4.4 User Factory

```typescript
// tests/fixtures/users.fixture.ts
import { faker } from '@faker-js/faker';

export type CreateUserPayload = {
  email: string;
  password: string;
  displayName: string;
};

export const createUserPayload = (overrides: Partial<CreateUserPayload> = {}): CreateUserPayload => ({
  email: faker.internet.email(),
  password: 'P@ssword1234!',
  displayName: faker.person.fullName(),
  ...overrides,
});
```

---

## 5. Critical Test Implementations

### 5.1 P0-A01 — Full Registration Journey

```typescript
// tests/api/auth/p0-a01-register-verify-login-logout.spec.ts
import { test, expect } from '@playwright/test';
import { createUserPayload } from '../../fixtures/users.fixture';

test('P0-A01: full happy path — register → verify → login → logout', async ({ request }) => {
  const user = createUserPayload();

  // Step 1: Register
  const regResp = await request.post('/auth/register', { data: user });
  expect(regResp.status()).toBe(201);
  const regBody = await regResp.json();
  expect(regBody.success).toBe(true);

  // Step 2: Retrieve verification token
  // Option A — Mailosaur (real email)
  //   const token = await extractVerificationToken(user.email);
  // Option B — Back-door endpoint (recommended for CI speed)
  const tokenResp = await request.get('/test/verification-token', {
    params: { email: user.email },
  });
  const { token } = await tokenResp.json();

  // Step 3: Verify email
  const verifyResp = await request.get('/auth/verify', { params: { token } });
  expect(verifyResp.status()).toBe(200);

  // Step 4: Login
  const loginResp = await request.post('/auth/login', {
    data: { email: user.email, password: user.password },
  });
  expect(loginResp.status()).toBe(200);
  const { data } = await loginResp.json();
  expect(data.accessToken).toBeTruthy();
  expect(data.refreshToken).toBeTruthy();

  // Step 5: Logout
  const logoutResp = await request.post('/auth/logout', {
    data: { refreshToken: data.refreshToken },
    headers: { Authorization: `Bearer ${data.accessToken}` },
  });
  expect(logoutResp.status()).toBe(200);

  // Step 6: Revoked token must be rejected
  const protectedResp = await request.get('/api/v1/profile', {
    headers: { Authorization: `Bearer ${data.accessToken}` },
  });
  expect(protectedResp.status()).toBe(401);
});
```

### 5.2 P0-S01 — Protected Route Without Token

```typescript
// tests/api/auth/p0-s01-protected-route-without-token.spec.ts
import { test, expect } from '@playwright/test';

const PROTECTED_ROUTES = [
  { method: 'GET',    path: '/api/v1/profile' },
  { method: 'POST',   path: '/api/v1/profile' },
  { method: 'PUT',    path: '/api/v1/profile' },
  { method: 'GET',    path: '/api/v1/dashboard' },
  { method: 'GET',    path: '/api/v1/settings' },
  { method: 'GET',    path: '/platforms/youtube/auth/url' },
  { method: 'GET',    path: '/platforms/youtube/connection' },
  { method: 'DELETE', path: '/platforms/youtube/disconnect' },
  { method: 'GET',    path: '/platforms/tiktok/auth/url' },
  { method: 'GET',    path: '/platforms/tiktok/connection' },
  { method: 'DELETE', path: '/platforms/tiktok/disconnect' },
];

for (const route of PROTECTED_ROUTES) {
  test(`P0-S01: ${route.method} ${route.path} → 401 without auth`, async ({ request }) => {
    const resp = await (request as any)[route.method.toLowerCase()](route.path);
    expect(resp.status()).toBe(401);
  });
}
```

### 5.3 P0-O01/O02 — OAuth Callback Error Handling

```typescript
// tests/api/platforms/p0-o01-o02-oauth-callback-errors.spec.ts
import { test, expect } from '@playwright/test';

test.describe('OAuth callback error handling', () => {
  test('P0-O01: YouTube callback — no code or state → 400', async ({ request }) => {
    const resp = await request.get('/platforms/youtube/callback');
    expect(resp.status()).toBe(400);
    const body = await resp.json();
    expect(body.success).toBe(false);
  });

  test('P0-O01: TikTok callback — no code or state → 400', async ({ request }) => {
    const resp = await request.get('/platforms/tiktok/callback');
    expect(resp.status()).toBe(400);
  });

  test('P0-O02: YouTube callback — provider error → 400', async ({ request }) => {
    const resp = await request.get('/platforms/youtube/callback', {
      params: { error: 'access_denied', error_description: 'User denied access' },
    });
    expect(resp.status()).toBe(400);
    const body = await resp.json();
    expect(body.message).not.toContain('\n'); // no log injection
  });

  test('P2-EC1: error_description > 200 chars truncated', async ({ request }) => {
    const longDesc = 'A'.repeat(501);
    const resp = await request.get('/platforms/youtube/callback', {
      params: { error: 'server_error', error_description: longDesc },
    });
    expect(resp.status()).toBe(400);
    const body = await resp.json();
    expect(body.message.length).toBeLessThanOrEqual(200);
  });
});
```

### 5.4 P1-P01/P02/P03 — Creator Profile CRUD

```typescript
// tests/api/profile/p1-profile-crud.spec.ts
import { test as authTest, expect } from '../../fixtures/auth.fixture';
import { faker } from '@faker-js/faker';

authTest.describe('Creator Profile', () => {
  authTest('P1-P04: GET profile before creation → 404', async ({ request, accessToken }) => {
    const resp = await request.get('/api/v1/profile', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(resp.status()).toBe(404);
  });

  authTest('P1-P01: POST create profile', async ({ request, accessToken }) => {
    const resp = await request.post('/api/v1/profile', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        displayName: faker.person.fullName(),
        bio: 'Tech creator',
        category: 'TECHNOLOGY',
      },
    });
    expect(resp.status()).toBe(201);
    const { data } = await resp.json();
    expect(data.id).toBeTruthy();
  });

  authTest('P1-P02: GET profile after creation', async ({ request, accessToken }) => {
    // Pre-condition: profile already created in test setup
    const resp = await request.get('/api/v1/profile', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(resp.status()).toBe(200);
    const { data } = await resp.json();
    expect(data.displayName).toBeTruthy();
  });

  authTest('P1-P05: POST create profile twice → conflict', async ({ request, accessToken }) => {
    const payload = { displayName: 'Name', bio: 'Bio', category: 'TECHNOLOGY' };
    await request.post('/api/v1/profile', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: payload,
    });
    const resp = await request.post('/api/v1/profile', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: payload,
    });
    expect([400, 409]).toContain(resp.status());
  });
});
```

---

## 6. Email Verification Strategy

The registration flow sends a verification email. Two options for CI:

| Option | Pros | Cons | Recommended For |
|--------|------|------|-----------------|
| **Mailosaur** | Real SMTP, no code changes | External service cost, slower (~5–10 s) | Staging/E2E |
| **Test back-door endpoint** (`GET /test/verification-token`) | Fast, no external service | Requires test-only route (disable in prod) | CI/local |
| **GreenMail** (embedded) | No external service, fast | Requires Maven/Docker config | Integration |

**Recommendation:** Use a test-only endpoint secured by `@Profile("test")` in Spring. Disable in production profile via application configuration. This matches the `email-auth` knowledge fragment strategy for fast CI cycles.

---

## 7. Test Environment Setup

### docker-compose.test.yml requirements

The existing `docker-compose.yml` and `Dockerfile.dev` already provide MySQL + Redis. The test suite needs:

```yaml
services:
  app:
    environment:
      SPRING_PROFILES_ACTIVE: dev,test
      # Test mail: capture all outbound email
      SPRING_MAIL_HOST: mailhog
      SPRING_MAIL_PORT: 1025

  mailhog:
    image: mailhog/mailhog:v1.0.1
    ports:
      - "1025:1025"   # SMTP
      - "8025:8025"   # Web UI / API
```

Or use Mailosaur credentials stored as CI secrets (`MAILOSAUR_API_KEY`, `MAILOSAUR_SERVER_ID`).

---

## 8. CI Quality Gates

| Gate | Trigger | Pass Criteria |
|------|---------|---------------|
| P0 suite | Every PR | 0 failures, 0 retried-and-passed |
| P0 + P1 suite | Every PR merge to main | 0 failures |
| P0 + P1 + P2 suite | Nightly | 0 failures; flakiness rate < 2% |
| P3 performance | Weekly | p95 latency < 500 ms |

**Flakiness policy:** Any test that fails-then-passes in 2 consecutive retry attempts is flagged as flaky and must be resolved within the next sprint. Flaky tests are treated as critical tech debt (P0 priority).

---

## 9. Definition of Done

A test suite is ready to merge when:

- [ ] All P0 tests are green in CI
- [ ] All P1 tests are green in CI  
- [ ] No test relies on shared mutable state (each test creates its own user)
- [ ] No test uses `page.waitForTimeout()` or `sleep()` (use polling/retry)
- [ ] Auth tokens are obtained via fixtures, not hardcoded
- [ ] All test data cleaned up post-test (or uses isolated users per test)
- [ ] `playwright-report/` artifact uploaded in CI

---

## 10. Gaps & Deferred Items

| Gap | Owner | Priority |
|-----|-------|----------|
| OAuth happy-path tests require real YouTube/TikTok OAuth — use mocked callback server (WireMock) | Backend team | P2 |
| Profile image tests require multipart upload fixture — low priority, no business risk until UI ships | QA | P3 |
| Token encryption at-rest test for `PlatformConnection` — currently unit-tested; API-level smoke needed | QA | P2 |
| Rate limiting / circuit breaker state transition tests | Platform team | P2 |

---

*Generated by Murat — Master Test Architect. Risk scores apply `P × I` 1–3 scale. Gate thresholds: ≥6 HIGH (must mitigate), =9 CRITICAL (gate blocker).*
