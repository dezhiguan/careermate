// @ts-check
/**
 * 云端部署后轻量 smoke：健康检查 + 进入 Agent + 单轮对话。
 * 不跑完整 8 轮、不创建任务、不写简历/岗位/面试等业务数据。
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  assertBackendReady,
  detectAuthMode,
  attachDiagnostics,
  waitStable,
  enterFromLoginIfNeeded,
  enterApplicationAsUser,
  gotoApp,
  FATAL_APP_ERROR,
} = require('./e2e-env');

const PROMPT = '请介绍一下 CareerMate';

/** @type {'single-user' | 'jwt'} */
let authMode = 'jwt';

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  authMode = await detectAuthMode(request);
  console.log(`[cloud-smoke] 认证模式: ${authMode}`);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('云端部署后 smoke', () => {
  test.describe.configure({ mode: 'serial' });

  test('API 健康检查返回 200', async ({ request }) => {
    const { apiBaseURL } = require('./e2e-env');
    const healthUrl = `${apiBaseURL}/health`;
    const response = await request.get(healthUrl, { timeout: 20_000 });
    expect(response.status()).toBe(200);
  });

  test('打开前端、进入 Agent 对话台并收到回复', async ({ page }) => {
    await gotoApp(page, '/');
    await waitStable(page);

    if (authMode === 'single-user') {
      await enterFromLoginIfNeeded(page);
    } else {
      await enterApplicationAsUser(page, null);
    }

    await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 25_000 });
    await expect(page.locator('.user-badge')).toBeVisible();
    await expect(page.locator('input[placeholder="说说你想做什么..."]')).toBeVisible();

    const input = page.locator('input[placeholder="说说你想做什么..."]');
    const sendBtn = page.getByRole('button', { name: '↑' });
    await input.fill(PROMPT);
    await expect(sendBtn).toBeEnabled({ timeout: 15_000 });
    await sendBtn.click();

    await expect(page.locator('.user-bubble', { hasText: PROMPT })).toBeVisible({
      timeout: 20_000,
    });

    const agentBubble = page.locator('.agent-bubble').last();
    await expect(agentBubble).not.toContainText('流式输出中', { timeout: 120_000 });

    const replyText = (await agentBubble.innerText()).trim();
    expect(replyText.length, 'Agent 应有可见回复内容').toBeGreaterThan(5);

    await expect(page.locator('.stream-flag')).toHaveCount(0, { timeout: 15_000 });
    await expect(input).toBeEnabled({ timeout: 15_000 });
    await expect(page.locator('body')).not.toContainText(FATAL_APP_ERROR);
  });
});
