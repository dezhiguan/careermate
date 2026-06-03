// @ts-check
/**
 * Dashboard 求职任务清单 V1
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  e2ePrefix,
  assertBackendReady,
  assertUserFlowEnvironment,
  detectAuthMode,
  attachDiagnostics,
  waitStable,
  enterFromLoginIfNeeded,
  enterApplicationAsUser,
  gotoApp,
} = require('./e2e-env');

/** @type {'single-user' | 'jwt'} */
let detectedAuthMode = 'jwt';
/** @type {{ username: string; email: string; password: string } | null} */
let jwtTestAccount = null;

const TASK_TITLE = `${e2ePrefix()}_补充 Java 后端项目指标`;

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

async function gotoDashboard(page) {
  await page.getByRole('link', { name: /看板/ }).click();
  await expect(page.getByRole('heading', { name: /求职看板/ })).toBeVisible({ timeout: 15_000 });
}

async function ensureTestTask(request) {
  const { apiBaseURL } = require('./e2e-env');
  const res = await request.post(`${apiBaseURL}/tasks`, {
    data: {
      title: TASK_TITLE,
      description: 'e2e dashboard task',
      category: 'RESUME',
      priority: 'HIGH',
      dueDate: '2026-06-10',
    },
  });
  const body = await res.json();
  if (!res.ok() || body?.code !== 0) {
    throw new Error(`创建测试任务失败: ${JSON.stringify(body)}`);
  }
  return body.data?.id;
}

test.describe('Dashboard career tasks V1', () => {
  test.beforeAll(async ({ request }) => {
    logEnv();
    await assertBackendReady(request);
    await assertUserFlowEnvironment(request);
    detectedAuthMode = await detectAuthMode(request);
  });

  test.beforeEach(({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '需桌面布局');
    attachDiagnostics(page);
  });

  test('complete task on dashboard and agent still works', async ({ page, request }) => {
    test.setTimeout(600_000);
    await ensureInApp(page);
    await ensureTestTask(request);
    await gotoDashboard(page);

    await expect(page.getByText('下一步任务')).toBeVisible();
    const taskCard = page.locator('.task-card', { hasText: TASK_TITLE });
    await expect(taskCard).toBeVisible({ timeout: 15_000 });
    await taskCard.getByRole('button', { name: '完成' }).click();
    await expect(taskCard).toHaveCount(0, { timeout: 15_000 });

    await page.reload();
    await waitStable(page);
    await expect(page.getByRole('heading', { name: /求职看板/ })).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.task-card', { hasText: TASK_TITLE })).toHaveCount(0);

    await page.getByRole('link', { name: '💬 对话台', exact: true }).click();
    await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 15_000 });

    const roundMessages = [
      '你好，请用一句话回复',
      '帮我看一下默认简历',
      '看一下我的求职进展',
      '请给我一句鼓励',
      '我该如何巩固基础知识',
      '帮我列三个学习重点',
      '今天适合投递简历吗',
      '用一句话总结',
    ];
    for (let i = 0; i < roundMessages.length; i++) {
      const input = page.locator('input[placeholder="说说你想做什么..."]');
      const sendBtn = page.getByRole('button', { name: '↑' });
      await input.fill(`${roundMessages[i]}（任务R${i + 1}）`);
      await expect(sendBtn).toBeEnabled({ timeout: 20_000 });
      await sendBtn.click();
      const agentBubble = page.locator('.agent-bubble').last();
      await expect(agentBubble).not.toContainText('流式输出中', { timeout: 90_000 });
      await expect(input).toBeEnabled({ timeout: 15_000 });
      await expect(page.locator('.stream-flag')).toHaveCount(0);
    }
  });
});
