// @ts-check
/**
 * 求职画像记忆 V1
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
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

async function sendAgentMessage(page, message) {
  const input = page.locator('input[placeholder="说说你想做什么..."]');
  const sendBtn = page.getByRole('button', { name: '↑' });
  await input.fill(message);
  await expect(sendBtn).toBeEnabled({ timeout: 15_000 });
  await sendBtn.click();
  await expect(page.locator('.user-bubble', { hasText: message }).first()).toBeVisible({
    timeout: 20_000,
  });
}

async function waitAgentReply(page) {
  const agentBubble = page.locator('.agent-bubble').last();
  await expect(agentBubble).not.toContainText('流式输出中', { timeout: 90_000 });
  await expect(page.locator('.global-error')).toHaveCount(0);
  return agentBubble;
}

async function assertAgentInputReady(page) {
  const input = page.locator('input[placeholder="说说你想做什么..."]');
  await expect(page.getByText('当前状态：').locator('..').locator('.panel-value')).toContainText(
    /已完成|空闲/,
    { timeout: 15_000 }
  );
  await expect(input).toBeEnabled({ timeout: 15_000 });
}

test.describe('Agent career profile V1', () => {
  test.beforeAll(async ({ request }) => {
    logEnv();
    await assertBackendReady(request);
    await assertUserFlowEnvironment(request);
    detectedAuthMode = await detectAuthMode(request);
  });

  test.beforeEach(({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '需右侧面板（desktop）');
    attachDiagnostics(page);
  });

  test('cross-session career profile memory and eight rounds', async ({ page }) => {
    test.setTimeout(600_000);
    await ensureInApp(page);
    await gotoAgent(page);
    await page.locator('.header-action', { hasText: /新会话/ }).click();
    await waitStable(page);

    const goalMessage = '我的目标是 Java 后端开发岗位';
    await sendAgentMessage(page, goalMessage);
    await waitAgentReply(page);
    await assertAgentInputReady(page);

    const profilePanel = page.locator('.career-profile-panel');
    await expect(profilePanel).toBeVisible({ timeout: 20_000 });
    await expect(profilePanel).toContainText(/Java\s*后端/i);

    await page.locator('.header-action', { hasText: /新会话/ }).click();
    await waitStable(page);

    await sendAgentMessage(page, '你还记得我的目标吗');
    const memoryReply = await waitAgentReply(page);
    await expect(memoryReply).toContainText(/Java\s*后端/i);
    await assertAgentInputReady(page);

    await page.reload();
    await waitStable(page);
    await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.career-profile-panel')).toContainText(/Java\s*后端/i, {
      timeout: 20_000,
    });

    await sendAgentMessage(page, '我现在应该优先准备什么');
    const prepReply = await waitAgentReply(page);
    await expect(prepReply).toContainText(/Java/i);
    await assertAgentInputReady(page);

    const roundMessages = [
      '请给我一句鼓励',
      '我该如何巩固基础知识',
      '帮我列三个学习重点',
      '今天适合投递简历吗',
      '再补充一个行动建议',
      '用一句话总结当前目标',
    ];
    for (let i = 0; i < roundMessages.length; i++) {
      await sendAgentMessage(page, `${roundMessages[i]}（画像R${i + 3}）`);
      await waitAgentReply(page);
      await assertAgentInputReady(page);
      await expect(page.locator('.stream-flag')).toHaveCount(0);
    }

    await expect(page.locator('.global-error')).toHaveCount(0);
    await expect(page.locator('.career-profile-panel')).toContainText(/Java\s*后端/i);
  });
});
