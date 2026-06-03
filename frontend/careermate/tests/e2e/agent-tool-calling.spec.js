// @ts-check
/**
 * Agent 工具调用 V1 — 页面 E2E
 * 前置：默认简历 + 一条岗位匹配记录
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  apiBaseURL,
  assertBackendReady,
  assertUserFlowEnvironment,
  detectAuthMode,
  attachDiagnostics,
  waitStable,
  enterFromLoginIfNeeded,
  enterApplicationAsUser,
  gotoApp,
  ensureResumeIsDefault,
  FATAL_APP_ERROR,
  TOKEN_KEY,
} = require('./e2e-env');

const RESUME_TITLE = 'e2e_agent_tool_resume';
const BASELINE_JOB_TITLE = 'e2e_agent_tool_baseline_job';
const AGENT_JD_JOB_TITLE = 'Java 后端工程师';
const AGENT_JD_COMPANY = 'e2e_agent_tool_co';

const JD_FOR_AGENT_MATCH = [
  `岗位：${AGENT_JD_JOB_TITLE}`,
  `公司：${AGENT_JD_COMPANY}`,
  '招聘要求：Java Spring Boot Redis Docker Elasticsearch Kubernetes MySQL PostgreSQL',
  '微服务架构 分布式系统 缓存设计 消息队列 性能优化 持续集成 单元测试 代码评审',
].join('\n');

/** @type {'single-user' | 'jwt'} */
let detectedAuthMode = 'jwt';
/** @type {{ username: string; email: string; password: string } | null} */
let jwtTestAccount = null;

/**
 * @param {import('@playwright/test').Page} page
 */
async function ensureInApp(page) {
  const { mustUseUserFlow } = require('./e2e-env');
  if (mustUseUserFlow) {
    jwtTestAccount = await enterApplicationAsUser(page, jwtTestAccount);
    return;
  }
  await gotoApp(page, '/');
  await waitStable(page);
  if (detectedAuthMode === 'single-user') {
    await enterFromLoginIfNeeded(page);
    return;
  }
  jwtTestAccount = await enterApplicationAsUser(page, jwtTestAccount);
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function gotoAgent(page) {
  await page.getByRole('link', { name: /对话台/ }).click();
  await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 15_000 });
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function ensureDefaultResume(page) {
  await page.getByRole('link', { name: /简历/ }).click();
  await expect(page).toHaveURL(/#\/resume/);

  let card = page.locator('.resume-card', { hasText: RESUME_TITLE });
  if ((await card.count()) === 0) {
    const createPanel = page.locator('.create-panel');
    await page.getByRole('button', { name: /创建简历|新建/ }).first().click();
    await createPanel.getByPlaceholder('例如：Java 后端简历').fill(RESUME_TITLE);
    await createPanel.locator('.field-textarea').fill(
      'Java, Spring Boot, Redis, PostgreSQL, 微服务项目经验，熟悉 Docker 与 Elasticsearch 基础使用。'
    );
    await createPanel.getByRole('button', { name: '保存', exact: true }).click();
    card = page.locator('.resume-card', { hasText: RESUME_TITLE });
    await expect(card).toBeVisible({ timeout: 20_000 });
  }
  await card.click();
  await ensureResumeIsDefault(page, card);
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function ensureBaselineJobMatch(page) {
  await page.getByRole('link', { name: /岗位匹配/ }).click();
  await expect(page).toHaveURL(/#\/match/);

  const existing = page.locator('.job-card', { hasText: BASELINE_JOB_TITLE });
  if ((await existing.count()) > 0) {
    return;
  }

  const analyzePanel = page.locator('.analyze-panel');
  await analyzePanel.getByPlaceholder('例如：Java 后端工程师').fill(BASELINE_JOB_TITLE);
  await analyzePanel.getByPlaceholder('例如：某互联网公司').fill('e2e_baseline_co');
  await analyzePanel.getByPlaceholder(/粘贴岗位描述/).fill(
    'Java, Spring Boot, Redis, Elasticsearch, Docker, 任职要求 3 年以上后端开发经验，熟悉分布式与高并发场景。'
  );
  await page.getByRole('button', { name: '开始匹配' }).click();
  await expect(page.locator('.modal-card')).toBeVisible({ timeout: 25_000 });
  await page.locator('.modal-card .modal-close').click();
  await expect(page.locator('.job-card', { hasText: BASELINE_JOB_TITLE })).toBeVisible({
    timeout: 15_000,
  });
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {string} message
 */
async function sendAgentMessage(page, message) {
  const input = page.locator('input[placeholder="说说你想做什么..."]');
  const sendBtn = page.getByRole('button', { name: '↑' });
  await input.fill(message);
  await expect(sendBtn).toBeEnabled({ timeout: 15_000 });
  await sendBtn.click();
  await expect(page.locator('.user-bubble', { hasText: message })).toBeVisible({
    timeout: 20_000,
  });
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {RegExp | string} pattern
 */
async function waitAgentReply(page, pattern) {
  const agentBubble = page.locator('.agent-bubble').last();
  await expect(agentBubble).toContainText(pattern, { timeout: 60_000 });
  await expect(agentBubble).not.toContainText('流式输出中', { timeout: 5_000 }).catch(() => {});
  await expect(page.locator('body')).not.toContainText(FATAL_APP_ERROR);
  return agentBubble;
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function readSessionIdFromPanel(page) {
  const sections = page.locator('.panel-section');
  const count = await sections.count();
  for (let i = 0; i < count; i++) {
    const label = await sections.nth(i).locator('.panel-label').innerText().catch(() => '');
    if (label.includes('sessionId')) {
      const value = (await sections.nth(i).locator('.panel-value').innerText()).trim();
      if (value && !value.includes('创建中')) {
        return value;
      }
    }
  }
  throw new Error('无法在侧栏读取 sessionId');
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} toolName
 */
async function expectToolInServerTrace(page, request, toolName) {
  const sessionId = await readSessionIdFromPanel(page);
  const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const res = await request.get(`${apiBaseURL}/agent/sessions/${sessionId}/trace`, {
    headers,
    timeout: 20_000,
  });
  expect(res.ok(), `trace API HTTP ${res.status()}`).toBeTruthy();
  const body = await res.json();
  expect(body?.code).toBe(0);
  const names = (body?.data || []).map((t) => t.toolName || t.type);
  expect(
    names.some((n) => n === toolName),
    `服务端 trace 应包含 ${toolName}，实际: ${names.join(', ')}`
  ).toBeTruthy();
  const toolTrace = body.data.find((t) => (t.toolName || t.type) === toolName);
  const summary = `${toolTrace?.requestSummary || ''}${toolTrace?.responseSummary || ''}`;
  expect(summary.length).toBeLessThan(8000);
  if (toolName === 'create_job_match' && toolTrace?.status === 'SUCCESS') {
    expect(summary).toContain('jdContentLength');
    expect(summary).not.toMatch(/"jdContent"\s*:\s*"/);
  }
}

/**
 * Trace 在 SSE tool_start/tool_result 与流结束后服务端刷新中逐步出现。
 * @param {import('@playwright/test').Page} page
 * @param {string} toolName
 */
async function expectToolInUiTrace(page, toolName) {
  const pattern = new RegExp(toolName);
  await expect(page.locator('.tool-log', { hasText: pattern })).toBeVisible({ timeout: 60_000 });
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} toolName
 */
async function expectToolTrace(page, request, toolName) {
  await expectToolInUiTrace(page, toolName).catch(async () => {
    await page.getByRole('button', { name: /刷新 Trace/ }).click();
    await expectToolInUiTrace(page, toolName);
  });
  await expectToolInServerTrace(page, request, toolName);
}

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  detectedAuthMode = await detectAuthMode(request);
  console.log(`[agent-tool] 认证模式: ${detectedAuthMode}`);
});

test.beforeEach(({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop project（需 Trace 侧栏）');
  attachDiagnostics(page);
});

test.describe('Agent 工具调用 V1', () => {
  test.describe.configure({ mode: 'serial' });

  test('前置：创建默认简历与岗位匹配', async ({ page }) => {
    await ensureInApp(page);
    await ensureDefaultResume(page);
    await ensureBaselineJobMatch(page);
    await gotoAgent(page);
  });

  test('1. 默认简历工具 get_default_resume', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);

    await sendAgentMessage(page, '帮我分析默认简历');
    await waitAgentReply(page, /我已通过工具读取你的默认简历|我已读取你的默认简历/);
    await expectToolTrace(page, request, 'get_default_resume');
  });

  test('2. 最近岗位匹配工具 get_latest_job_match', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);

    await sendAgentMessage(page, '我和最近岗位差距在哪里');
    const bubble = await waitAgentReply(
      page,
      /我已通过工具读取你最近的岗位匹配|我已读取你最近的岗位匹配|匹配分数|缺失技能|补齐/
    );
    await expect(bubble).toContainText(new RegExp(BASELINE_JOB_TITLE.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '|匹配'));
    await expectToolTrace(page, request, 'get_latest_job_match');
  });

  test('3. 创建岗位匹配工具 create_job_match', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);

    await sendAgentMessage(page, JD_FOR_AGENT_MATCH);
    await waitAgentReply(page, /我已为你生成岗位匹配结果|匹配分数/);
    await expectToolTrace(page, request, 'create_job_match');

    await page.getByRole('link', { name: /岗位匹配/ }).click();
    await expect(page).toHaveURL(/#\/match/);
    const card = page.locator('.job-card', { hasText: AGENT_JD_JOB_TITLE });
    await expect(card).toBeVisible({ timeout: 15_000 });
    await expect(card).toContainText(AGENT_JD_COMPANY);
  });

  test('4. 创建面试训练工具 create_interview_session', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);

    await sendAgentMessage(page, '帮我创建面试训练');
    await waitAgentReply(page, /我已为你创建面试训练|面试特训/);
    await expectToolTrace(page, request, 'create_interview_session');

    await page.getByRole('link', { name: /面试特训/ }).click();
    await expect(page).toHaveURL(/#\/interview/);
    await expect(page.locator('.session-card', { hasText: '面试训练' }).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  test('5. 看板工具 get_dashboard_overview', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);

    await sendAgentMessage(page, '看一下我的求职进展');
    await waitAgentReply(page, /我已读取你的求职看板|简历|岗位匹配|面试训练/);
    await expectToolTrace(page, request, 'get_dashboard_overview');
  });
});
