// @ts-check
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  waitStable,
  clearAuthStorage,
  gotoApp,
  ensurePasswordLoginForm,
  ensureForgotPasswordForm,
  createPhoneOnlyAccount,
  createAccountWithPhoneAndPassword,
  sendPasswordResetSmsViaUi,
  resetPasswordViaUi,
  loginViaUi,
  logoutViaUi,
  TOKEN_KEY,
  LOGIN_PAGE_TITLE,
  FORGOT_PASSWORD_PAGE_TITLE,
  SMS_LOGIN_PAGE_TITLE,
  MOCK_SMS_CODE,
} = require('./e2e-env');

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('Web 端找回密码', () => {
  test.describe.configure({ mode: 'serial', timeout: 300_000 });

  /** @type {{ phone: string; username: string; password?: string; newPassword?: string } | null} */
  let phoneAccount = null;

  test.beforeEach(async ({ page }) => {
    await gotoApp(page, '/login');
    await clearAuthStorage(page);
    await page.reload();
    await waitStable(page);
  });

  test('账号密码登录页展示忘记密码入口', async ({ page }) => {
    await ensurePasswordLoginForm(page);
    await expect(page.getByRole('button', { name: '忘记密码？' })).toBeVisible();
  });

  test('点击忘记密码进入找回密码表单', async ({ page }) => {
    await ensureForgotPasswordForm(page);
    await expect(page.getByLabel('手机号')).toBeVisible();
    await expect(page.getByLabel('验证码')).toBeVisible();
    await expect(page.getByLabel('新密码', { exact: true })).toBeVisible();
    await expect(page.getByLabel('确认新密码', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '重置密码' })).toBeVisible();
    await expect(page.getByRole('button', { name: '返回登录' })).toBeVisible();
    await expect(page.locator('.mode-toggle')).toHaveCount(0);
  });

  test('发送验证码后进入倒计时', async ({ page, request }) => {
    phoneAccount = await createPhoneOnlyAccount(request);
    await ensureForgotPasswordForm(page);
    await page.getByLabel('手机号').fill(phoneAccount.phone);
    await page.getByRole('button', { name: '发送验证码' }).click();
    await expect(page.locator('.success')).toContainText('如果该手机号已绑定账号，验证码将发送到手机', {
      timeout: 15_000,
    });
    const smsBtn = page.getByRole('button', { name: /\d+s|发送验证码/ });
    await expect(smsBtn).toBeDisabled();
    await expect(smsBtn).toContainText(/\d+s/);
  });

  test('手机号格式错误时提示', async ({ page }) => {
    await ensureForgotPasswordForm(page);
    await page.getByLabel('手机号').fill('12345');
    await page.getByRole('button', { name: '发送验证码' }).click();
    await expect(page.locator('.error')).toContainText('请输入正确的手机号');
  });

  test('未获取验证码直接重置时提示', async ({ page, request }) => {
    phoneAccount = phoneAccount || (await createPhoneOnlyAccount(request));
    await ensureForgotPasswordForm(page);
    await page.getByLabel('手机号').fill(phoneAccount.phone);
    await page.getByLabel('验证码').fill(MOCK_SMS_CODE);
    await page.getByLabel('新密码', { exact: true }).fill('NewPass123!');
    await page.getByLabel('确认新密码', { exact: true }).fill('NewPass123!');
    await page.getByRole('button', { name: '重置密码' }).click();
    await expect(page.locator('.error')).toContainText('请先获取验证码');
  });

  test('验证码错误时提示重新获取', async ({ page, request }) => {
    const account = await createPhoneOnlyAccount(request);
    await ensureForgotPasswordForm(page);
    await sendPasswordResetSmsViaUi(page, account.phone);
    await page.getByLabel('验证码').fill('000000');
    await page.getByLabel('新密码', { exact: true }).fill('NewPass123!');
    await page.getByLabel('确认新密码', { exact: true }).fill('NewPass123!');
    await page.getByRole('button', { name: '重置密码' }).click();
    await expect(page.locator('.error')).toContainText('验证码错误或已过期，请重新获取');
  });

  test('重置密码成功后回到账号密码登录', async ({ page, request }) => {
    phoneAccount = await createAccountWithPhoneAndPassword(request);
    await ensureForgotPasswordForm(page);
    await resetPasswordViaUi(page, phoneAccount);
    await expect(page.getByRole('button', { name: '忘记密码？' })).toBeVisible();
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeFalsy();
  });

  test('新密码可登录，错误密码不能登录', async ({ page }) => {
    test.skip(!phoneAccount?.newPassword, '前置账号未创建');
    await loginViaUi(page, { username: phoneAccount.username, password: phoneAccount.newPassword });
    let token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();

    await logoutViaUi(page);
    await ensurePasswordLoginForm(page);
    await page.getByLabel('用户名').fill(phoneAccount.username);
    await page.getByLabel('密码').fill(phoneAccount.wrongPassword);
    await page.locator('form .btn-primary').click();
    await expect(page).toHaveURL(/#\/login/);
    await expect(page.locator('.error')).toContainText(/用户名或密码错误|未认证|请求失败/i);
    token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeFalsy();
  });
});

test.describe('移动端不展示找回密码', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test.beforeEach(async ({ page }) => {
    await gotoApp(page, '/login');
    await clearAuthStorage(page);
    await page.reload();
    await waitStable(page);
  });

  test('移动端仅手机号验证码登录，无忘记密码入口', async ({ page }) => {
    await expect(page.getByRole('heading', { name: SMS_LOGIN_PAGE_TITLE })).toBeVisible();
    await expect(page.getByRole('button', { name: '忘记密码？' })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: FORGOT_PASSWORD_PAGE_TITLE })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: LOGIN_PAGE_TITLE })).toHaveCount(0);
    await expect(page.locator('.mode-toggle')).toHaveCount(0);
  });
});
