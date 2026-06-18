const { test, expect } = require('@playwright/test');
const {
  assertBackendReady,
  attachDiagnostics,
  createTestPhone,
  gotoApp,
  mobileLoginViaApi,
  seedPageWithToken,
} = require('./e2e-env');

test.describe('P0 chat zero state landing', () => {
  test.beforeAll(async ({ request }) => {
    await assertBackendReady(request);
  });

  test.beforeEach(({ page }) => {
    attachDiagnostics(page);
  });

  test('new user lands on chat and prompt chip starts a real session stream', async ({ page, request }) => {
    const login = await mobileLoginViaApi(request, createTestPhone());
    await seedPageWithToken(page, login.token, login.user);

    const createSession = page.waitForResponse((response) => (
      response.url().includes('/api/agent/sessions')
      && response.request().method() === 'POST'
    ), { timeout: 30_000 });
    const streamRequest = page.waitForRequest((req) => (
      req.url().includes('/api/agent/sessions/')
      && req.url().includes('/messages/stream')
      && req.method() === 'POST'
    ), { timeout: 30_000 });

    await gotoApp(page, '/');

    await expect(page).toHaveURL(/#\/chat$/);
    await expect(page.locator('.profile-aha-banner')).toContainText('画像完整度');
    await expect(page.locator('.zero-chat-label')).toContainText('示例 · 点 chip 开始真实对话');
    await expect(page.locator('.zero-prompt-chip')).toHaveCount(3);
    await expect(page.getByRole('button', { name: '帮我看 JD' })).toBeVisible();
    await expect(page.getByRole('button', { name: '改我的简历' })).toBeVisible();
    await expect(page.getByRole('button', { name: '练一道面试题' })).toBeVisible();

    await page.getByRole('button', { name: '帮我看 JD' }).click();

    const [sessionResponse] = await Promise.all([createSession, streamRequest]);
    expect(sessionResponse.ok()).toBeTruthy();
    await expect(page.locator('.user-bubble')).toContainText('帮我看 JD', { timeout: 10_000 });
    await expect(page.locator('.zero-chat-card')).toHaveCount(0);
  });
});
