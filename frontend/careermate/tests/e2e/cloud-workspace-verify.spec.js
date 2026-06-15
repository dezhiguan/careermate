// @ts-check
/**
 * 云端 T04 workspace 验证：手机号登录后验证市场/机会联动小职。
 * 用法：
 * E2E_TARGET=cloud E2E_SMS_PHONE=18565040934 E2E_SMS_CODE=123456 \
 *   npx playwright test tests/e2e/cloud-workspace-verify.spec.js \
 *   --project=local-chrome-desktop --workers=1
 */
const { test, expect } = require('@playwright/test');
const {
  apiBaseURL,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  gotoApp,
  logEnv,
  loginViaSmsUi,
  MOCK_SMS_CODE,
} = require('./e2e-env');

const SMS_PHONE = process.env.E2E_SMS_PHONE || '';
const SMS_CODE = process.env.E2E_SMS_CODE || MOCK_SMS_CODE;

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  if (!SMS_PHONE) {
    throw new Error('请设置 E2E_SMS_PHONE 环境变量');
  }
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('云端 T04 Workspace 验证', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop chrome');
    await gotoApp(page, '/login');
    await loginViaSmsUi(page, SMS_PHONE, SMS_CODE);
    await expect(page.getByRole('heading', { name: '今天的机会' })).toBeVisible({ timeout: 20_000 });
  });

  test('市场页生成谈薪脚本进入小职', async ({ page }) => {
    await gotoApp(page, '/market');
    await page.waitForLoadState('networkidle');

    const negotiateBtn = page.getByRole('button', { name: /生成谈薪脚本/ });
    await expect(negotiateBtn).toBeVisible({ timeout: 15_000 });
    await negotiateBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/市场策略空间/, { timeout: 20_000 });
    await expect(page.locator('.context-chips-bar')).toContainText(/广州|Java|3-5年|市场行情/, {
      timeout: 20_000,
    });
  });

  test('机会页定制简历进入 JD 准备空间', async ({ page }) => {
    await gotoApp(page, '/opportunity');
    await page.waitForLoadState('networkidle');

    const emptyState = page.locator('.empty-state');
    if (await emptyState.isVisible({ timeout: 10_000 }).catch(() => false)) {
      test.skip(true, '云端 RagForge 无 JD 数据');
    }

    const resumeBtn = page.getByRole('button', { name: '定制简历' }).first();
    await expect(resumeBtn).toBeVisible({ timeout: 30_000 });
    await resumeBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/JD 准备空间/, { timeout: 20_000 });
  });
});
