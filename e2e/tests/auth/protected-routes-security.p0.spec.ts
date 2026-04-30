import { test, expect, APIRequestContext, APIResponse } from '@playwright/test';
import { PROTECTED_ROUTES } from '../support/routes';

type HttpMethod = 'get' | 'post' | 'put' | 'delete' | 'patch' | 'head';

async function dispatchRequest(
  request: APIRequestContext,
  method: string,
  path: string,
  options?: Parameters<APIRequestContext['get']>[1],
): Promise<APIResponse> {
  const m = method.toLowerCase() as HttpMethod;
  return request[m](path, options);
}

/**
 * P0-S01: Every protected endpoint returns 401 when called without a token.
 * P0-S02: Every protected endpoint returns 401 when called with a malformed token.
 *
 * This is a parametric test — adding new protected routes to PROTECTED_ROUTES
 * in routes.ts automatically adds them to this test suite.
 */
for (const route of PROTECTED_ROUTES) {
  test(`P0-S01: ${route.method} ${route.path} → 401 without auth token`, async ({
    request,
  }) => {
    const res = await dispatchRequest(request, route.method, route.path);
    expect(res.status()).toBe(401);
  });

  test(`P0-S02: ${route.method} ${route.path} → 401 with malformed token`, async ({
    request,
  }) => {
    const res = await dispatchRequest(request, route.method, route.path, {
      headers: { Authorization: 'Bearer this.is.garbage' },
    });
    expect(res.status()).toBe(401);
  });
}
