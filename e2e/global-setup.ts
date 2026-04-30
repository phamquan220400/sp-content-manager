import { request } from '@playwright/test';

/**
 * Global setup runs once before all test suites.
 * Verifies the server is reachable before spending time on tests.
 */
async function globalSetup(): Promise<void> {
  const baseURL = process.env.API_BASE_URL ?? 'http://localhost:8080';
  const healthUrl = `${baseURL}/api/v1/actuator/health`;

  const ctx = await request.newContext({ baseURL });
  let lastError: unknown;

  for (let attempt = 1; attempt <= 10; attempt++) {
    try {
      const res = await ctx.get(healthUrl);
      if (res.ok()) {
        console.log(`[global-setup] Server healthy at ${healthUrl}`);
        await ctx.dispose();
        return;
      }
      lastError = `HTTP ${res.status()}`;
    } catch (err) {
      lastError = err;
    }
    console.log(`[global-setup] Waiting for server (attempt ${attempt}/10)...`);
    await new Promise((r) => setTimeout(r, 3_000));
  }

  await ctx.dispose();
  throw new Error(`[global-setup] Server not ready at ${healthUrl}: ${lastError}`);
}

export default globalSetup;
