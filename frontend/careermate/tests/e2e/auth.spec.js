// @ts-check
const { test, expect } = require('@playwright/test');

const TOKEN_KEY = 'careermate_token';
const USER_KEY = 'careermate_user';
const FATAL_ERROR = /系统异常|登录失败|会话创建失败/;
const SINGLE_USER_TIP = '当前为本地单用户模式，可直接进入';

/** @type {'single-user' | 'jwt' | 'unknown'} */
let detectedAuthMode = 'unknown';

/**
 * @param {import('@playwright/test').APIRequestContext} request
 */
async function assertBackendReady(request) {
  const base = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173';
  let response;
  try {
    response = await request.get(`${base}/api/health`, { timeout: 15_000 });
  } catch (err) {
    throw new Error(
      [
        '无法访问后端 API（/api/health）。',
        '请先启动 backend（默认 http://localhost:8080），并确保前端 dev 已将 /api 代理到后端。',
        `详情: ${err instanceof Error ? err.message : String(err)}`,
      ].join('\n')
    );
  }
  if (!response.ok()) {
    const body = await response.text().catch(() => '');
    throw new Error(`后端健康检查失败 HTTP ${response.status()}。\n${body}`);
  }
}

/**
 * 根据 GET /api/auth/me（无 token）识别后端认证模式。
 * @param {import('@playwright/test').APIRequestContext} request
 * @returns {Promise<'single-user' | 'jwt'>}
 */
async function detectAuthMode(request) {
  const base = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173';
  const response = await request.get(`${base}/api/auth/me`, { timeout: 15_000 });
  const body = await response.json().catch(() => null);
  console.log(`[detect] GET /api/auth/me -> HTTP ${response.status()}`, JSON.stringify(body));

  if (response.ok() && body?.code === 0 && body?.data?.username === 'local-user') {
    return 'single-user';
  }
  return 'jwt';
}

/**
 * @param {import('@playwright/test').Page} page
 */
function attachDiagnostics(page) {
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      console.log(`[console.error] ${msg.text()}`);
    }
  });
  page.on('pageerror', (err) => {
    console.log(`[pageerror] ${err.message}`);
  });
  page.on('requestfailed', (req) => {
    console.log(`[requestfailed] ${req.method()} ${req.url()} — ${req.failure()?.errorText || 'failed'}`);
  });
  page.on('framenavigated', (frame) => {
    if (frame === page.mainFrame()) {
      console.log(`[url] ${frame.url()}`);
    }
  });
  page.on('response', async (response) => {
    const url = response.url();
    if (!url.includes('/api/auth/')) return;
    const path = url.replace(/^.*\/api/, '/api');
    let snippet = '';
    try {
      const json = await response.json();
      snippet = ` code=${json?.code} message=${json?.message || ''}`;
    } catch {
      snippet = '';
    }
    console.log(`[api] ${response.request().method()} ${path} -> HTTP ${response.status()}${snippet}`);
  });
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function clearAuthStorage(page) {
  await page.evaluate(
    ([tokenKey, userKey]) => {
      localStorage.removeItem(tokenKey);
      localStorage.removeItem(userKey);
    },
    [TOKEN_KEY, USER_KEY]
  );
  console.log('[storage] cleared careermate_token & careermate_user');
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function logoutIfNeeded(page) {
  const logoutBtn = page.getByRole('button', { name: '退出' });
  if (await logoutBtn.isVisible({ timeout: 8_000 }).catch(() => false)) {
    await logoutBtn.click();
    await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });
    console.log('[auth] 已点击退出，回到登录页');
  }
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function assertAgentDashboard(page) {
  await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 25_000 });
  await expect(page.locator('.user-badge')).toBeVisible();
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function assertNoFatalErrors(page) {
  await expect(page.locator('body')).not.toContainText(FATAL_ERROR);
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function enterFromLoginIfNeeded(page) {
  const enterBtn = page.getByRole('button', { name: '进入 CareerMate' });
  if (await enterBtn.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await enterBtn.click();
  }
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function waitStable(page) {
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

test.beforeAll(async ({ request }) => {
  await assertBackendReady(request);
  detectedAuthMode = await detectAuthMode(request);
  console.log(`[auth-mode] 当前后端认证模式: ${detectedAuthMode}`);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('single-user 模式', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(({ }, testInfo) => {
    test.skip(detectedAuthMode !== 'single-user', '当前后端不是 single-user 模式，跳过 single-user 测试');
  });

  test('用例1：访问首页自动进入或可进入', async ({ page }) => {
    await page.goto('/login');
    await clearAuthStorage(page);
    await page.reload();
    await page.goto('/');
    await waitStable(page);

    await enterFromLoginIfNeeded(page);
    await assertAgentDashboard(page);
    await expect(page.locator('.user-badge')).toContainText(/local-user\s*\/\s*USER/);
    await assertNoFatalErrors(page);
  });

  test('用例2：访问登录页', async ({ page }) => {
    await logoutIfNeeded(page);
    await page.goto('/login');
    await waitStable(page);

    await expect(page.getByText('CareerMate', { exact: true })).toBeVisible();
    await expect(page.getByText(SINGLE_USER_TIP)).toBeVisible();
    await page.getByRole('button', { name: '进入 CareerMate' }).click();

    await expect(page).toHaveURL(/#\/$/);
    await assertAgentDashboard(page);
    await assertNoFatalErrors(page);
  });

  test('用例3：退出登录后可重新进入', async ({ page }) => {
    await logoutIfNeeded(page);
    await page.goto('/');
    await enterFromLoginIfNeeded(page);
    await assertAgentDashboard(page);

    await page.getByRole('button', { name: '退出' }).click();
    await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });

    await page.goto('/');
    await waitStable(page);
    await enterFromLoginIfNeeded(page);

    await assertAgentDashboard(page);
    await expect(page.locator('.user-badge')).toContainText(/local-user\s*\/\s*USER/);
    await assertNoFatalErrors(page);
  });
});

test.describe('jwt 模式', () => {
  test.describe.configure({ mode: 'serial' });

  /** @type {{ username: string; email: string; password: string } | null} */
  let registeredAccount = null;

  test.beforeEach(({ }, testInfo) => {
    if (detectedAuthMode !== 'jwt') {
      console.log('当前后端不是 jwt 模式，跳过 jwt 登录注册测试。');
      test.skip(true, '当前后端不是 jwt 模式，跳过 jwt 登录注册测试');
    }
  });

  test('用例4：未登录访问首页跳转登录', async ({ page }) => {
    await clearAuthStorage(page);
    await page.goto('/');
    await waitStable(page);

    await expect(page).toHaveURL(/#\/login/);
    await expect(page.getByText('Agent 对话台')).not.toBeVisible();
  });

  test('用例5：注册成功', async ({ page }) => {
    const ts = Date.now();
    registeredAccount = {
      username: `e2e_user_${ts}`,
      email: `e2e_${ts}@careermate.test`,
      password: 'Test123456!',
    };

    await clearAuthStorage(page);
    await page.goto('/login');
    await page.getByRole('button', { name: '注册' }).click();

    await page.getByLabel('用户名').fill(registeredAccount.username);
    await page.getByLabel('邮箱').fill(registeredAccount.email);
    await page.getByLabel('密码').fill(registeredAccount.password);
    await page.getByRole('button', { name: '注册并进入' }).click();

    await expect(page).toHaveURL(/#\/$/, { timeout: 20_000 });
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await expect(page.locator('.user-badge')).toContainText(registeredAccount.username);
    await assertNoFatalErrors(page);
  });

  test('用例6：登录成功并保持登录态', async ({ page }) => {
    test.skip(!registeredAccount, '用例5未成功注册，跳过');

    await page.getByRole('button', { name: '退出' }).click();
    await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });

    await page.getByRole('button', { name: '登录' }).click();
    await page.getByLabel('用户名').fill(registeredAccount.username);
    await page.getByLabel('密码').fill(registeredAccount.password);
    await page.locator('form .primary').click();

    await expect(page).toHaveURL(/#\/$/, { timeout: 20_000 });
    let token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();

    await page.reload();
    await waitStable(page);
    await assertAgentDashboard(page);
    token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    await expect(page.locator('.user-badge')).toContainText(registeredAccount.username);
  });

  test('用例7：错误密码登录失败', async ({ page }) => {
    test.skip(!registeredAccount, '用例5未成功注册，跳过');

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

  test('用例8：无效 token 触发 401 并清理登录态', async ({ page }) => {
    await page.evaluate(
      ([tokenKey, badToken]) => {
        localStorage.setItem(tokenKey, badToken);
      },
      [TOKEN_KEY, 'invalid-token-for-e2e']
    );

    await page.goto('/');
    await waitStable(page);

    await expect(page).toHaveURL(/#\/login/, { timeout: 20_000 });
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeFalsy();
    await expect(page.getByText('Agent 对话台')).not.toBeVisible();
  });
});

test.afterAll(() => {
  console.log(`[report] 检测到的认证模式: ${detectedAuthMode}`);
});
