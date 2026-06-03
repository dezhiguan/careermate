// @ts-check
/** @typedef {'local' | 'cloud'} E2ETarget */

const target = /** @type {E2ETarget} */ (process.env.E2E_TARGET || 'local');

const targets = {
  local: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173',
    apiBaseURL: process.env.PLAYWRIGHT_API_BASE_URL || 'http://localhost:8080/api',
  },
  cloud: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://8.163.63.222/careermate',
    apiBaseURL: process.env.PLAYWRIGHT_API_BASE_URL || 'http://8.163.63.222/careermate-api',
  },
};

if (!targets[target]) {
  throw new Error(`Unsupported E2E_TARGET: ${target}`);
}

const current = targets[target];

const baseURL = current.baseURL.replace(/\/$/, '');
const apiBaseURL = current.apiBaseURL.replace(/\/$/, '');
const isCloud = target === 'cloud';
const isLocal = target === 'local';

const TOKEN_KEY = 'careermate_token';
const USER_KEY = 'careermate_user';
const SINGLE_USER_TIP = '当前为本地单用户模式，可直接进入';
const FATAL_AUTH_ERROR = /系统异常|登录失败|会话创建失败/;
const FATAL_APP_ERROR = /系统异常|会话创建失败|流式请求失败/;
const MOCK_REPLY =
  /Mock 简历分析结果|建议你突出项目中的业务指标|Agent Trace|流式完成|DONE|MESSAGE/i;

/** @type {Array<{ username: string; email: string }>} */
const createdTestAccounts = [];

function logEnv() {
  console.log('[e2e-env]', {
    E2E_TARGET: target,
    baseURL,
    apiBaseURL,
    isCloud,
    isLocal,
  });
}

/**
 * Hash 路由完整 URL（避免 Playwright baseURL 子路径下 goto('/x') 丢失 /careermate 前缀）。
 * @param {string} [route] 如 '/'、'/login'
 */
function appUrl(route = '/') {
  const normalized = route.startsWith('/') ? route : `/${route}`;
  const hash = normalized === '/' ? '#/' : `#${normalized}`;
  return `${baseURL}/${hash}`;
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {string} [route]
 */
async function gotoApp(page, route = '/') {
  const url = appUrl(route);
  console.log(`[goto] ${url}`);
  await page.goto(url);
}

function e2ePrefix() {
  return isCloud ? 'e2e_cloud' : 'e2e_local';
}

function createTestCredentials() {
  const ts = Date.now();
  const prefix = e2ePrefix();
  const account = {
    username: `${prefix}_user_${ts}`,
    email: `${prefix}_${ts}@careermate.test`,
    password: 'Test123456!',
  };
  createdTestAccounts.push({ username: account.username, email: account.email });
  console.log('[e2e-account] created', account.username, account.email);
  return account;
}

/**
 * @param {import('@playwright/test').APIRequestContext} request
 */
async function assertBackendReady(request) {
  const healthUrl = `${apiBaseURL}/health`;
  let response;
  try {
    response = await request.get(healthUrl, { timeout: 20_000 });
  } catch (err) {
    throw new Error(
      [
        `无法访问后端健康检查：${healthUrl}`,
        isLocal
          ? '请先启动 backend（默认 http://localhost:8080），并确保数据库可用。'
          : '请确认云端 careermate-api 可访问。',
        `详情: ${err instanceof Error ? err.message : String(err)}`,
      ].join('\n')
    );
  }
  if (!response.ok()) {
    const body = await response.text().catch(() => '');
    throw new Error(`后端健康检查失败 HTTP ${response.status()} @ ${healthUrl}\n${body}`);
  }
  console.log(`[health] OK ${healthUrl}`);
}

/**
 * @param {import('@playwright/test').APIRequestContext} request
 * @returns {Promise<'single-user' | 'jwt'>}
 */
async function detectAuthMode(request) {
  const meUrl = `${apiBaseURL}/auth/me`;
  const response = await request.get(meUrl, { timeout: 20_000 });
  const body = await response.json().catch(() => null);
  console.log(`[detect] GET ${meUrl} -> HTTP ${response.status()}`, JSON.stringify(body));

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
    if (!/\/auth\/(me|login|register)/.test(url)) return;
    let snippet = '';
    try {
      const json = await response.json();
      snippet = ` code=${json?.code} message=${json?.message || ''}`;
    } catch {
      snippet = '';
    }
    console.log(`[api] ${response.request().method()} ${url} -> HTTP ${response.status()}${snippet}`);
  });
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function waitStable(page) {
  await page.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => {});
  await page.waitForTimeout(500);
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
  console.log('[storage] cleared token & user');
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function enterFromLoginIfNeeded(page) {
  const enterBtn = page.getByRole('button', { name: '进入 CareerMate' });
  if (await enterBtn.isVisible({ timeout: 8_000 }).catch(() => false)) {
    await enterBtn.click();
  }
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function assertAgentDashboard(page) {
  await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 25_000 });
  await expect(page.locator('.user-badge')).toBeVisible();
  await expect(page.getByRole('button', { name: /新会话/ })).toBeVisible();
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {RegExp | string} [userPattern]
 */
async function assertAgentDashboardWithUser(page, userPattern) {
  await assertAgentDashboard(page);
  if (userPattern) {
    await expect(page.locator('.user-badge')).toContainText(userPattern);
  }
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function ensureLoginPage(page) {
  await gotoApp(page, '/login');
  await waitStable(page);
  const onLogin = await page
    .getByText(SINGLE_USER_TIP)
    .isVisible({ timeout: 4_000 })
    .catch(() => false);
  if (!onLogin) {
    console.log('[auth] /login 已自动进入应用，先退出以展示登录页');
    const logoutBtn = page.getByRole('button', { name: '退出' });
    await expect(logoutBtn).toBeVisible({ timeout: 15_000 });
    await logoutBtn.click();
    await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });
    await expect(page.getByText(SINGLE_USER_TIP)).toBeVisible({ timeout: 10_000 });
  }
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {{ username: string; email: string; password: string }} account
 */
async function registerViaUi(page, account) {
  await clearAuthStorage(page);
  await gotoApp(page, '/login');
  await waitStable(page);
  await page.getByRole('button', { name: '注册' }).click();
  await page.getByLabel('用户名').fill(account.username);
  await page.getByLabel('邮箱').fill(account.email);
  await page.getByLabel('密码').fill(account.password);
  await page.getByRole('button', { name: '注册并进入' }).click();
  await expect(page).toHaveURL(/#\/$/, { timeout: 25_000 });
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {{ username: string; password: string }} account
 */
async function loginViaUi(page, account) {
  await gotoApp(page, '/login');
  await waitStable(page);
  await page.getByRole('button', { name: '登录' }).click();
  await page.getByLabel('用户名').fill(account.username);
  await page.getByLabel('密码').fill(account.password);
  await page.locator('form .primary').click();
  await expect(page).toHaveURL(/#\/$/, { timeout: 25_000 });
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {'single-user' | 'jwt'} mode
 * @param {{ username: string; email: string; password: string } | null} [jwtAccount]
 */
async function enterApplication(page, mode, jwtAccount = null) {
  await gotoApp(page, '/');
  await waitStable(page);

  if (mode === 'single-user') {
    await enterFromLoginIfNeeded(page);
    await assertAgentDashboardWithUser(page, /local-user\s*\/\s*USER/);
    return;
  }

  const onLogin = await page
    .getByRole('button', { name: '进入 CareerMate' })
    .isVisible({ timeout: 3_000 })
    .catch(() => false);
  const needsAuth =
    onLogin ||
    (await page.getByText('CareerMate', { exact: true }).isVisible({ timeout: 3_000 }).catch(() => false));

  if (needsAuth) {
    const account = jwtAccount || createTestCredentials();
    if (!jwtAccount) {
      await registerViaUi(page, account);
    } else {
      await loginViaUi(page, account);
    }
    await assertAgentDashboardWithUser(page, account.username);
  } else {
    await assertAgentDashboard(page);
  }
}

// Playwright expect is injected by spec files
const { expect } = require('@playwright/test');

function printCreatedAccountsReport() {
  if (createdTestAccounts.length === 0) return;
  console.log('[report] 本次创建的测试账号:');
  for (const acc of createdTestAccounts) {
    console.log(`  - username: ${acc.username}, email: ${acc.email}`);
  }
}

module.exports = {
  target,
  baseURL,
  apiBaseURL,
  isCloud,
  isLocal,
  appUrl,
  gotoApp,
  TOKEN_KEY,
  USER_KEY,
  SINGLE_USER_TIP,
  FATAL_AUTH_ERROR,
  FATAL_APP_ERROR,
  MOCK_REPLY,
  createdTestAccounts,
  logEnv,
  e2ePrefix,
  createTestCredentials,
  assertBackendReady,
  detectAuthMode,
  attachDiagnostics,
  waitStable,
  clearAuthStorage,
  enterFromLoginIfNeeded,
  assertAgentDashboard,
  assertAgentDashboardWithUser,
  ensureLoginPage,
  registerViaUi,
  loginViaUi,
  enterApplication,
  printCreatedAccountsReport,
};
