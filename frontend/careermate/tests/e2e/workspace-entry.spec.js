// @ts-check
/**
 * T04 工作空间跳转链路：市场 / 面试题 tab 进入 AI 小职。
 *
 * E2E_TARGET=local playwright test tests/e2e/workspace-entry.spec.js \
 *   --project=local-chrome-desktop --workers=1
 */
const { test, expect } = require('@playwright/test');
const {
  apiBaseURL,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  createTestCredentials,
  gotoApp,
  logEnv,
  TOKEN_KEY,
  USER_KEY,
} = require('./e2e-env');

async function ensureLoggedInViaApi(page, request) {
  const account = createTestCredentials();
  const res = await request.post(`${apiBaseURL}/auth/register`, {
    data: {
      username: account.username,
      password: account.password,
      email: account.email,
    },
    timeout: 20_000,
  });
  const body = await res.json().catch(() => null);
  expect(res.ok(), `注册 API 失败: ${JSON.stringify(body)}`).toBeTruthy();
  expect(body?.code, `注册 API 业务失败: ${JSON.stringify(body)}`).toBe(0);
  await gotoApp(page, '/login');
  await page.evaluate(
    ([tokenKey, userKey, token, user]) => {
      localStorage.setItem(tokenKey, token);
      localStorage.setItem(
        userKey,
        JSON.stringify({
          userId: user.userId,
          username: user.username,
          role: user.role,
          authenticated: true,
        })
      );
    },
    [TOKEN_KEY, USER_KEY, body.data.token, body.data.user]
  );
  await page.reload({ waitUntil: 'networkidle' });
  await expect(page.getByRole('heading', { name: '今天的机会' })).toBeVisible({ timeout: 15_000 });
}

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('T04 工作空间跳转链路', () => {
  test.beforeEach(async ({ page, request }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop chrome');
    await ensureLoggedInViaApi(page, request);
  });

  test('市场页生成谈薪脚本进入小职', async ({ page }) => {
    await gotoApp(page, '/market');
    await page.waitForLoadState('networkidle');

    const negotiateBtn = page.getByRole('button', { name: /生成谈薪脚本/ });
    await expect(negotiateBtn).toBeVisible({ timeout: 15_000 });
    await negotiateBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/市场策略空间/, { timeout: 20_000 });
    await expect(page.locator('body')).not.toContainText('工作空间不存在', { timeout: 5_000 });
  });

  test('面试题页让小职讲解进入小职', async ({ page }) => {
    await gotoApp(page, '/interview');
    await page.waitForLoadState('networkidle');

    const redisTag = page.getByRole('button', { name: 'Redis', exact: true });
    await expect(redisTag).toBeVisible({ timeout: 10_000 });
    await redisTag.click();

    const emptyTip = page.locator('.kb-section .empty-tip');
    const questionCard = page.locator('.kb-question-card').first();
    await expect(questionCard.or(emptyTip)).toBeVisible({ timeout: 60_000 });
    if (await emptyTip.isVisible().catch(() => false)) {
      test.skip(true, '面试知识库暂无 Redis 题目');
    }

    await page.locator('.question-header').first().click();
    const explainBtn = page.getByRole('button', { name: '让小职讲解' }).first();
    await expect(explainBtn).toBeVisible({ timeout: 10_000 });
    await explainBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/面试训练空间/, { timeout: 20_000 });
    await expect(page.locator('body')).not.toContainText('工作空间不存在', { timeout: 5_000 });
  });
});
