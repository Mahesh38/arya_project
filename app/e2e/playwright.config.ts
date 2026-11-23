import { defineConfig, devices } from '@playwright/test';

// doc 13 §3.8 (D99): projects js-on / js-off at 360 and 1280 px; BASE_URL comes from the Maven e2e profile.
const baseURL = process.env.BASE_URL ?? 'http://localhost:8090';
// optional: use an already installed Chromium (offline labs, CI images)
const executablePath = process.env.CHROMIUM_PATH || undefined;

const viewports = [
  { name: '360', viewport: { width: 360, height: 800 } },
  { name: '1280', viewport: { width: 1280, height: 800 } },
];

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: { baseURL, trace: 'off', screenshot: 'only-on-failure', launchOptions: { executablePath } },
  projects: viewports.flatMap((v) => [
    { name: `js-on-${v.name}`, use: { ...devices['Desktop Chrome'], viewport: v.viewport, javaScriptEnabled: true } },
    { name: `js-off-${v.name}`, use: { ...devices['Desktop Chrome'], viewport: v.viewport, javaScriptEnabled: false } },
  ]),
});
