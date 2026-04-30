/**
 * Canonical route map for all API endpoints.
 *
 * Route anatomy:
 *   server.servlet.context-path = /api/v1   (all requests prefixed here)
 *
 *   AuthController         @RequestMapping("/auth")          → /api/v1/auth/…
 *   PlatformController     @RequestMapping("/platforms")     → /api/v1/platforms/…
 *   CreatorProfileController @RequestMapping("/api/v1/profile") → /api/v1/profile  (includes context-path, maps to /api/v1/profile)
 *   DashboardController    @RequestMapping("/api/v1")        → /api/v1/dashboard
 *
 * Note: Profile and Dashboard controllers include /api/v1 in their @RequestMapping
 * which is the full absolute path. Spring treats paths starting with / as absolute
 * within the servlet context, so /api/v1/profile maps to http://host:8080/api/v1/profile.
 *
 * All paths are absolute (start with /) to be used with Playwright's `request` context
 * configured with baseURL = http://localhost:8080.
 */

export const ROUTES = {
  AUTH: {
    REGISTER: '/api/v1/auth/register',
    VERIFY: '/api/v1/auth/verify',
    LOGIN: '/api/v1/auth/login',
    REFRESH: '/api/v1/auth/refresh',
    LOGOUT: '/api/v1/auth/logout',
  },
  PROFILE: {
    BASE: '/api/v1/profile',
    IMAGE: '/api/v1/profile/image',
  },
  DASHBOARD: {
    DASHBOARD: '/api/v1/dashboard',
    SETTINGS: '/api/v1/settings',
  },
  PLATFORMS: {
    YOUTUBE: {
      AUTH_URL: '/api/v1/platforms/youtube/auth/url',
      CALLBACK: '/api/v1/platforms/youtube/callback',
      CONNECTION: '/api/v1/platforms/youtube/connection',
      DISCONNECT: '/api/v1/platforms/youtube/disconnect',
    },
    TIKTOK: {
      AUTH_URL: '/api/v1/platforms/tiktok/auth/url',
      CALLBACK: '/api/v1/platforms/tiktok/callback',
      CONNECTION: '/api/v1/platforms/tiktok/connection',
      DISCONNECT: '/api/v1/platforms/tiktok/disconnect',
    },
  },
  ACTUATOR: {
    HEALTH: '/api/v1/actuator/health',
  },
  /** Test-only back-door: active only when SPRING_PROFILES_ACTIVE includes 'test' */
  TEST_SUPPORT: {
    VERIFICATION_TOKEN: '/api/v1/test/verification-token',
    MAILHOG_API: 'http://localhost:8025/api/v2/messages',
  },
} as const;

/** All endpoints that require an Authorization header (JWT bearer). */
export const PROTECTED_ROUTES: Array<{ method: string; path: string }> = [
  { method: 'GET',    path: ROUTES.PROFILE.BASE },
  { method: 'POST',   path: ROUTES.PROFILE.BASE },
  { method: 'PUT',    path: ROUTES.PROFILE.BASE },
  { method: 'POST',   path: ROUTES.PROFILE.IMAGE },
  { method: 'GET',    path: ROUTES.DASHBOARD.DASHBOARD },
  { method: 'GET',    path: ROUTES.DASHBOARD.SETTINGS },
  { method: 'GET',    path: ROUTES.PLATFORMS.YOUTUBE.AUTH_URL },
  { method: 'GET',    path: ROUTES.PLATFORMS.YOUTUBE.CONNECTION },
  { method: 'DELETE', path: ROUTES.PLATFORMS.YOUTUBE.DISCONNECT },
  { method: 'GET',    path: ROUTES.PLATFORMS.TIKTOK.AUTH_URL },
  { method: 'GET',    path: ROUTES.PLATFORMS.TIKTOK.CONNECTION },
  { method: 'DELETE', path: ROUTES.PLATFORMS.TIKTOK.DISCONNECT },
];
