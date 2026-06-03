// @ts-check
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  assertBackendReady,
  detectAuthMode,
  attachDiagnostics,
  waitStable,
  clearAuthStorage,
  enterFromLoginIfNeeded,
  assertAgentDashboard,
  assertAgentDashboardWithUser,
  ensureLoginPage,
  createTestCredentials,
  registerViaUi,
  loginViaUi,
  printCreatedAccountsReport,
  gotoApp,
  TOKEN_KEY,
  SINGLE_USER_TIP,
  FATAL_AUTH_ERROR,
} = require('./e2e-env');

/** @type {'single-user' | 'jwt'} */
let detectedAuthMode = 'jwt';

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  detectedAuthMode = await detectAuthMode(request);
  console.log(`[auth-mode] 当前认证模式: ${detectedAuthMode}`);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('single-user 模式', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(() => {
    test.skip(detectedAuthMode !== 'single-user', '当前后端不是 single-user 模式');
  });

  test('用例1：访问首页进入应用', async ({ page }) => {
    await gotoApp(page, '/login');
    await clearAuthStorage(page);
    await page.reload();
    await gotoApp(page, '/');
    await waitStable(page);
    await enterFromLoginIfNeeded(page);
    await assertAgentDashboardWithUser(page, /local-user\s*\/\s*USER/);
    await expect(page.locator('body')).not.toContainText(FATAL_AUTH_ERROR);
  });

  test('用例2：访问 /login', async ({ page }) => {
    await ensureLoginPage(page);
    await expect(page.getByText('CareerMate', { exact: true })).toBeVisible();
    await expect(page.getByText(SINGLE_USER_TIP)).toBeVisible();
    await page.getByRole('button', { name: '进入 CareerMate' }).click();
    await expect(page).toHaveURL(/#\/$/);
    await assertAgentDashboardWithUser(page, /local-user\s*\/\s*USER/);
    await expect(page.locator('body')).not.toContainText(FATAL_AUTH_ERROR);
  });

  test('用例3：退出后可重新进入', async ({ page }) => {
    await gotoApp(page, '/');
    await waitStable(page);
    await enterFromLoginIfNeeded(page);
    await assertAgentDashboard(page);
    await page.getByRole('button', { name: '退出' }).click();
    await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });
    await gotoApp(page, '/');
    await waitStable(page);
    await enterFromLoginIfNeeded(page);
    await assertAgentDashboardWithUser(page, /local-user\s*\/\s*USER/);
    await expect(page.locator('body')).not.toContainText(FATAL_AUTH_ERROR);
  });
});

test.describe('jwt 模式', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {{ username: string; email: string; password: string } | null} */
  let registeredAccount = null;

  test.beforeEach(() => {
    if (detectedAuthMode !== 'jwt') {
      console.log('当前后端不是 jwt 模式，跳过 jwt 登录注册测试。');
      test.skip(true, '当前后端不是 jwt 模式，跳过 jwt 登录注册测试');
    }
  });

  test('用例1：未登录访问首页跳转 /login', async ({ page }) => {
    await gotoApp(page, '/login');
    await clearAuthStorage(page);
    await page.reload();
    await gotoApp(page, '/');
    await waitStable(page);
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.getByText('Agent 对话台')).not.toBeVisible();
  });

  test('用例2：注册成功', async ({ page }) => {
    registeredAccount = createTestCredentials();
    await registerViaUi(page, registeredAccount);
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await expect(page.locator('.user-badge')).toContainText(registeredAccount.username);
    await expect(page.locator('body')).not.toContainText(FATAL_AUTH_ERROR);
  });

  test('用例3：登录成功并保持登录态', async ({ page }) => {
    test.skip(!registeredAccount, '注册用例未执行');
    await page.getByRole('button', { name: '退出' }).click();
    await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });
    await loginViaUi(page, registeredAccount);
    let token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await page.reload();
    await waitStable(page);
    await assertAgentDashboard(page);
    token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await expect(page.locator('.user-badge')).toContainText(registeredAccount.username);
  });

  test('用例4：错误密码', async ({ page }) => {
    test.skip(!registeredAccount, '注册用例未执行');
    await page.getByRole('button', { name: '退出' }).click();
    await expect(page).toHaveURL(/#\/login/);
    await page.getByLabel('用户名').fill(registeredAccount.username);
    await page.getByLabel('密码').fill('WrongPassword99!');
    await page.locator('form .primary').click();
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.locator('.error')).toContainText(/用户名或密码错误|未认证|请求失败/i);
    await expect(page.getByText('Agent 对话台')).not.toBeVisible();
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeFalsy();
  });

  test('用例5：无效 token 401 处理', async ({ page }) => {
    await page.evaluate(
      ([tokenKey, badToken]) => {
        localStorage.setItem(tokenKey, badToken);
      },
      [TOKEN_KEY, 'invalid-token-for-e2e']
    );
    await gotoApp(page, '/');
    await waitStable(page);
    await expect(page).toHaveURL(/#\/login/, { timeout: 20_000 });
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeFalsy();
    await expect(page.getByText('Agent 对话台')).not.toBeVisible();
  });
});

test.afterAll(() => {
  console.log(`[report] E2E_TARGET 认证模式: ${detectedAuthMode}`);
  printCreatedAccountsReport();
});
