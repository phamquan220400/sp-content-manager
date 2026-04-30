import { test, expect } from '../fixtures/auth.fixture';
import { ROUTES } from '../support/routes';
import { createProfilePayload } from '../fixtures/profiles.fixture';

test.describe('Dashboard', () => {
  /**
   * P1-D01: Dashboard returns a valid response structure for a verified creator.
   * Profile creation ensures the dashboard has something to aggregate.
   */
  test('P1-D01: GET dashboard returns 200 with data for verified creator', async ({
    request,
    authHeaders,
  }) => {
    // Create profile so dashboard has content
    await request.post(ROUTES.PROFILE.BASE, {
      data: createProfilePayload(),
      headers: authHeaders,
    });

    const res = await request.get(ROUTES.DASHBOARD.DASHBOARD, { headers: authHeaders });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data).toBeDefined();
  });

  /**
   * P1-D02: Settings stub returns 200 with expected response.
   */
  test('P1-D02: GET settings returns 200 with stub message', async ({
    request,
    authHeaders,
  }) => {
    const res = await request.get(ROUTES.DASHBOARD.SETTINGS, { headers: authHeaders });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(typeof body.data).toBe('string');
  });
});
