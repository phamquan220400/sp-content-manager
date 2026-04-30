import { test, expect } from '../fixtures/auth.fixture';
import { ROUTES } from '../support/routes';
import { createProfilePayload } from '../fixtures/profiles.fixture';

/**
 * P1-P04: GET profile before any creation → 404
 * P1-P01: POST create profile (happy path) → 201
 * P1-P02: GET profile after creation → 200
 * P1-P03: PUT update profile → 200
 * P1-P05: POST create profile a second time → 409/400 (conflict)
 *
 * Each test gets its own verified user (via auth fixture) so tests are
 * fully isolated and safe to run in parallel.
 */
test.describe('Creator Profile CRUD', () => {
  test('P1-P04: GET profile before creation returns 404', async ({
    request,
    authHeaders,
  }) => {
    const res = await request.get(ROUTES.PROFILE.BASE, { headers: authHeaders });
    expect(res.status()).toBe(404);
  });

  test('P1-P01: POST create profile returns 201 with profile data', async ({
    request,
    authHeaders,
  }) => {
    const payload = createProfilePayload();
    const res = await request.post(ROUTES.PROFILE.BASE, {
      data: payload,
      headers: authHeaders,
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data.id).toBeTruthy();
    expect(body.data.displayName).toBe(payload.displayName);
    expect(body.data.creatorCategory).toBe(payload.creatorCategory);
  });

  test('P1-P02: GET profile after creation returns 200 with correct data', async ({
    request,
    authHeaders,
  }) => {
    const payload = createProfilePayload({ displayName: 'TestCreator' });

    // Create first
    const createRes = await request.post(ROUTES.PROFILE.BASE, {
      data: payload,
      headers: authHeaders,
    });
    expect(createRes.status()).toBe(201);

    // Then retrieve
    const getRes = await request.get(ROUTES.PROFILE.BASE, { headers: authHeaders });
    expect(getRes.status()).toBe(200);
    const body = await getRes.json();
    expect(body.data.displayName).toBe('TestCreator');
  });

  test('P1-P03: PUT update profile returns 200 with updated data', async ({
    request,
    authHeaders,
  }) => {
    // Create first
    await request.post(ROUTES.PROFILE.BASE, {
      data: createProfilePayload(),
      headers: authHeaders,
    });

    // Update
    const updateRes = await request.put(ROUTES.PROFILE.BASE, {
      data: { displayName: 'UpdatedName', bio: 'New bio', creatorCategory: 'GAMING' },
      headers: authHeaders,
    });
    expect(updateRes.status()).toBe(200);
    const body = await updateRes.json();
    expect(body.data.displayName).toBe('UpdatedName');
    expect(body.data.creatorCategory).toBe('GAMING');
  });

  test('P1-P05: POST create profile twice returns conflict error', async ({
    request,
    authHeaders,
  }) => {
    const payload = createProfilePayload();

    const first = await request.post(ROUTES.PROFILE.BASE, {
      data: payload,
      headers: authHeaders,
    });
    expect(first.status()).toBe(201);

    const second = await request.post(ROUTES.PROFILE.BASE, {
      data: payload,
      headers: authHeaders,
    });
    expect([400, 409]).toContain(second.status());
    const body = await second.json();
    expect(body.success).toBe(false);
  });
});
