// @ts-check
const { test, expect } = require('@playwright/test');
const {
  enterApplicationAsUser,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  logEnv,
} = require('./e2e-env');

/** @type {{ username: string; email: string; password: string } | null} */
let jwtTestAccount = null;

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('市场页联动小职 Workspace', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop project');
    jwtTestAccount = await enterApplicationAsUser(page, jwtTestAccount);
  });

  test('点击生成谈薪脚本进入市场策略空间', async ({ page }) => {
    await page.goto('/market');
    await page.waitForLoadState('networkidle');

    const negotiateBtn = page.getByRole('button', { name: /生成谈薪脚本/ });
    await expect(negotiateBtn).toBeVisible({ timeout: 15_000 });
    await negotiateBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/市场策略空间/, { timeout: 20_000 });
  });
});
