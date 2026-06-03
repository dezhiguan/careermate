// @ts-check
/**
 * Agent 工具调用卡片 UI + 跳转 + 多轮不卡死
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
  ensureResumeIsDefault,
} = require('./e2e-env');

const prefix = e2ePrefix();
const RESUME_TITLE = `${prefix}_agent_ui_resume`;
const BASELINE_JOB = `${prefix}_agent_ui_job`;

const JD_LONG = [
  '岗位：Java 后端工程师',
  `公司：${prefix}_ui_co`,
  '招聘要求：Java Spring Boot Redis Docker Elasticsearch Kubernetes MySQL PostgreSQL',
  '微服务 分布式 缓存 消息队列 性能优化 持续集成 单元测试',
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
  await page.getByRole('link', { name: '💬 对话台', exact: true }).click();
  await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 15_000 });
}

async function ensurePrerequisites(page) {
  await page.getByRole('link', { name: /简历/ }).click();
  let card = page.locator('.resume-card', { hasText: RESUME_TITLE });
  if ((await card.count()) === 0) {
    const panel = page.locator('.create-panel');
    await page.getByRole('button', { name: /创建简历|新建/ }).first().click();
    await panel.getByPlaceholder('例如：Java 后端简历').fill(RESUME_TITLE);
    await panel.locator('.field-textarea').fill('Java Spring Boot Redis 项目经验');
    await panel.getByRole('button', { name: '保存', exact: true }).click();
    card = page.locator('.resume-card', { hasText: RESUME_TITLE });
    await expect(card).toBeVisible({ timeout: 20_000 });
  }
  await card.click();
  await ensureResumeIsDefault(page, card);

  await page.getByRole('link', { name: /岗位匹配/ }).click();
  if ((await page.locator('.job-card', { hasText: BASELINE_JOB }).count()) === 0) {
    const ap = page.locator('.analyze-panel');
    await ap.getByPlaceholder('例如：Java 后端工程师').fill(BASELINE_JOB);
    await ap.getByPlaceholder('例如：某互联网公司').fill(`${prefix}_ui_base`);
    await ap.getByPlaceholder(/粘贴岗位描述/).fill(
      'Java Spring Boot Redis Docker Elasticsearch 任职要求 三年以上经验 分布式系统'
    );
    await page.getByRole('button', { name: '开始匹配' }).click();
    await expect(page.locator('.modal-card')).toBeVisible({ timeout: 25_000 });
    await page.locator('.modal-card .modal-close').click();
  }
  await gotoAgent(page);
}

async function sendAgentMessage(page, text) {
  const input = page.locator('input[placeholder="说说你想做什么..."]');
  const sendBtn = page.getByRole('button', { name: '↑' });
  await input.fill(text);
  await expect(sendBtn).toBeEnabled({ timeout: 15_000 });
  await sendBtn.click();
}

async function assertInputReady(page) {
  await expect(page.locator('.stream-flag')).toHaveCount(0, { timeout: 60_000 });
  await expect(page.locator('input[placeholder="说说你想做什么..."]')).toBeEnabled({ timeout: 15_000 });
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {string} toolLabel 卡片标题，如「默认简历」
 * @param {string} [actionLabel] 跳转按钮文案
 */
async function expectToolCard(page, toolLabel, actionLabel) {
  const agentBubble = page.locator('.agent-bubble').last();
  await expect(agentBubble).toContainText(toolLabel, { timeout: 60_000 });
  await expect(agentBubble).not.toContainText('流式输出中', { timeout: 60_000 });
  const card = agentBubble.locator('[data-testid="tool-call-card"]').last();
  await expect(card).toBeVisible({ timeout: 15_000 });
  await expect(card.locator('[data-testid="tool-call-label"]')).toHaveText(toolLabel);
  await expect(card.locator('.tool-call-badge')).not.toHaveText('执行中', { timeout: 15_000 });
  if (actionLabel) {
    await expect(card.locator('[data-testid="tool-call-action"]')).toHaveText(actionLabel, {
      timeout: 15_000,
    });
  }
  await assertInputReady(page);
  return card;
}

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  detectedAuthMode = await detectAuthMode(request);
});

test.beforeEach(async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'local-chrome-desktop', '工具卡片在消息区展示，需 desktop');
  attachDiagnostics(page);
  if (!testInfo.title.startsWith('前置')) {
    await ensureInApp(page);
    await ensurePrerequisites(page);
  }
});

test.describe('Agent 工具卡片 UI', () => {
  test.describe.configure({ mode: 'serial' });

  test('前置：默认简历与岗位匹配', async ({ page }) => {
    await ensureInApp(page);
    await ensurePrerequisites(page);
  });

  test('1. 默认简历工具卡片与查看简历跳转', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, '帮我看一下我的默认简历');
    const card = await expectToolCard(page, '默认简历', '查看简历');
    await card.locator('[data-testid="tool-call-action"]').click();
    await expect(page).toHaveURL(/#\/resume/, { timeout: 15_000 });
    await gotoAgent(page);
  });

  test('2. 最近岗位匹配卡片', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, '我和最近岗位的差距在哪里');
    await expectToolCard(page, '最近岗位匹配', '查看匹配');
  });

  test('3. 岗位匹配分析卡片与跳转', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, JD_LONG);
    const card = await expectToolCard(page, '岗位匹配分析', '查看匹配');
    await card.locator('[data-testid="tool-call-action"]').click();
    await expect(page).toHaveURL(/#\/match/, { timeout: 15_000 });
    await gotoAgent(page);
  });

  test('4. 面试训练卡片与跳转', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, '帮我创建一次面试训练');
    const card = await expectToolCard(page, '面试训练', '查看训练');
    await card.locator('[data-testid="tool-call-action"]').click();
    await expect(page).toHaveURL(/#\/interview/, { timeout: 15_000 });
    await gotoAgent(page);
  });

  test('5. 求职概览卡片与跳转', async ({ page }) => {
    await gotoAgent(page);
    await sendAgentMessage(page, '看一下我的求职进展');
    const card = await expectToolCard(page, '求职概览', '查看概览');
    await card.locator('[data-testid="tool-call-action"]').click();
    await expect(page).toHaveURL(/#\/dashboard/, { timeout: 15_000 });
    await gotoAgent(page);
  });

  test('6. 连续 6 轮对话不卡死', async ({ page }) => {
    await gotoAgent(page);
    const rounds = [
      { msg: '帮我看一下我的默认简历', label: '默认简历' },
      { msg: '我和最近岗位的差距在哪里', label: '最近岗位匹配' },
      { msg: '你好，谢谢', label: null },
      { msg: '看一下我的求职进展', label: '求职概览' },
      { msg: '帮我创建一次面试训练', label: '面试训练' },
      { msg: '今天天气怎么样', label: null },
    ];
    for (const round of rounds) {
      await sendAgentMessage(page, round.msg);
      await assertInputReady(page);
      if (round.label) {
        await expect(page.locator('[data-testid="tool-call-label"]').filter({ hasText: round.label }).last())
          .toBeVisible({ timeout: 60_000 });
      }
    }
  });

  test('7. 点击各工具卡片跳转路由不报错', async ({ page }) => {
    await gotoAgent(page);
    const cases = [
      { msg: '帮我看一下我的默认简历', label: '默认简历', action: '查看简历', url: /#\/resume/ },
      { msg: '我和最近岗位的差距在哪里', label: '最近岗位匹配', action: '查看匹配', url: /#\/match/ },
      { msg: '帮我创建一次面试训练', label: '面试训练', action: '查看训练', url: /#\/interview/ },
      { msg: '看一下我的求职进展', label: '求职概览', action: '查看概览', url: /#\/dashboard/ },
    ];
    for (const c of cases) {
      await sendAgentMessage(page, c.msg);
      const card = await expectToolCard(page, c.label, c.action);
      await card.locator('[data-testid="tool-call-action"]').click();
      await expect(page).toHaveURL(c.url, { timeout: 15_000 });
      await gotoAgent(page);
    }
  });

});
