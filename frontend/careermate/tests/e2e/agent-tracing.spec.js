// @ts-check
/**
 * 分布式追踪 V1 回归：单轮对话、响应头、与 8 轮稳定性（复用 mock 环境）
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  assertBackendReady,
  assertUserFlowEnvironment,
  enterApplicationAsUser,
  gotoApp,
  waitStable,
} = require('./e2e-env');


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

test.describe('Agent 追踪回归', () => {
  test.beforeAll(async ({ request }) => {
    logEnv();
    await assertBackendReady(request);
    await assertUserFlowEnvironment(request);
  });

  test('健康检查响应头含 X-Request-Id 与 X-Trace-Id', async ({ request }) => {
    const { apiBaseURL } = require('./e2e-env');
    const base = apiBaseURL.replace(/\/api\/?$/, '');
    const res = await request.get(`${base}/api/health`);
    expect(res.ok()).toBeTruthy();
    expect(res.headers()['x-request-id']).toBeTruthy();
    expect(res.headers()['x-trace-id']).toBeTruthy();
  });

  test('单轮对话正常结束且输入框恢复', async ({ page }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await sendAgentMessage(page, '请介绍一下 CareerMate');
    await expect(page.locator('.stream-flag')).toHaveCount(0, { timeout: 90_000 });
    await expect(page.locator('input[placeholder="说说你想做什么..."]')).toBeEnabled({ timeout: 15_000 });
    const bubble = page.locator('.agent-bubble').last();
    await expect(bubble).not.toContainText('流式输出中', { timeout: 90_000 });
    await expect(bubble).not.toBeEmpty();
  });
});
