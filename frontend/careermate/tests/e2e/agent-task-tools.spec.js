// @ts-check
/**
 * Agent 求职任务工具：创建 / 查询 / 完成 + Dashboard 同步
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
const TASK_TITLE = `${prefix}_补充 Java 后端项目指标`;

/** @type {{ username: string; email: string; password: string } | null} */
let jwtTestAccount = null;

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

/**
 * @param {import('@playwright/test').Page} page
 * @param {string} toolLabel
 * @param {string} [replyHint]
 */
async function expectToolCardAndReply(page, toolLabel, replyHint) {
  const agentBubble = page.locator('.agent-bubble').last();
  await expect(agentBubble).toContainText(toolLabel, { timeout: 90_000 });
  if (replyHint) {
    await expect(agentBubble).toContainText(replyHint, { timeout: 90_000 });
  }
  await expect(agentBubble).not.toContainText('流式输出中', { timeout: 90_000 });
  const card = agentBubble.locator('[data-testid="tool-call-card"]').last();
  await expect(card).toBeVisible({ timeout: 15_000 });
  await expect(card.locator('[data-testid="tool-call-label"]')).toHaveText(toolLabel);
  await expect(card.locator('[data-testid="tool-call-action"]')).toHaveText('查看任务', { timeout: 15_000 });
  await assertInputReady(page);
  return card;
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

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  await cleanupPrefixedTasks(request);
});

test.beforeEach(async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'local-chrome-desktop', '任务工具 E2E 需 desktop');
  attachDiagnostics(page);
  await ensureInApp(page);
});

test.describe('Agent 求职任务工具', () => {
  test.describe.configure({ mode: 'serial' });

  test('1-4 创建任务并展示创建任务卡片', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, `帮我创建一个任务：${TASK_TITLE}`);
    await expectToolCardAndReply(page, '创建任务', '任务已创建');
  });

  test('5 查询未完成任务包含刚创建项', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, '我还有哪些任务');
    await expectToolCardAndReply(page, '求职任务', TASK_TITLE);
  });

  test('6-7 工具卡片跳转 Dashboard 展示任务', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, '我还有哪些任务');
    const card = await expectToolCardAndReply(page, '求职任务');
    await card.locator('[data-testid="tool-call-action"]').click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15_000 });
    await waitStable(page);
    await expect(page.getByRole('heading', { name: '求职看板' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('下一步任务')).toBeVisible();
    const taskCard = page.locator('.task-card', { hasText: TASK_TITLE });
    await expect(taskCard.first()).toBeVisible({ timeout: 15_000 });
  });

  test('8-10 完成任务卡片与回复', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, `${TASK_TITLE}已经做完了`);
    await expectToolCardAndReply(page, '完成任务', '任务已完成');
  });

  test('11-12 刷新 Dashboard 后任务不在未办列表', async ({ page }) => {
    await page.getByRole('link', { name: /看板|Dashboard|求职看板/ }).first().click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15_000 });
    await page.reload();
    await waitStable(page);
    await expect(page.getByRole('heading', { name: '求职看板' })).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.task-card', { hasText: TASK_TITLE })).toHaveCount(0, { timeout: 15_000 });
  });

  test('13-14 连续 8 轮对话输入框可用', async ({ page }) => {
    await gotoAgent(page);
    const rounds = [
      `帮我创建一个任务：${prefix}_round_task`,
      '我还有哪些任务',
      `${prefix}_round_task已经做完了`,
      '我的任务',
      '求职任务清单',
      '你好',
      '看一下我的求职进展',
      '第七轮确认任务清单',
    ];
    for (const msg of rounds) {
      await sendAgentMessage(page, msg);
      await assertInputReady(page);
    }
  });
});
