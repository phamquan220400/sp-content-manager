import { test as base, expect, APIRequestContext } from '@playwright/test';
import { ROUTES } from '../support/routes';
import {
  createRegistrationPayload,
  createLoginPayload,
  RegistrationPayload,
} from './users.fixture';

export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
};

export type VerifiedUser = {
  credentials: RegistrationPayload;
  tokens: AuthTokens;
};

/**
 * Registers a new user, retrieves the email verification token,
 * verifies the account, then logs in and returns tokens.
 *
 * Email verification strategy (in priority order):
 *   1. Back-door endpoint: GET /api/v1/test/verification-token?email=... (requires 'test' Spring profile)
 *   2. MailHog API: GET http://localhost:8025/api/v2/messages (searches for the token in email body)
 *
 * Set SPRING_PROFILES_ACTIVE=dev,test to enable the back-door endpoint.
 */
async function registerAndVerify(request: APIRequestContext): Promise<VerifiedUser> {
  const credentials = createRegistrationPayload();

  // 1 — Register
  const regRes = await request.post(ROUTES.AUTH.REGISTER, { data: credentials });
  expect(regRes.status(), `Registration failed: ${await regRes.text()}`).toBe(201);

  // 2 — Get verification token
  const token = await resolveVerificationToken(request, credentials.email);

  // 3 — Verify email
  const verifyRes = await request.get(ROUTES.AUTH.VERIFY, { params: { token } });
  expect(verifyRes.status(), `Email verification failed: ${await verifyRes.text()}`).toBe(200);

  // 4 — Login
  const loginRes = await request.post(ROUTES.AUTH.LOGIN, {
    data: createLoginPayload(credentials.email),
  });
  expect(loginRes.status(), `Login failed: ${await loginRes.text()}`).toBe(200);
  const loginBody = await loginRes.json();
  const tokens: AuthTokens = {
    accessToken: loginBody.data.accessToken,
    refreshToken: loginBody.data.refreshToken,
  };

  return { credentials, tokens };
}

/**
 * Resolves the email verification token using the available strategy.
 */
async function resolveVerificationToken(
  request: APIRequestContext,
  email: string,
): Promise<string> {
  // Strategy 1: Back-door test endpoint (fast, preferred for CI)
  try {
    const res = await request.get(ROUTES.TEST_SUPPORT.VERIFICATION_TOKEN, {
      params: { email },
    });
    if (res.ok()) {
      const body = await res.json();
      if (body.token) return body.token;
    }
  } catch {
    // Fall through to MailHog strategy
  }

  // Strategy 2: MailHog API (slower, requires MailHog running)
  // Polls up to 10 seconds for the email to arrive
  for (let i = 0; i < 10; i++) {
    await new Promise((r) => setTimeout(r, 1_000));
    try {
      const res = await request.get(ROUTES.TEST_SUPPORT.MAILHOG_API);
      if (res.ok()) {
        const body = await res.json();
        const messages: Array<{ Content: { Body: string }; To: Array<{ Mailbox: string; Domain: string }> }> =
          body.items ?? [];
        for (const msg of messages) {
          const toAddress = msg.To?.map((t) => `${t.Mailbox}@${t.Domain}`).join(',') ?? '';
          if (toAddress.includes(email)) {
            // Extract token from URL in email body (e.g. ?token=<value>)
            const match = msg.Content.Body.match(/[?&]token=([A-Za-z0-9_\-%.]+)/);
            if (match) return decodeURIComponent(match[1]);
          }
        }
      }
    } catch {
      // Retry
    }
  }

  throw new Error(
    `Could not retrieve verification token for ${email}. ` +
      'Ensure the Spring "test" profile is active (back-door endpoint) ' +
      'or MailHog is running on port 8025.',
  );
}

/** Bearer header for a given access token. */
export function bearerHeader(accessToken: string): Record<string, string> {
  return { Authorization: `Bearer ${accessToken}` };
}

type AuthFixtures = {
  verifiedUser: VerifiedUser;
  accessToken: string;
  authHeaders: Record<string, string>;
};

/**
 * Extends Playwright's base `test` with auth fixtures.
 *
 * Usage:
 *   import { test, expect } from '../fixtures/auth.fixture';
 *
 *   test('...', async ({ request, accessToken, authHeaders }) => { ... });
 */
export const test = base.extend<AuthFixtures>({
  verifiedUser: async ({ request }, use) => {
    const user = await registerAndVerify(request);
    await use(user);
  },

  accessToken: async ({ verifiedUser }, use) => {
    await use(verifiedUser.tokens.accessToken);
  },

  authHeaders: async ({ accessToken }, use) => {
    await use(bearerHeader(accessToken));
  },
});

export { expect } from '@playwright/test';
