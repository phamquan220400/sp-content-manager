import { test, expect } from '../fixtures/auth.fixture';
import { ROUTES } from '../support/routes';

test.describe('P0-A04/A05/A06: Token management', () => {
  /**
   * P0-A04: Valid refresh token issues new access + refresh tokens.
   */
  test('P0-A04: refresh with valid token issues new tokens', async ({
    request,
    verifiedUser,
  }) => {
    const refreshRes = await request.post(ROUTES.AUTH.REFRESH, {
      data: { refreshToken: verifiedUser.tokens.refreshToken },
    });
    expect(refreshRes.status()).toBe(200);
    const body = await refreshRes.json();
    expect(body.data.accessToken).toBeTruthy();
    expect(body.data.refreshToken).toBeTruthy();
    // Refresh token must always rotate (UUID — always different)
    expect(body.data.refreshToken).not.toBe(verifiedUser.tokens.refreshToken);
    // Note: access token may be identical if issued within the same second (same JWT claims)
    // so we only assert it is present, not that it differs.
  });

  /**
   * P0-A05: Invalid / expired refresh token returns 401.
   */
  test('P0-A05: refresh with invalid token returns 401', async ({ request }) => {
    const refreshRes = await request.post(ROUTES.AUTH.REFRESH, {
      data: { refreshToken: 'invalid.token.value' },
    });
    expect(refreshRes.status()).toBe(401);
    const body = await refreshRes.json();
    expect(body.success).toBe(false);
  });

  /**
   * P0-A05b: Empty refresh token body returns 400 (validation failure).
   */
  test('P0-A05b: refresh with missing refreshToken field returns 400', async ({ request }) => {
    const refreshRes = await request.post(ROUTES.AUTH.REFRESH, {
      data: {},
    });
    expect(refreshRes.status()).toBe(400);
  });

  /**
   * P0-A06: Refresh token is revoked after logout.
   *
   * Note: The server uses stateless JWTs — access tokens remain valid until
   * natural expiry after logout. Only the refresh token is blacklisted.
   * This test verifies that the revoked refresh token cannot issue new tokens.
   */
  test('P0-A06: refresh token rejected after logout', async ({
    request,
    verifiedUser,
  }) => {
    const { accessToken, refreshToken } = verifiedUser.tokens;

    // Confirm refresh token works before logout
    const beforeRefresh = await request.post(ROUTES.AUTH.REFRESH, {
      data: { refreshToken },
    });
    expect(beforeRefresh.status()).toBe(200);
    const newTokens = await beforeRefresh.json();
    const newRefreshToken = newTokens.data.refreshToken;

    // Logout — revokes the new refresh token
    const logoutRes = await request.post(ROUTES.AUTH.LOGOUT, {
      data: { refreshToken: newRefreshToken },
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(logoutRes.status()).toBe(200);

    // Revoked refresh token must now be rejected
    const after = await request.post(ROUTES.AUTH.REFRESH, {
      data: { refreshToken: newRefreshToken },
    });
    expect(after.status()).toBe(401);
  });
});
