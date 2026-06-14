// @ts-check
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  waitStable,
  clearAuthStorage,
  assertAuthenticatedLanding,
  ensureLoginPage,
  ensurePasswordLoginForm,
  ensureSmsLoginForm,
  ensureRegisterForm,
  createTestCredentials,
  createTestPhone,
  registerViaUi,
  loginViaUi,
  sendSmsCodeViaUi,
  loginViaSmsUi,
  printCreatedAccountsReport,
  gotoApp,
  TOKEN_KEY,
  LOGIN_PAGE_TITLE,
  SMS_LOGIN_PAGE_TITLE,
  REGISTER_PAGE_TITLE,
  POST_LOGIN_TITLE,
  MOCK_SMS_CODE,
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

test.describe('账号密码登录与注册', () => {
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
    await expect(page.getByRole('heading', { name: POST_LOGIN_TITLE })).not.toBeVisible();
  });

  test('用例2：注册成功', async ({ page }) => {
    registeredAccount = createTestCredentials();
    await registerViaUi(page, registeredAccount);
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await gotoApp(page, '/mine');
    await waitStable(page);
    await expect(page.locator('.identity-name')).toContainText(registeredAccount.username);
    await expect(page.locator('body')).not.toContainText(FATAL_AUTH_ERROR);
  });

  test('用例3：登录成功并保持登录态', async ({ page }) => {
    test.skip(!registeredAccount, '注册用例未执行');
    await loginViaUi(page, registeredAccount);
    let token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await page.reload();
    await waitStable(page);
    await assertAuthenticatedLanding(page);
    token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await gotoApp(page, '/mine');
    await expect(page.locator('.identity-name')).toContainText(registeredAccount.username);
  });

  test('用例4：错误密码', async ({ page }) => {
    test.skip(!registeredAccount, '注册用例未执行');
    await gotoApp(page, '/login');
    await waitStable(page);
    await ensurePasswordLoginForm(page);
    await page.getByLabel('用户名').fill(registeredAccount.username);
    await page.getByLabel('密码').fill('WrongPassword99!');
    await page.locator('form .btn-primary').click();
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.locator('.error')).toContainText(/用户名或密码错误|未认证|请求失败/i);
    await expect(page.getByRole('heading', { name: POST_LOGIN_TITLE })).not.toBeVisible();
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
    await expect(page.getByRole('heading', { name: POST_LOGIN_TITLE })).not.toBeVisible();
  });

  test('用例6：访问 /login 展示登录页', async ({ page }) => {
    await ensureLoginPage(page);
    await expect(page.getByText('CareerMate', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: LOGIN_PAGE_TITLE })).toBeVisible();
    await expect(page.locator('.mode-toggle button', { hasText: '登录' })).toHaveClass(/active/);
    await expect(page.getByLabel('用户名')).toBeVisible();
    await expect(page.getByRole('button', { name: '使用手机验证码登录' })).toBeVisible();
    await expect(page).toHaveURL(/#\/login/);
  });
});

test.describe('登录页 UI 切换', () => {
  test.beforeEach(async ({ page }) => {
    await gotoApp(page, '/login');
    await clearAuthStorage(page);
    await page.reload();
    await waitStable(page);
  });

  test('桌面端默认账号密码登录，可切换注册与手机验证码', async ({ page }) => {
    await expect(page.getByRole('heading', { name: LOGIN_PAGE_TITLE })).toBeVisible();
    await expect(page.getByLabel('用户名')).toBeVisible();
    await expect(page.getByLabel('密码')).toBeVisible();
    await expect(page.getByRole('button', { name: '使用手机验证码登录' })).toBeVisible();
    await expect(page.locator('.method-toggle')).toHaveCount(0);

    await ensureSmsLoginForm(page);
    await expect(page.getByRole('heading', { name: SMS_LOGIN_PAGE_TITLE })).toBeVisible();
    await expect(page.getByLabel('手机号')).toBeVisible();
    await expect(page.getByLabel('验证码')).toBeVisible();
    await expect(page.getByRole('button', { name: '使用账号密码登录' })).toBeVisible();

    await ensureRegisterForm(page);
    await expect(page.getByRole('heading', { name: REGISTER_PAGE_TITLE })).toBeVisible();
    await expect(page.getByLabel('邮箱')).toBeVisible();
    await expect(page.getByRole('button', { name: '使用手机验证码登录' })).toHaveCount(0);
  });

  test('移动端仅展示手机号验证码登录', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.reload();
    await waitStable(page);

    await expect(page.getByRole('heading', { name: SMS_LOGIN_PAGE_TITLE })).toBeVisible();
    await expect(page.getByLabel('手机号')).toBeVisible();
    await expect(page.getByRole('button', { name: '手机号登录' })).toBeVisible();
    await expect(page.locator('.mode-toggle')).toHaveCount(0);
    await expect(page.getByRole('button', { name: '使用手机验证码登录' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '注册' })).toHaveCount(0);
  });
});

test.describe('手机号验证码登录', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async ({ page }) => {
    await gotoApp(page, '/login');
    await clearAuthStorage(page);
    await page.reload();
    await waitStable(page);
  });

  test('Web 端发送验证码后进入倒计时', async ({ page }) => {
    await ensureSmsLoginForm(page);
    const phone = createTestPhone();
    await sendSmsCodeViaUi(page, phone);
    const smsBtn = page.getByRole('button', { name: /\d+s|发送验证码/ });
    await expect(smsBtn).toBeDisabled();
    await expect(smsBtn).toContainText(/\d+s/);
  });

  test('Web 端验证码登录成功进入机会页', async ({ page }) => {
    const phone = createTestPhone();
    await loginViaSmsUi(page, phone, MOCK_SMS_CODE);
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await expect(page.locator('body')).not.toContainText(FATAL_AUTH_ERROR);
  });

  test('未发送验证码时提示先发送', async ({ page }) => {
    await ensureSmsLoginForm(page);
    const phone = createTestPhone();
    await page.getByLabel('手机号').fill(phone);
    await page.getByLabel('验证码').fill(MOCK_SMS_CODE);
    await page.getByRole('button', { name: '手机号登录' }).click();
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.locator('.error')).toContainText('请先获取验证码');
  });

  test('手机号格式错误时提示', async ({ page }) => {
    await ensureSmsLoginForm(page);
    await page.getByLabel('手机号').fill('12345');
    await page.getByRole('button', { name: '发送验证码' }).click();
    await expect(page.locator('.error')).toContainText('请输入正确的手机号');
  });

  test('验证码错误时提示重新输入', async ({ page }) => {
    await ensureSmsLoginForm(page);
    const phone = createTestPhone();
    await sendSmsCodeViaUi(page, phone);
    await page.getByLabel('验证码').fill('000000');
    await page.getByRole('button', { name: '手机号登录' }).click();
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.locator('.error')).toContainText('验证码错误，请重新输入');
  });

  test('切换手机号后需重新发送验证码', async ({ page }) => {
    await ensureSmsLoginForm(page);
    const phoneA = createTestPhone();
    const phoneB = `139${String(Date.now()).slice(-8)}`;
    await sendSmsCodeViaUi(page, phoneA);
    await page.getByLabel('手机号').fill(phoneB);
    await page.getByLabel('验证码').fill(MOCK_SMS_CODE);
    await page.getByRole('button', { name: '手机号登录' }).click();
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.locator('.error')).toContainText('请先获取验证码');
  });
});

test.describe('移动端手机号验证码登录', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test.beforeEach(async ({ page }) => {
    await gotoApp(page, '/login');
    await clearAuthStorage(page);
    await page.reload();
    await waitStable(page);
    await expect(page.locator('.mode-toggle')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: SMS_LOGIN_PAGE_TITLE })).toBeVisible();
  });

  test('移动端验证码登录成功', async ({ page }) => {
    const phone = createTestPhone();
    await sendSmsCodeViaUi(page, phone);
    await page.getByLabel('验证码').fill(MOCK_SMS_CODE);
    const loginResponse = page.waitForResponse(
      (response) =>
        response.url().includes('/auth/mobile/login') && response.request().method() === 'POST',
      { timeout: 20_000 }
    );
    await page.locator('form .btn-primary').click();
    const response = await loginResponse;
    expect(response.ok(), `手机号登录 API 失败: HTTP ${response.status()}`).toBeTruthy();
    await assertAuthenticatedLanding(page);
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
  });
});

test.afterAll(() => {
  printCreatedAccountsReport();
});
