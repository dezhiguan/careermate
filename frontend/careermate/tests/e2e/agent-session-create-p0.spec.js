const { test, expect } = require('@playwright/test');
const {
  assertBackendReady,
  attachDiagnostics,
  gotoApp,
  loginViaSmsUi,
  waitStable,
  FATAL_APP_ERROR,
} = require('./e2e-env');

const PHONE = process.env.E2E_SMS_PHONE || '18565040934';
const SMS_CODE = process.env.E2E_SMS_CODE || '123456';

test.describe('P0 agent session create', () => {
  test.beforeAll(async ({ request }) => {
    await assertBackendReady(request);
  });

  test.beforeEach(({ page }) => {
    attachDiagnostics(page);
  });

  test('mobile login can create chat session and receive first streaming chunk', async ({ page }) => {
    await gotoApp(page, '/login');
    await waitStable(page);
    await loginViaSmsUi(page, PHONE, SMS_CODE);

    await gotoApp(page, '/chat');
    await expect(page).toHaveURL(/#\/chat/);
    await expect(page.locator('body')).not.toContainText(FATAL_APP_ERROR, { timeout: 10_000 });
    await expect(page.locator('.global-error')).toHaveCount(0, { timeout: 10_000 });

    const input = page.locator('.chat-input');
    const sendButton = page.getByRole('button', { name: '发送' });
    await expect(input).toBeEnabled({ timeout: 20_000 });
    await input.fill('你好，帮我分析简历');
    await expect(sendButton).toBeEnabled({ timeout: 10_000 });

    await Promise.all([
      page.waitForResponse(
        (response) =>
          response.url().includes('/api/agent/sessions/') &&
          response.url().includes('/messages/stream') &&
          response.request().method() === 'POST',
        { timeout: 30_000 }
      ),
      sendButton.click(),
    ]);

    await expect(page.locator('.user-bubble', { hasText: '你好，帮我分析简历' })).toBeVisible({
      timeout: 10_000,
    });
    const agentBubble = page.locator('.agent-bubble').last();
    await expect(agentBubble.locator('.md-body')).not.toHaveText('', { timeout: 30_000 });
    await expect(agentBubble).not.toContainText(/系统异常|会话创建失败|流式请求失败/, { timeout: 1_000 });
    await expect(page.locator('body')).not.toContainText(FATAL_APP_ERROR, { timeout: 1_000 });
  });
});
