import { defineConfig, devices } from '@playwright/test';

/**
 * Config for the demo-recording walkthrough only (see scripts/demo-recording/README.md) — this is
 * not a general test suite. The frontend has no other automated tests by design; `testDir` is
 * scoped narrowly so `playwright test` never picks up anything outside this one script.
 */
export default defineConfig({
  testDir: './scripts/demo-recording',
  timeout: 5 * 60_000,
  expect: { timeout: 10_000 },
  retries: 0,
  workers: 1,
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:3001',
    viewport: { width: 1440, height: 900 },
    video: { mode: 'on', size: { width: 1440, height: 900 } },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
