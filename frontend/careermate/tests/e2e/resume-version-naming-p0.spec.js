const { test, expect } = require('@playwright/test');
const {
  assertBackendReady,
  attachDiagnostics,
  gotoApp,
  mobileLoginViaApi,
  seedPageWithToken,
} = require('./e2e-env');

test.describe('P0 resume version naming', () => {
  test.beforeAll(async ({ request }) => {
    await assertBackendReady(request);
  });

  test.beforeEach(({ page }) => {
    attachDiagnostics(page);
  });

  test('resume version cards use target naming instead of legacy generic title', async ({ page, request }) => {
    const login = await mobileLoginViaApi(request, '18565040934');
    await seedPageWithToken(page, login.token, login.user);

    await gotoApp(page, '/mine/resume');
    await expect(page).toHaveURL(/#\/mine\/resume/);
    await expect(page.locator('.resume-page')).toBeVisible({ timeout: 20_000 });

    const versionNames = page.locator('.version-name');
    await expect(versionNames.first()).toBeVisible({ timeout: 20_000 });
    const count = await versionNames.count();

    await expect(page.locator('.version-name', { hasText: /^定制简历版$/ })).toHaveCount(0);
    await expect(page.locator('.version-name', { hasText: '定制简历版' })).toHaveCount(0);
    for (let i = 0; i < count; i += 1) {
      await expect(versionNames.nth(i)).toContainText(/^针对【.+】.+ · v\d+$/);
    }
    await expect(page.locator('.version-name', { hasText: 'Java 后端工程师 A · v1' })).toBeVisible();
    await expect(page.locator('.version-name', { hasText: 'Java 后端工程师 A · v2' })).toBeVisible();
    await expect(page.locator('.version-name', { hasText: 'Java 后端工程师 B · v1' })).toBeVisible();
  });
});
