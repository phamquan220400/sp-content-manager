import { test, expect } from '@playwright/test';
import { ROUTES } from '../support/routes';
import { createRegistrationPayload } from '../fixtures/users.fixture';

/**
 * P2-P04: Registration field validation
 * P2-EC2: Email verification token replay blocked
 */
test.describe('Auth validation edge cases', () => {
  test('P2-P04: register with invalid email format returns 400', async ({ request }) => {
    const res = await request.post(ROUTES.AUTH.REGISTER, {
      data: { email: 'not-an-email', password: 'P@ssword1234!', confirmPassword: 'P@ssword1234!' },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
  });

  test('P2-P04: register with missing email returns 400', async ({ request }) => {
    const res = await request.post(ROUTES.AUTH.REGISTER, {
      data: { password: 'P@ssword1234!', confirmPassword: 'P@ssword1234!' },
    });
    expect(res.status()).toBe(400);
  });

  test('P2-P04: register with password too short returns 400', async ({ request }) => {
    const res = await request.post(ROUTES.AUTH.REGISTER, {
      data: { email: 'user@test.example.com', password: 'short', confirmPassword: 'short' },
    });
    expect(res.status()).toBe(400);
  });

  test('P2-P04: register same email twice returns error', async ({ request }) => {
    const payload = createRegistrationPayload();

    const first = await request.post(ROUTES.AUTH.REGISTER, { data: payload });
    expect(first.status()).toBe(201);

    const second = await request.post(ROUTES.AUTH.REGISTER, { data: payload });
    expect([400, 409]).toContain(second.status());
    const body = await second.json();
    expect(body.success).toBe(false);
  });

  /**
   * P2-EC2: Re-using a verification token after it has been consumed must fail.
   * Requires the test back-door endpoint.
   */
  test('P2-EC2: email verification token cannot be replayed', async ({ request }) => {
    const payload = createRegistrationPayload();

    const regRes = await request.post(ROUTES.AUTH.REGISTER, { data: payload });
    expect(regRes.status()).toBe(201);

    const tokenRes = await request.get(ROUTES.TEST_SUPPORT.VERIFICATION_TOKEN, {
      params: { email: payload.email },
    });
    test.skip(!tokenRes.ok(), 'Test back-door endpoint unavailable');
    const { token } = await tokenRes.json();

    // First use — should succeed
    const first = await request.get(ROUTES.AUTH.VERIFY, { params: { token } });
    expect(first.status()).toBe(200);

    // Second use — must fail
    const second = await request.get(ROUTES.AUTH.VERIFY, { params: { token } });
    expect([400, 410]).toContain(second.status());
    const body = await second.json();
    expect(body.success).toBe(false);
  });
});
