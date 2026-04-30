import { test, expect } from '@playwright/test';
import { ROUTES } from '../support/routes';
import { createRegistrationPayload, createLoginPayload } from '../fixtures/users.fixture';

test.describe('P0-A02/A03: Login rejection scenarios', () => {
  /**
   * P0-A02: Login with an unverified account must be blocked.
   * The server returns 401/403 when email is not yet verified.
   */
  test('P0-A02: login with unverified email is rejected', async ({ request }) => {
    const credentials = createRegistrationPayload();

    // Register but do NOT verify
    const regRes = await request.post(ROUTES.AUTH.REGISTER, { data: credentials });
    expect(regRes.status()).toBe(201);

    // Attempt login immediately — must fail
    const loginRes = await request.post(ROUTES.AUTH.LOGIN, {
      data: createLoginPayload(credentials.email),
    });
    expect([401, 403]).toContain(loginRes.status());
    const body = await loginRes.json();
    expect(body.success).toBe(false);
  });

  /**
   * P0-A03: Login with wrong password must return 401.
   */
  test('P0-A03: login with wrong password returns 401', async ({ request }) => {
    const loginRes = await request.post(ROUTES.AUTH.LOGIN, {
      data: { email: 'nobody@test.example.com', password: 'WrongPassword!' },
    });
    expect(loginRes.status()).toBe(401);
    const body = await loginRes.json();
    expect(body.success).toBe(false);
  });
});
