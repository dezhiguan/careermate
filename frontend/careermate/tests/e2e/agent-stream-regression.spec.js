// @ts-check
/**
 * Agent SSE 多轮回归（E2E_TARGET=cloud）
 * 验证连续多轮对话不会卡在「流式输出中...」
 */
const { test, expect } = require('@playwright/test');
const {
  isCloud,
  logEnv,
  assertBackendReady,
  attachDiagnostics,
  waitStable,
  enterFromLoginIfNeeded,
  assertAgentDashboard,
  gotoApp,
  MOCK_REPLY,
} = require('./e2e-env');

const ROUNDS = [
  '帮我分析简历',
  '根据最近岗位帮我优化简历',
  '我和岗位差距在哪里',
  '帮我准备面试',
  '给我一个复盘建议',
  '总结一下',
];

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ request }) => {
  test.skip(!isCloud, '仅 E2E_TARGET=cloud 时运行');
  logEnv();
  await assertBackendReady(request);
});

test.beforeEach(({ page }) => {
  test.skip(!isCloud, '仅云端');
  attachDiagnostics(page);
});

test.describe('云端 · Agent SSE 多轮回归', () => {
  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '桌面端');
  });

  test('连续 6 轮对话不卡死且输入可恢复', async ({ page }) => {
    await gotoApp(page, '/');
    await waitStable(page);
    await enterFromLoginIfNeeded(page);
    await assertAgentDashboard(page);

    const input = page.locator('input[placeholder="说说你想做什么..."]');
    const sendBtn = page.getByRole('button', { name: '↑' });

    for (let i = 0; i < ROUNDS.length; i++) {
      const message = ROUNDS[i];
      console.log(`[agent-regression] round ${i + 1}/${ROUNDS.length}: ${message}`);

      await expect(input).toBeEnabled({ timeout: 30_000 });
      await expect(sendBtn).toBeEnabled({ timeout: 30_000 });
      await expect(page.locator('.stream-flag')).not.toBeVisible({ timeout: 5_000 }).catch(() => {});

      await input.fill(message);
      await sendBtn.click();

      await expect(page.locator('.user-bubble', { hasText: message })).toBeVisible({
        timeout: 20_000,
      });

      const agentBubble = page.locator('.agent-bubble').last();
      await expect(agentBubble).toContainText(MOCK_REPLY, { timeout: 90_000 });

      await expect(page.locator('.stream-flag')).not.toBeVisible({ timeout: 30_000 });
      await expect(input).toBeEnabled({ timeout: 30_000 });
      await expect(sendBtn).toBeEnabled({ timeout: 30_000 });

      const panelValue = page.locator('.panel-value').first();
      await expect(panelValue).not.toHaveText('流式生成中', { timeout: 5_000 });
    }
  });
});
