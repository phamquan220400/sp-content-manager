import { test, expect } from '@playwright/test';
import { ROUTES } from '../support/routes';

/**
 * P0-O01: OAuth callbacks reject missing required parameters.
 * P0-O02: OAuth callbacks reject error params from provider (access_denied, etc.).
 * P2-EC1: error_description longer than 200 chars is truncated in the response.
 * P2-TK4: TikTok callback also rejects missing params.
 */
test.describe('OAuth callback error handling', () => {
  // ── YouTube ─────────────────────────────────────────────────────────────

  test('P0-O01: YouTube callback — no params → 400', async ({ request }) => {
    const res = await request.get(ROUTES.PLATFORMS.YOUTUBE.CALLBACK);
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
  });

  test('P0-O01: YouTube callback — code only (no state) → 400', async ({ request }) => {
    const res = await request.get(ROUTES.PLATFORMS.YOUTUBE.CALLBACK, {
      params: { code: 'some-code' },
    });
    expect(res.status()).toBe(400);
  });

  test('P0-O01: YouTube callback — state only (no code) → 400', async ({ request }) => {
    const res = await request.get(ROUTES.PLATFORMS.YOUTUBE.CALLBACK, {
      params: { state: 'some-state' },
    });
    expect(res.status()).toBe(400);
  });

  test('P0-O02: YouTube callback — provider error (access_denied) → 400', async ({
    request,
  }) => {
    const res = await request.get(ROUTES.PLATFORMS.YOUTUBE.CALLBACK, {
      params: { error: 'access_denied', error_description: 'User denied access' },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.message).toBeTruthy();
    // No newlines/tabs in message (log injection sanitization)
    expect(body.message).not.toMatch(/[\r\n\t]/);
  });

  test('P2-EC1: YouTube callback — error_description > 200 chars is truncated', async ({
    request,
  }) => {
    const longDesc = 'A'.repeat(501);
    const res = await request.get(ROUTES.PLATFORMS.YOUTUBE.CALLBACK, {
      params: { error: 'server_error', error_description: longDesc },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.message.length).toBeLessThanOrEqual(200);
  });

  // ── TikTok ──────────────────────────────────────────────────────────────

  test('P0-O01 / P2-TK4: TikTok callback — no params → 400', async ({ request }) => {
    const res = await request.get(ROUTES.PLATFORMS.TIKTOK.CALLBACK);
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
  });

  test('P0-O02: TikTok callback — provider error → 400', async ({ request }) => {
    const res = await request.get(ROUTES.PLATFORMS.TIKTOK.CALLBACK, {
      params: { error: 'access_denied' },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.message).not.toMatch(/[\r\n\t]/);
  });

  test('P2-EC1: TikTok callback — error_description > 200 chars is truncated', async ({
    request,
  }) => {
    const longDesc = 'B'.repeat(501);
    const res = await request.get(ROUTES.PLATFORMS.TIKTOK.CALLBACK, {
      params: { error: 'server_error', error_description: longDesc },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.message.length).toBeLessThanOrEqual(200);
  });
});
