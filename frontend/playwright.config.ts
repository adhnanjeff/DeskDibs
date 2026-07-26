import { defineConfig, devices } from '@playwright/test';

const FRONTEND_URL = process.env.E2E_BASE_URL ?? 'http://localhost:5173';

/**
 * End-to-end tests, run against a real backend and a real database.
 *
 * <p>Unlike the Vitest suite — which mocks the API with MSW and proves component behaviour — these
 * exist for the one guarantee that cannot be mocked: two people clicking the same seat at the same
 * instant, settled by a Postgres unique index. Mocking that would prove nothing.
 *
 * <p>The backend is NOT started here. It needs PostgreSQL and Flyway, and a Playwright `webServer`
 * that silently starts a second application against the developer's dev database is a good way to
 * lose data. Start it yourself (`mvn spring-boot:run`) and these tests will find it; if it is not
 * running they fail fast with a clear message rather than a wall of timeouts.
 */
export default defineConfig({
  testDir: './e2e',
  // The race test drives two browser contexts that must click within milliseconds of each other.
  // Running spec files in parallel on a shared database would let them fight over the same seats.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: process.env.CI ? [['github'], ['list']] : [['list']],

  use: {
    baseURL: FRONTEND_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  // Vite is safe to start automatically — it holds no state. `reuseExistingServer` keeps a dev
  // server you already have running, rather than failing on the port.
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: FRONTEND_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
