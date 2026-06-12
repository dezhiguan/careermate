// @ts-check
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  waitStable,
  clearAuthStorage,
  assertAgentDashboard,
  ensureLoginPage,
  createTestCredentials,
  registerViaUi,
  loginViaUi,
  printCreatedAccountsReport,
  gotoApp,
  TOKEN_KEY,
  LOGIN_PAGE_TITLE,
  FATAL_AUTH_ERROR,
} = require('./e2e-env');

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('JWT 登录', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {{ username: string; email: string; password: string } | null} */
  let registeredAccount = null;

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
    await page.locator('form .btn-primary').click();
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

  test('用例6：访问 /login 展示登录页', async ({ page }) => {
    await ensureLoginPage(page);
    await expect(page.getByText('CareerMate', { exact: true })).toBeVisible();
    await expect(page.getByText(LOGIN_PAGE_TITLE)).toBeVisible();
    await expect(page).toHaveURL(/#\/login/);
  });
});

test.afterAll(() => {
  printCreatedAccountsReport();
});
