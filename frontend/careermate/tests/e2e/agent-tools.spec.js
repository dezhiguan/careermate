// @ts-check
/**
 * Agent 工具调用 V1 页面回归（本地 / 云端 e2e_* 前缀数据）
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  apiBaseURL,
  e2ePrefix,
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

const prefix = e2ePrefix();
const RESUME_TITLE = `${prefix}_agent_tool_resume`;
const BASELINE_JOB_TITLE = `${prefix}_agent_tool_baseline_job`;
const AGENT_JD_JOB_TITLE = 'Java 后端工程师';
const AGENT_JD_COMPANY = `${prefix}_agent_tool_co`;

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

async function gotoAgent(page) {
  await page.getByRole('link', { name: /对话台/ }).click();
  await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 15_000 });
}

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

async function ensureBaselineJobMatch(page) {
  await page.getByRole('link', { name: /岗位匹配/ }).click();
  await expect(page).toHaveURL(/#\/match/);

  const existing = page.locator('.job-card', { hasText: BASELINE_JOB_TITLE });
  if ((await existing.count()) > 0) {
    return;
  }

  const analyzePanel = page.locator('.analyze-panel');
  await analyzePanel.getByPlaceholder('例如：Java 后端工程师').fill(BASELINE_JOB_TITLE);
  await analyzePanel.getByPlaceholder('例如：某互联网公司').fill(`${prefix}_baseline_co`);
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

async function waitAgentReply(page, pattern) {
  const agentBubble = page.locator('.agent-bubble').last();
  await expect(agentBubble).toContainText(pattern, { timeout: 60_000 });
  await expect(agentBubble).not.toContainText('流式输出中', { timeout: 10_000 });
  await expect(page.locator('body')).not.toContainText(FATAL_APP_ERROR);
  return agentBubble;
}

/** 流式结束后输入框与发送按钮应恢复可用 */
async function assertAgentInputReady(page) {
  const input = page.locator('input[placeholder="说说你想做什么..."]');
  const sendBtn = page.getByRole('button', { name: '↑' });
  await expect(page.getByText('当前状态：').locator('..').locator('.panel-value')).toContainText(
    /已完成|空闲/,
    { timeout: 15_000 }
  );
  await expect(input).toBeEnabled({ timeout: 15_000 });
  await expect(sendBtn).toBeDisabled();
}

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

async function expectToolInServerTrace(page, request, toolName, options = {}) {
  const { mustNotAppear = false } = options;
  const sessionId = await readSessionIdFromPanel(page);
  const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const res = await request.get(`${apiBaseURL}/agent/sessions/${sessionId}/trace`, {
    headers,
    timeout: 20_000,
  });
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  expect(body?.code).toBe(0);
  const names = (body?.data || []).map((t) => t.toolName || t.type);
  if (mustNotAppear) {
    expect(names.filter((n) => n === toolName).length).toBe(0);
    return;
  }
  expect(names.some((n) => n === toolName)).toBeTruthy();
}

async function expectToolInUiTrace(page, toolName) {
  await expect(page.locator('.tool-log', { hasText: new RegExp(toolName) })).toBeVisible({
    timeout: 60_000,
  });
}

async function expectToolTrace(page, request, toolName) {
  await expectToolInUiTrace(page, toolName).catch(async () => {
    await page.getByRole('button', { name: /刷新 Trace/ }).click();
    await expectToolInUiTrace(page, toolName);
  });
  await expectToolInServerTrace(page, request, toolName);
}

async function expectNoBusinessToolInLatestTrace(page, request) {
  const businessTools = [
    'get_default_resume',
    'get_latest_job_match',
    'create_job_match',
    'create_interview_session',
    'get_dashboard_overview',
  ];
  const sessionId = await readSessionIdFromPanel(page);
  const token = await page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const res = await request.get(`${apiBaseURL}/agent/sessions/${sessionId}/trace`, {
    headers,
    timeout: 20_000,
  });
  const body = await res.json();
  const names = (body?.data || []).map((t) => t.toolName || t.type);
  const hit = names.filter((n) => businessTools.includes(n));
  expect(hit.length).toBe(0);
}

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  detectedAuthMode = await detectAuthMode(request);
});

test.beforeEach(({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'local-chrome-desktop', '需 Trace 侧栏（desktop）');
  attachDiagnostics(page);
});

test.describe('Agent 工具调用 V1 页面回归', () => {
  test.describe.configure({ mode: 'serial' });

  test('前置：默认简历与岗位匹配', async ({ page }) => {
    await ensureInApp(page);
    await ensureDefaultResume(page);
    await ensureBaselineJobMatch(page);
    await gotoAgent(page);
  });

  test('1. 默认简历工具', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await sendAgentMessage(page, '帮我分析默认简历');
    await waitAgentReply(page, /我已通过工具读取你的默认简历|我已读取你的默认简历/);
    await expectToolTrace(page, request, 'get_default_resume');
    await assertAgentInputReady(page);
  });

  test('2. 最近岗位工具', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await sendAgentMessage(page, '我和最近岗位差距在哪里');
    await waitAgentReply(
      page,
      /我已通过工具读取你最近的岗位匹配|我已读取你最近的岗位匹配|匹配分数|缺失技能/
    );
    await expectToolTrace(page, request, 'get_latest_job_match');
    await assertAgentInputReady(page);
  });

  test('3. 创建岗位匹配工具', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await sendAgentMessage(page, JD_FOR_AGENT_MATCH);
    await waitAgentReply(page, /我已为你生成岗位匹配结果|匹配分数/);
    await expectToolTrace(page, request, 'create_job_match');
    await assertAgentInputReady(page);

    await page.getByRole('link', { name: /岗位匹配/ }).click();
    const newMatchCard = page.locator('.job-card', { hasText: AGENT_JD_COMPANY });
    await expect(newMatchCard.first()).toBeVisible({ timeout: 15_000 });
    await expect(newMatchCard.first()).toContainText(AGENT_JD_JOB_TITLE);
    await gotoAgent(page);
  });

  test('4. 创建面试训练工具', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await sendAgentMessage(page, '帮我创建面试训练');
    await waitAgentReply(page, /我已为你创建面试训练|面试特训/);
    await expectToolTrace(page, request, 'create_interview_session');
    await assertAgentInputReady(page);

    await page.getByRole('link', { name: /面试特训/ }).click();
    await expect(page.locator('.session-card', { hasText: '面试训练' }).first()).toBeVisible({
      timeout: 15_000,
    });
    await gotoAgent(page);
  });

  test('5. 看板工具', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await sendAgentMessage(page, '看一下我的求职进展');
    await waitAgentReply(page, /我已读取你的求职看板|简历|岗位匹配|面试训练/);
    await expectToolTrace(page, request, 'get_dashboard_overview');
    await assertAgentInputReady(page);
  });

  test('6. 普通闲聊不误触发工具', async ({ page, request }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await page.getByRole('button', { name: /新会话/ }).click();
    await expect(page.getByText('新会话已重置')).toBeVisible({ timeout: 10_000 });
    await waitStable(page);
    await sendAgentMessage(page, '你好');
    await waitAgentReply(page, /Mock|CareerMate|你好|帮助|简历|岗位/);
    await expectNoBusinessToolInLatestTrace(page, request);
    await assertAgentInputReady(page);
  });
});
