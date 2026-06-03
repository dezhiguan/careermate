// @ts-check
const { defineConfig } = require('@playwright/test');

const target = process.env.E2E_TARGET || 'local';

const targets = {
  local: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173',
    apiBaseURL: process.env.PLAYWRIGHT_API_BASE_URL || 'http://localhost:8080/api',
    startWebServer: true,
  },
  cloud: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://8.163.63.222/careermate',
    apiBaseURL: process.env.PLAYWRIGHT_API_BASE_URL || 'http://8.163.63.222/careermate-api',
    startWebServer: false,
  },
};

if (!targets[target]) {
  throw new Error(`Unsupported E2E_TARGET: ${target}`);
}

const currentTarget = targets[target];
const baseURL = currentTarget.baseURL.replace(/\/$/, '');

/** @type {import('@playwright/test').PlaywrightTestConfig['webServer']} */
const webServer = currentTarget.startWebServer
  ? {
      command: 'npm run dev -- --host 127.0.0.1 --port 5173',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: true,
      timeout: 120_000,
    }
  : undefined;

module.exports = defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 180_000,
  expect: { timeout: 20_000 },
  reporter: [['list']],
  use: {
    baseURL,
    actionTimeout: 25_000,
    navigationTimeout: 45_000,
  },
  projects: [
    {
      name: 'local-chrome-desktop',
      use: {
        channel: 'chrome',
        headless: false,
        viewport: { width: 1440, height: 900 },
        baseURL,
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
        trace: 'retain-on-failure',
      },
    },
    {
      name: 'local-chrome-mobile',
      use: {
        channel: 'chrome',
        headless: false,
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
        baseURL,
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
        trace: 'retain-on-failure',
      },
    },
  ],
  webServer,
});
