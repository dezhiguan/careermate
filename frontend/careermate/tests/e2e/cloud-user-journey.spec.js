// @ts-check
/**
 * 云端完整用户场景 E2E（仅 E2E_TARGET=cloud）
 * - 强制注册/登录，不使用 single-user「进入 CareerMate」
 * - 串行执行全部已实现功能，不 skip 步骤
 */
const { test, expect } = require('@playwright/test');
const {
  isCloud,
  logEnv,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  waitStable,
  clearAuthStorage,
  gotoApp,
  createTestCredentials,
  registerViaUi,
  loginViaUi,
  detectAuthMode,
  assertAgentDashboard,
  assertAgentDashboardForAccount,
  printCreatedAccountsReport,
  TOKEN_KEY,
  FATAL_AUTH_ERROR,
  FATAL_APP_ERROR,
  sendAgentMessageAndExpectMockReply,
} = require('./e2e-env');

test.describe.configure({ mode: 'serial' });

/** @type {{ username: string; email: string; password: string } | null} */
let userAccount = null;
/** @type {'single-user' | 'jwt'} */
let authMode = 'jwt';

test.beforeAll(async ({ request }) => {
  test.skip(!isCloud, '仅 E2E_TARGET=cloud 时运行');
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  authMode = await detectAuthMode(request);
});

test.beforeEach(({ page }, testInfo) => {
  test.skip(!isCloud, '仅云端');
  attachDiagnostics(page);
});

test.describe('云端 · 桌面端完整用户旅程', () => {
  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '桌面端用例');
  });

  test('01 未登录访问首页应跳转登录', async ({ page }) => {
    await gotoApp(page, '/login');
    await clearAuthStorage(page);
    await page.reload();
    await gotoApp(page, '/');
    await waitStable(page);

    const onAgent = await page
      .getByText('Agent 对话台')
      .isVisible({ timeout: 5_000 })
      .catch(() => false);
    if (onAgent) {
      console.log('[01] single-user 自动进入，先退出再验证登录页');
      await page.getByRole('button', { name: '退出' }).click();
      await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });
    } else {
      await expect(page).toHaveURL(/#\/login/);
    }
    await expect(page.getByText('Agent 对话台')).not.toBeVisible();
  });

  test('02 注册并进入应用', async ({ page, request }) => {
    userAccount = createTestCredentials();
    await registerViaUi(page, userAccount, request);
    await assertAgentDashboardForAccount(page, userAccount, authMode);
    await expect(page.locator('body')).not.toContainText(FATAL_AUTH_ERROR);
  });

  test('03 Agent 对话 SSE', async ({ page }) => {
    test.skip(!userAccount, '依赖 02 注册');
    await gotoApp(page, '/');
    await waitStable(page);
    await assertAgentDashboardForAccount(page, userAccount, authMode);

    await sendAgentMessageAndExpectMockReply(page);
    await expect(page.locator('body')).not.toContainText(FATAL_APP_ERROR);
  });

  test('04 简历页', async ({ page }) => {
    test.skip(!userAccount, '依赖 02 注册');
    await gotoApp(page, '/');
    await waitStable(page);
    await assertAgentDashboard(page);
    await page.getByRole('link', { name: /简历/ }).click();
    await expect(page).toHaveURL(/#\/resume/);
    await expect(page.getByRole('heading', { name: /简历工作室/ })).toBeVisible();
    await expect(page.getByText('上传简历')).toBeVisible();
    expect((await page.locator('main').innerText()).trim().length).toBeGreaterThan(20);
  });

  test('05 岗位匹配与详情弹窗', async ({ page }) => {
    test.skip(!userAccount, '依赖 02 注册');
    await gotoApp(page, '/');
    await waitStable(page);
    await page.getByRole('link', { name: /岗位匹配/ }).click();
    await expect(page).toHaveURL(/#\/match/);
    await expect(page.locator('.job-card').first()).toBeVisible();
    await page.locator('.job-card').first().click();
    const modal = page.locator('.modal-card');
    await expect(modal).toBeVisible({ timeout: 12_000 });
    await expect(modal).toContainText(/匹配/);
    await expect(modal.getByRole('link', { name: /回对话台查看深度分析/ })).toBeVisible();
  });

  test('06 面试特训页', async ({ page }) => {
    test.skip(!userAccount, '依赖 02 注册');
    await gotoApp(page, '/');
    await page.getByRole('link', { name: /面试特训/ }).click();
    await expect(page).toHaveURL(/#\/interview/);
    await expect(page.getByRole('heading', { name: /面试特训/ })).toBeVisible();
    await expect(page.getByText(/第 \d+ 题/)).toBeVisible();
  });

  test('07 求职看板页', async ({ page }) => {
    test.skip(!userAccount, '依赖 02 注册');
    await gotoApp(page, '/');
    await page.getByRole('link', { name: /求职看板/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/);
    await expect(page.getByRole('heading', { name: /求职看板/ })).toBeVisible();
    await expect(page.getByText('最近活动')).toBeVisible();
  });

  test('08 底部导航往返', async ({ page }) => {
    test.skip(!userAccount, '依赖 02 注册');
    await gotoApp(page, '/');
    await waitStable(page);
    const routes = [
      { link: /对话台/, url: /#\/$/, title: 'Agent 对话台', isHeading: false },
      { link: /简历/, url: /#\/resume/, title: /简历工作室/, isHeading: true },
      { link: /岗位匹配/, url: /#\/match/, title: /岗位匹配/, isHeading: true },
      { link: /面试特训/, url: /#\/interview/, title: /面试特训/, isHeading: true },
      { link: /求职看板/, url: /#\/dashboard/, title: /求职看板/, isHeading: true },
    ];
    for (const route of routes) {
      await page.getByRole('link', { name: route.link }).click();
      await expect(page).toHaveURL(route.url);
      await waitStable(page);
      const titleLocator = route.isHeading
        ? page.getByRole('heading', { name: route.title })
        : page.getByText(route.title, { exact: true });
      await expect(titleLocator).toBeVisible({ timeout: 15_000 });
      if (route.isHeading) {
        expect((await page.locator('main').innerText()).trim().length).toBeGreaterThan(20);
      }
    }
  });

  test('09 退出后重新登录', async ({ page }) => {
    test.skip(!userAccount, '依赖 02 注册');
    await gotoApp(page, '/');
    await waitStable(page);
    await assertAgentDashboard(page);
    await page.getByRole('button', { name: '退出' }).click();
    await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });
    await loginViaUi(page, userAccount);
    await assertAgentDashboardForAccount(page, userAccount, authMode);
    await page.reload();
    await waitStable(page);
    await assertAgentDashboardForAccount(page, userAccount, authMode);
    expect(await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY)).toBeTruthy();
  });

  test('10 错误密码登录失败', async ({ page }) => {
    test.skip(authMode === 'single-user', '需 SECURITY_MODE=jwt');
    test.skip(!userAccount, '依赖 02 注册');
    await page.getByRole('button', { name: '退出' }).click();
    await expect(page).toHaveURL(/#\/login/);
    await page.getByLabel('用户名').fill(userAccount.username);
    await page.getByLabel('密码').fill('WrongPassword99!');
    await page.locator('form .primary').click();
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.locator('.error')).toContainText(/用户名或密码错误|未认证|请求失败/i);
    expect(await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY)).toBeFalsy();
  });

  test('11 无效 token 清理并跳转登录', async ({ page }) => {
    test.skip(authMode === 'single-user', '需 SECURITY_MODE=jwt');
    await page.evaluate(
      ([tokenKey, bad]) => {
        localStorage.setItem(tokenKey, bad);
      },
      [TOKEN_KEY, 'invalid-token-for-e2e-cloud']
    );
    await gotoApp(page, '/');
    await waitStable(page);
    await expect(page).toHaveURL(/#\/login/, { timeout: 20_000 });
    expect(await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY)).toBeFalsy();
  });
});

test.describe('云端 · 手机端完整用户旅程', () => {
  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-mobile', '手机端用例');
  });

  test('12 登录后各页展示与横向滚动', async ({ page, request }) => {
    if (!userAccount) {
      userAccount = createTestCredentials();
      await registerViaUi(page, userAccount, request);
    } else {
      await clearAuthStorage(page);
      await loginViaUi(page, userAccount);
    }
    await assertAgentDashboardForAccount(page, userAccount, authMode);

    const hasHorizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
    );
    expect(hasHorizontalOverflow).toBeFalsy();

    const routes = [
      { link: /对话台/, url: /#\/$/, title: 'Agent 对话台', isHeading: false },
      { link: /简历/, url: /#\/resume/, title: /简历工作室/, isHeading: true },
      { link: /岗位匹配/, url: /#\/match/, title: /岗位匹配/, isHeading: true },
      { link: /面试特训/, url: /#\/interview/, title: /面试特训/, isHeading: true },
      { link: /求职看板/, url: /#\/dashboard/, title: /求职看板/, isHeading: true },
    ];

    for (const route of routes) {
      await page.getByRole('link', { name: route.link }).click();
      await expect(page).toHaveURL(route.url);
      const titleLocator = route.isHeading
        ? page.getByRole('heading', { name: route.title })
        : page.getByText(route.title, { exact: true });
      await expect(titleLocator).toBeVisible({ timeout: 15_000 });
      expect((await page.locator('main').innerText()).trim().length).toBeGreaterThan(20);
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
      );
      expect(overflow).toBeFalsy();
      await page.waitForTimeout(500);
    }
  });
});

test.afterAll(() => {
  printCreatedAccountsReport();
});
