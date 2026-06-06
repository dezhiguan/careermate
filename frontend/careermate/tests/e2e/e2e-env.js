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
/** 云端或 E2E_USER_FLOW=1 时强制真实用户流程（注册/登录），禁止 single-user 捷径 */
const mustUseUserFlow = isCloud || process.env.E2E_USER_FLOW === '1';

const TOKEN_KEY = 'careermate_token';
const USER_KEY = 'careermate_user';
const SINGLE_USER_TIP = '当前为本地单用户模式，可直接进入';
const FATAL_AUTH_ERROR = /系统异常|登录失败|会话创建失败/;
const FATAL_APP_ERROR = /系统异常|会话创建失败|流式请求失败/;
const MOCK_REPLY =
  /Mock 简历分析结果|我已读取你的默认简历|我已通过工具读取你的默认简历|我已读取你最近的岗位匹配结果|我已通过工具读取你最近的岗位匹配|我已为你生成岗位匹配结果|我已为你创建面试训练|我已读取你的求职看板|还没有读取到默认简历|还没有读取到岗位匹配记录|建议你突出项目中的业务指标|建议你重点优化|匹配分数|缺失技能|Agent Trace|流式完成|DONE|MESSAGE|我记得|Java 后端|基于你之前提到的目标|求职目标|求职画像|优先准备/i;

/** @type {Array<{ username: string; email: string }>} */
const createdTestAccounts = [];

function logEnv() {
  console.log('[e2e-env]', {
    E2E_TARGET: target,
    baseURL,
    apiBaseURL,
    isCloud,
    isLocal,
    mustUseUserFlow,
  });
}

/**
 * 云端/用户流程模式：要求后端为 jwt（未登录 /auth/me 不能返回 local-user）。
 * @param {import('@playwright/test').APIRequestContext} request
 */
async function assertUserFlowEnvironment(request) {
  if (!mustUseUserFlow) return;

  const mode = await detectAuthMode(request);
  if (mode === 'jwt') {
    console.log('[user-flow] 后端为 jwt 模式，将执行完整注册/登录流程');
    return;
  }

  const probeUser = `${e2ePrefix()}_probe_${Date.now()}`;
  const registerRes = await request.post(`${apiBaseURL}/auth/register`, {
    data: {
      username: probeUser,
      password: 'Test123456!',
      email: `${probeUser}@careermate.test`,
    },
    timeout: 20_000,
  });
  const registerBody = await registerRes.json().catch(() => null);
  console.log(
    `[user-flow] single-user 模式，注册 API 探测 HTTP ${registerRes.status()}`,
    JSON.stringify(registerBody)
  );

  if (registerRes.ok() && registerBody?.code === 0) {
    console.warn(
      '[user-flow] 警告：SECURITY_MODE 仍为 single-user，将按注册/登录 UI 流程测试；建议改为 jwt 以获得一致的未登录跳转行为。'
    );
    return;
  }

  throw new Error(
    [
      '云端用户场景测试无法继续：既非 jwt，注册 API 也不可用。',
      `注册探测 HTTP ${registerRes.status()} message=${registerBody?.message || ''}`,
      '请检查 JWT_SECRET（≥32 字节）、数据库与 careermate-backend 日志。',
    ].join('\n')
  );
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
  page.addInitScript(() => {
    localStorage.setItem('careermate.navExpanded', 'true');
  });
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
  if (!/careermate/.test(page.url())) {
    await gotoApp(page, '/login');
    await waitStable(page);
  }
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

const LOCAL_USER_BADGE = /local-user\s*\/\s*USER/;
const AGENT_NETWORK_ERROR = /network error|暂未收到回复/i;

/**
 * 发送 Agent 消息并等待 Mock 回复；云端 SSE 偶发中断时重试一次。
 * @param {import('@playwright/test').Page} page
 * @param {string} [message]
 */
/**
 * 若简历尚未是默认，则点击「设为默认」并等待角标出现。
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Locator} card
 */
/**
 * @param {import('@playwright/test').Page} page
 */
async function assertNoHorizontalScroll(page) {
  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
  );
  expect(hasHorizontalOverflow).toBeFalsy();
}

async function ensureResumeIsDefault(page, card) {
  if (await card.locator('.default-badge').isVisible().catch(() => false)) {
    return;
  }
  const detailPanel = page.locator('.detail-panel');
  const setDefaultBtn = detailPanel.getByRole('button', { name: '设为默认' });
  await expect(setDefaultBtn).toBeEnabled({ timeout: 10_000 });
  await setDefaultBtn.click();
  await expect(card.locator('.default-badge')).toBeVisible({ timeout: 10_000 });
}

async function sendAgentMessageAndExpectMockReply(page, message = '帮我分析简历') {
  const input = page.locator('input[placeholder="说说你想做什么..."]');
  const sendBtn = page.getByRole('button', { name: '↑' });
  const attempts = isCloud ? 2 : 1;

  for (let attempt = 1; attempt <= attempts; attempt++) {
    await input.fill(message);
    await expect(sendBtn).toBeEnabled({ timeout: 15_000 });
    await sendBtn.click();
    await expect(page.locator('.user-bubble', { hasText: message }).last()).toBeVisible({
      timeout: 20_000,
    });
    const agentBubble = page.locator('.agent-bubble').last();
    try {
      await expect(agentBubble).toContainText(MOCK_REPLY, { timeout: 45_000 });
      const text = await agentBubble.innerText();
      if (AGENT_NETWORK_ERROR.test(text)) {
        console.warn(
          '[agent] 已收到 Mock 回复，但流式连接异常断开（常见于 nginx 未关闭缓冲）；请检查 careermate-api SSE 代理配置'
        );
      }
      return;
    } catch (err) {
      const snippet = (await agentBubble.innerText().catch(() => '')).slice(0, 160);
      if (attempt < attempts && AGENT_NETWORK_ERROR.test(snippet) && !MOCK_REPLY.test(snippet)) {
        console.log(`[agent] 第 ${attempt} 次 SSE 异常 (${snippet})，重试`);
        const newSession = page.getByRole('button', { name: /新会话/ });
        if (await newSession.isEnabled({ timeout: 3_000 }).catch(() => false)) {
          await newSession.click();
          await waitStable(page);
        }
        continue;
      }
      throw err;
    }
  }
}

/**
 * 按后端认证模式断言角标：jwt 显示注册用户；single-user 固定为 local-user。
 * @param {import('@playwright/test').Page} page
 * @param {{ username: string }} account
 * @param {'single-user' | 'jwt'} authMode
 */
async function assertAgentDashboardForAccount(page, account, authMode) {
  await assertAgentDashboard(page);
  if (authMode === 'single-user') {
    const badge = page.locator('.user-badge');
    const text = (await badge.innerText()).trim();
    const ok = LOCAL_USER_BADGE.test(text) || text.includes(account.username);
    expect(ok, `single-user 角标应为 local-user 或 ${account.username}，实际: ${text}`).toBeTruthy();
    console.log(`[badge] single-user 角标: ${text}`);
    return;
  }
  await expect(page.locator('.user-badge')).toContainText(account.username);
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
 * @param {import('@playwright/test').APIRequestContext} [request]
 */
async function registerViaUi(page, account, request) {
  await gotoApp(page, '/login');
  await waitStable(page);

  const onLoginForm = await page
    .getByRole('button', { name: '注册' })
    .isVisible({ timeout: 6_000 })
    .catch(() => false);

  if (!onLoginForm) {
    if (!request) {
      throw new Error(
        '无法显示登录/注册页（常见于 single-user 自动登录）。请设置 SECURITY_MODE=jwt，或传入 request 使用 API 注册。'
      );
    }
    console.log('[register] 登录页不可用，使用 API 注册并写入 token（后端真实注册）');
    const res = await request.post(`${apiBaseURL}/auth/register`, {
      data: {
        username: account.username,
        password: account.password,
        email: account.email,
      },
      timeout: 20_000,
    });
    const body = await res.json().catch(() => null);
    console.log(`[register] API HTTP ${res.status()}`, JSON.stringify(body));
    expect(res.ok(), `注册 API HTTP 失败: ${JSON.stringify(body)}`).toBeTruthy();
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
    await gotoApp(page, '/');
    await waitStable(page);
    const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
    expect(token).toBeTruthy();
    return;
  }

  await clearAuthStorage(page);
  await page.reload();
  await waitStable(page);
  const registerTab = page.getByRole('button', { name: '注册' });
  if (await registerTab.first().isVisible({ timeout: 4_000 }).catch(() => false)) {
    await registerTab.first().click();
  }
  await page.getByLabel('用户名').fill(account.username);
  await page.getByLabel('邮箱').fill(account.email);
  await page.getByLabel('密码').fill(account.password);
  await page.getByRole('button', { name: '注册并进入' }).click();
  await expect(page.locator('body')).not.toContainText('系统异常', { timeout: 8_000 });
  await expect(page).toHaveURL(/#\/$/, { timeout: 25_000 });
  const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
  expect(token).toBeTruthy();
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {{ username: string; password: string }} account
 */
async function loginViaUi(page, account) {
  await gotoApp(page, '/login');
  await waitStable(page);
  const usernameField = page.getByLabel('用户名');
  if (!(await usernameField.isVisible({ timeout: 4_000 }).catch(() => false))) {
    await page.getByRole('button', { name: '登录' }).first().click();
  }
  await usernameField.fill(account.username);
  await page.getByLabel('密码').fill(account.password);
  await page.locator('form .primary').click();
  await expect(page.locator('body')).not.toContainText('系统异常', { timeout: 8_000 });
  await expect(page).toHaveURL(/#\/$/, { timeout: 25_000 });
  const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
  expect(token).toBeTruthy();
}

/**
 * 真实用户进入应用：注册（首次）或登录，绝不点「进入 CareerMate」。
 * @param {import('@playwright/test').Page} page
 * @param {{ username: string; email: string; password: string } | null} [existingAccount]
 * @returns {Promise<{ username: string; email: string; password: string }>}
 */
async function enterApplicationAsUser(page, existingAccount = null) {
  let account = existingAccount;
  let onAgent = await page
    .getByText('Agent 对话台')
    .isVisible({ timeout: 3_000 })
    .catch(() => false);

  if (!onAgent) {
    await gotoApp(page, '/');
    await waitStable(page);
    onAgent = await page
      .getByText('Agent 对话台')
      .isVisible({ timeout: 3_000 })
      .catch(() => false);
    if (!onAgent) {
      await expect(page).toHaveURL(/#\/login/, { timeout: 15_000 });
    }
  }

  if (!account) {
    account = createTestCredentials();
    await registerViaUi(page, account);
  } else if (!onAgent) {
    await clearAuthStorage(page);
    await loginViaUi(page, account);
  }

  await assertAgentDashboardWithUser(page, account.username);
  return account;
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
  mustUseUserFlow,
  appUrl,
  gotoApp,
  assertUserFlowEnvironment,
  enterApplicationAsUser,
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
  assertAgentDashboardForAccount,
  LOCAL_USER_BADGE,
  ensureLoginPage,
  registerViaUi,
  loginViaUi,
  enterApplication,
  printCreatedAccountsReport,
  sendAgentMessageAndExpectMockReply,
  ensureResumeIsDefault,
  assertNoHorizontalScroll,
};
