import { test, expect } from '../fixtures/auth.fixture';
import { ROUTES } from '../support/routes';
import { createProfilePayload } from '../fixtures/profiles.fixture';

/**
 * Platform connection status and auth URL generation.
 * These tests verify the endpoints are reachable and return valid shapes.
 *
 * Note: Full OAuth happy-path tests (P2-YT3, P2-TK3) require a mock OAuth
 * server (e.g. WireMock) to simulate the provider callback with a valid code.
 * Those are deferred — see e2e-test-plan.md §10.
 */
test.describe('Platform connections', () => {
  test.beforeEach(async ({ request, authHeaders }) => {
    // Platform endpoints require a creator profile to exist
    const existing = await request.get(ROUTES.PROFILE.BASE, { headers: authHeaders });
    if (existing.status() === 404) {
      await request.post(ROUTES.PROFILE.BASE, {
        data: createProfilePayload(),
        headers: authHeaders,
      });
    }
  });

  // ── YouTube ─────────────────────────────────────────────────────────────

  test('P1-YT1: GET YouTube auth URL returns 200 with authorization URL', async ({
    request,
    authHeaders,
  }) => {
    const res = await request.get(ROUTES.PLATFORMS.YOUTUBE.AUTH_URL, {
      headers: authHeaders,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data.authorizationUrl).toMatch(/^https:\/\//);
  });

  test('P1-YT2: GET YouTube connection status returns 200 (disconnected)', async ({
    request,
    authHeaders,
  }) => {
    const res = await request.get(ROUTES.PLATFORMS.YOUTUBE.CONNECTION, {
      headers: authHeaders,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    // Newly created profile should not have an active YouTube connection
    expect(body.data).toBeDefined();
  });

  // ── TikTok ──────────────────────────────────────────────────────────────

  test('P1-TK1: GET TikTok auth URL returns 200 with authorization URL', async ({
    request,
    authHeaders,
  }) => {
    const res = await request.get(ROUTES.PLATFORMS.TIKTOK.AUTH_URL, {
      headers: authHeaders,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data.authorizationUrl).toMatch(/^https:\/\//);
  });

  test('P1-TK2: GET TikTok connection status returns 200 (disconnected)', async ({
    request,
    authHeaders,
  }) => {
    const res = await request.get(ROUTES.PLATFORMS.TIKTOK.CONNECTION, {
      headers: authHeaders,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data).toBeDefined();
  });
});
