// @ts-check
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

test.describe('机会页联动小职 Workspace', () => {
  test.beforeEach(async ({ page, request }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop project');
    await ensureLoggedInViaApi(page, request);
  });

  test('点击定制简历进入 JD 准备空间并展示生成简历 action', async ({ page }) => {
    await gotoApp(page, '/opportunity');
    await page.waitForLoadState('networkidle');

    const emptyState = page.locator('.empty-state');
    if (await emptyState.isVisible({ timeout: 10_000 }).catch(() => false)) {
      test.skip(true, 'RagForge 无 JD 数据，跳过机会页 workspace e2e');
    }

    const resumeBtn = page.getByRole('button', { name: '定制简历' }).first();
    await expect(resumeBtn).toBeVisible({ timeout: 45_000 });
    await resumeBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/JD 准备空间/, { timeout: 20_000 });
    await expect(page.locator('.agent-bubble, .msg-bubble').first()).toContainText(
      /定制简历|生成定制简历|已为你准备好/,
      { timeout: 20_000 }
    );
  });
});
