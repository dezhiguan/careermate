// @ts-check
/**
 * Agent 会话历史列表 + 会话恢复 V1
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  waitStable,
  enterApplicationAsUser,
  gotoApp,
  FATAL_APP_ERROR,
} = require('./e2e-env');

/** @type {{ username: string; email: string; password: string } | null} */
let jwtTestAccount = null;

async function ensureInApp(page) {
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

test.describe('Agent session history V1', () => {
  test.beforeAll(async ({ request }) => {
    logEnv();
    await assertBackendReady(request);
    await assertUserFlowEnvironment(request);
    detectedAuthMode = await detectAuthMode(request);
  });

  test.beforeEach(({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '需会话历史侧栏（desktop）');
    attachDiagnostics(page);
  });

  test('refresh restores recent sessions and switching keeps context', async ({ page }) => {
    await ensureInApp(page);
    await gotoAgent(page);
    await expect(page.locator('.session-panel')).toBeVisible();
    await page.locator('.header-action', { hasText: /新会话/ }).click();
    await waitStable(page);

    await sendAgentMessage(page, '帮我看一下默认简历');
    await waitAgentReply(page);
    const resumeReply = await page.locator('.messages-area').innerText();
    expect(resumeReply).toMatch(/简历|Mock|工具|默认/i);

    await page.locator('.header-action', { hasText: /新会话/ }).click();
    await waitStable(page);

    await sendAgentMessage(page, '看一下我的求职进展');
    await waitAgentReply(page);
    const progressReply = await page.locator('.messages-area').innerText();
    expect(progressReply).toMatch(/求职|看板|进展|匹配|Mock/i);

    const resumeHistory = page.locator('.session-history-item', { hasText: '帮我看一下默认简历' });
    const progressHistory = page.locator('.session-history-item', { hasText: '看一下我的求职进展' });
    await expect(resumeHistory.first()).toBeVisible({ timeout: 15_000 });
    await expect(progressHistory.first()).toBeVisible({ timeout: 15_000 });

    await page.reload();
    await waitStable(page);
    await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 15_000 });

    await expect(progressHistory.first()).toBeVisible({ timeout: 20_000 });
    await expect(resumeHistory.first()).toBeVisible({ timeout: 20_000 });

    const latestText = await page.locator('.messages-area').innerText();
    expect(latestText).toMatch(/求职|看板|进展|匹配|Mock/i);
    expect(page.locator('.stream-flag')).toHaveCount(0);

    const progressId = await progressHistory.first().getAttribute('data-session-id');
    const resumeId = await resumeHistory.first().getAttribute('data-session-id');
    expect(progressId).toBeTruthy();
    expect(resumeId).toBeTruthy();
    expect(progressId).not.toEqual(resumeId);

    await resumeHistory.first().click();
    await waitStable(page);
    const resumeRestored = await page.locator('.messages-area').innerText();
    expect(resumeRestored).toMatch(/帮我看一下默认简历/);
    expect(resumeRestored).toMatch(/简历|Mock|工具|默认/i);
    await assertAgentInputReady(page);

    await progressHistory.first().click();
    await waitStable(page);
    const progressRestored = await page.locator('.messages-area').innerText();
    expect(progressRestored).toMatch(/看一下我的求职进展/);
    expect(progressRestored).toMatch(/求职|看板|进展|匹配|Mock/i);
    await assertAgentInputReady(page);

    for (let i = 0; i < 5; i++) {
      if (i % 2 === 0) {
        await resumeHistory.first().click();
      } else {
        await progressHistory.first().click();
      }
      await waitStable(page);
      await expect(page.locator('.stream-flag')).toHaveCount(0);
    }
    await assertAgentInputReady(page);

    await progressHistory.first().click();
    await waitStable(page);
    await assertAgentInputReady(page);

    await sendAgentMessage(page, '继续补充一条测试消息');
    await waitAgentReply(page);
    const afterSend = await page.locator('.messages-area').innerText();
    expect(afterSend).toMatch(/继续补充一条测试消息/);
    expect(afterSend).toMatch(/看一下我的求职进展/);

    await expect(page.locator('.global-error')).toHaveCount(0);
  });
});
