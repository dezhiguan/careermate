// @ts-check
/**
 * LLM 切换收口回归：mock 8 轮、任务工具、会话恢复；可选真实 Qwen
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  e2ePrefix,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  waitStable,
  enterApplicationAsUser,
  gotoApp,
} = require('./e2e-env');

const prefix = e2ePrefix();
const TASK_TITLE = `${prefix}_llm_reg_补充 Java 后端项目指标`;


async function ensureInApp(page) {
  jwtTestAccount = await enterApplicationAsUser(page, jwtTestAccount);
}

async function gotoAgent(page) {
  await page.getByRole('link', { name: '💬 对话台', exact: true }).click();
  await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 15_000 });
}

async function sendAgentMessage(page, text) {
  const input = page.locator('input[placeholder="说说你想做什么..."]');
  const sendBtn = page.getByRole('button', { name: '↑' });
  await input.fill(text);
  await expect(sendBtn).toBeEnabled({ timeout: 15_000 });
  await sendBtn.click();
}

async function assertInputReady(page) {
  await expect(page.locator('.stream-flag')).toHaveCount(0, { timeout: 90_000 });
  await expect(page.locator('input[placeholder="说说你想做什么..."]')).toBeEnabled({ timeout: 15_000 });
}

async function waitAgentReply(page) {
  const agentBubble = page.locator('.agent-bubble').last();
  await expect(agentBubble).not.toContainText('流式输出中', { timeout: 90_000 });
  return agentBubble;
}

async function cleanupPrefixedTasks(request) {
  const { apiBaseURL } = require('./e2e-env');
  const res = await request.get(`${apiBaseURL}/tasks`);
  const body = await res.json();
  const tasks = Array.isArray(body?.data) ? body.data : [];
  for (const task of tasks) {
    if (typeof task?.title === 'string' && task.title.startsWith(prefix)) {
      await request.delete(`${apiBaseURL}/tasks/${task.id}`);
    }
  }
}

test.describe('Agent LLM 收口回归', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeAll(async ({ request }) => {
    logEnv();
    await assertBackendReady(request);
    await assertUserFlowEnvironment(request);
    detectedAuthMode = await detectAuthMode(request);
    await cleanupPrefixedTasks(request);
  });

  test.beforeEach(({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '需 desktop 布局');
    attachDiagnostics(page);
  });

  test('mock：连续 8 轮不卡死', async ({ page }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    const rounds = [
      '你好，请用一句话回复',
      '帮我看一下默认简历',
      '我还有哪些任务',
      '看一下我的求职进展',
      '请给我一句鼓励',
      '帮我列三个学习重点',
      '今天适合投递简历吗',
      '用一句话总结',
    ];
    for (let i = 0; i < rounds.length; i++) {
      await sendAgentMessage(page, `${rounds[i]}（LLMR${i + 1}）`);
      await assertInputReady(page);
      const bubble = page.locator('.agent-bubble').last();
      await expect(bubble).not.toHaveText('', { timeout: 90_000 });
    }
  });

  test('工具链路：创建 / 查看 / 完成 + Dashboard', async ({ page }) => {
    await ensureInApp(page);
    await gotoAgent(page);

    await sendAgentMessage(page, `帮我创建一个任务：${TASK_TITLE}`);
    const createBubble = await waitAgentReply(page);
    await expect(createBubble).toContainText('创建任务');
    await expect(createBubble).toContainText('任务已创建');
    await assertInputReady(page);

    await sendAgentMessage(page, '我还有哪些任务');
    const listBubble = await waitAgentReply(page);
    await expect(listBubble).toContainText('求职任务');
    await expect(listBubble).toContainText(TASK_TITLE);
    await assertInputReady(page);

    const card = listBubble.locator('[data-testid="tool-call-card"]').last();
    await card.locator('[data-testid="tool-call-action"]').click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15_000 });
    await waitStable(page);
    await expect(page.locator('.task-card', { hasText: TASK_TITLE }).first()).toBeVisible({
      timeout: 15_000,
    });

    await gotoAgent(page);
    await sendAgentMessage(page, `${TASK_TITLE}已经做完了`);
    const doneBubble = await waitAgentReply(page);
    await expect(doneBubble).toContainText('完成任务');
    await expect(doneBubble).toContainText('任务已完成');
    await assertInputReady(page);

    await page.getByRole('link', { name: /看板/ }).first().click();
    await page.reload();
    await waitStable(page);
    await expect(page.locator('.task-card', { hasText: TASK_TITLE })).toHaveCount(0, {
      timeout: 15_000,
    });
  });

  test('会话恢复：刷新后最近会话可恢复', async ({ page }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await page.locator('.header-action', { hasText: /新会话/ }).click();
    await waitStable(page);

    const marker = `${prefix}_session_marker_${Date.now()}`;
    await sendAgentMessage(page, marker);
    await waitAgentReply(page);
    await assertInputReady(page);

    await page.reload();
    await waitStable(page);
    await gotoAgent(page);
    await expect(page.locator('.session-panel')).toBeVisible();
    await expect(page.locator('.messages-area')).toContainText(marker, { timeout: 20_000 });
  });

  test('可选：真实 Qwen 回复', async ({ page }) => {
    const provider = process.env.LLM_PROVIDER || process.env.E2E_LLM_PROVIDER;
    const apiKey = process.env.LLM_API_KEY || process.env.E2E_LLM_API_KEY;
    test.skip(
      provider !== 'qwen' || !apiKey || apiKey.includes('your_'),
      '未配置真实 Qwen（需 LLM_PROVIDER=qwen 与 LLM_API_KEY）'
    );

    await ensureInApp(page);
    await gotoAgent(page);
    await sendAgentMessage(page, '请用一句话介绍 CareerMate');
    const bubble = await waitAgentReply(page);
    const text = await bubble.innerText();
    expect(text).not.toContain('这是 Mock CareerMate 回复：我可以帮助你做简历优化');
    expect(text.toLowerCase()).not.toContain('sk-');
    expect(text.toLowerCase()).not.toContain('api key');
    await assertInputReady(page);
  });
});
