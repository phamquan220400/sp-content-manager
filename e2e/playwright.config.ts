import { defineConfig } from '@playwright/test';

/**
 * Base URL resolves to: http://localhost:8080
 *
 * All request paths include the context-path prefix `/api/v1` explicitly
 * via the ROUTES constants (see tests/support/routes.ts).
 *
 * Why not set baseURL to http://localhost:8080/api/v1?
 *   Because Playwright treats paths starting with '/' as absolute (replaces
 *   the base path), so setting the context-path in baseURL would break
 *   controller mappings that themselves start with /api/v1 (Profile, Dashboard).
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 4 : 2,
  timeout: 15_000,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ...(process.env.CI ? [['github'] as ['github']] : []),
  ],
  use: {
    baseURL: process.env.API_BASE_URL ?? 'http://localhost:8080',
    extraHTTPHeaders: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    // Capture traces on first retry in CI for debugging
    trace: process.env.CI ? 'on-first-retry' : 'off',
  },

  projects: [
    // ── P0: Critical gate — every PR ─────────────────────────────────────
    {
      name: 'api-p0',
      testMatch: /.*\.p0\.spec\.ts/,
    },
    // ── P1: High — every PR merge to main ────────────────────────────────
    {
      name: 'api-p1',
      testMatch: /.*\.p1\.spec\.ts/,
    },
    // ── P2/P3: Nightly and weekly ─────────────────────────────────────────
    {
      name: 'api-nightly',
      testMatch: /.*\.(p2|p3)\.spec\.ts/,
    },
  ],

  globalSetup: './global-setup.ts',
});
