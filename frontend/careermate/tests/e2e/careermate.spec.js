// @ts-check
const { test, expect } = require('@playwright/test');
const {
  mustUseUserFlow,
  logEnv,
  assertBackendReady,
  assertUserFlowEnvironment,
  detectAuthMode,
  attachDiagnostics,
  waitStable,
  clearAuthStorage,
  enterFromLoginIfNeeded,
  enterApplicationAsUser,
  assertAgentDashboard,
  assertAgentDashboardWithUser,
  printCreatedAccountsReport,
  gotoApp,
  MOCK_REPLY,
  FATAL_APP_ERROR,
  ensureResumeIsDefault,
} = require('./e2e-env');

/** @type {'single-user' | 'jwt'} */
let detectedAuthMode = 'jwt';
/** @type {{ username: string; email: string; password: string } | null} */
let jwtTestAccount = null;

/**
 * @param {import('@playwright/test').Page} page
 */
async function ensureInApp(page) {
  if (mustUseUserFlow) {
    jwtTestAccount = await enterApplicationAsUser(page, jwtTestAccount);
    return;
  }

  await gotoApp(page, '/');
  await waitStable(page);

  if (detectedAuthMode === 'single-user') {
    await enterFromLoginIfNeeded(page);
    await assertAgentDashboardWithUser(page, /local-user\s*\/\s*USER/);
    return;
  }

  jwtTestAccount = await enterApplicationAsUser(page, jwtTestAccount);
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function assertNotBlank(page) {
  const text = await page.locator('main').innerText();
  expect(text.trim().length).toBeGreaterThan(20);
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function assertNoFatalErrors(page) {
  await expect(page.locator('body')).not.toContainText(FATAL_APP_ERROR);
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function assertNoHorizontalScroll(page) {
  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
  );
  expect(hasHorizontalOverflow).toBeFalsy();
}

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  detectedAuthMode = await detectAuthMode(request);
  console.log(`[careermate] 认证模式: ${detectedAuthMode}`);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('桌面端 · 本机 Chrome 功能展示', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop project');
  });

  test('1. 进入应用', async ({ page }) => {
    await ensureInApp(page);
    await assertNoFatalErrors(page);
  });

  test('2. Agent 对话', async ({ page }) => {
    await ensureInApp(page);
    const input = page.locator('input[placeholder="说说你想做什么..."]');
    const sendBtn = page.getByRole('button', { name: '↑' });
    await input.fill('帮我分析简历');
    await expect(sendBtn).toBeEnabled({ timeout: 15_000 });
    await sendBtn.click();
    await expect(page.locator('.user-bubble', { hasText: '帮我分析简历' })).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.locator('.agent-bubble').last()).toContainText(MOCK_REPLY, {
      timeout: 30_000,
    });
    await assertNoFatalErrors(page);
  });

  test('3. 简历页', async ({ page }) => {
    await ensureInApp(page);
    await page.getByRole('link', { name: /简历/ }).click();
    await expect(page).toHaveURL(/#\/resume/);
    await expect(page.getByRole('heading', { name: /简历工作室/ })).toBeVisible();
    await expect(page.getByText(/还没有简历|我的简历/)).toBeVisible();
    await assertNotBlank(page);
    await assertNoFatalErrors(page);
  });

  test('3b. 简历 CRUD', async ({ page }) => {
    await ensureInApp(page);
    const title = `e2e_resume_${Date.now()}`;
    const updatedContent = `e2e 更新正文 ${Date.now()}`;

    await page.getByRole('link', { name: /简历/ }).click();
    await expect(page).toHaveURL(/#\/resume/);
    await expect(page.getByRole('heading', { name: /简历工作室/ })).toBeVisible();

    const createPanel = page.locator('.create-panel');
    await page.getByRole('button', { name: /创建简历|新建/ }).first().click();
    await createPanel.getByPlaceholder('例如：Java 后端简历').fill(title);
    await createPanel.locator('.field-textarea').fill('e2e 初始正文');
    await createPanel.getByRole('button', { name: '保存', exact: true }).click();

    const card = page.locator('.resume-card', { hasText: title });
    await expect(card).toBeVisible({ timeout: 20_000 });
    await card.click();

    const detailPanel = page.locator('.detail-panel');
    await detailPanel.locator('.detail-textarea').fill(updatedContent);
    await detailPanel.getByRole('button', { name: '保存修改' }).click();
    await expect(card.locator('.resume-meta', { hasText: updatedContent.slice(0, 16) })).toBeVisible({
      timeout: 15_000,
    });

    await ensureResumeIsDefault(page, card);

    page.once('dialog', (dialog) => dialog.accept());
    await detailPanel.getByRole('button', { name: '删除' }).click();
    await expect(card).not.toBeVisible({ timeout: 15_000 });
  });

  test('3c. Agent 读取默认简历上下文', async ({ page }) => {
    await ensureInApp(page);
    const title = `e2e_resume_agent_context_${Date.now()}`;
    const keyword = `e2e_agent_keyword_${Date.now()}`;

    await page.getByRole('link', { name: /简历/ }).click();
    await expect(page).toHaveURL(/#\/resume/);

    const createPanel = page.locator('.create-panel');
    await page.getByRole('button', { name: /创建简历|新建/ }).first().click();
    await createPanel.getByPlaceholder('例如：Java 后端简历').fill(title);
    await createPanel.locator('.field-textarea').fill(keyword);
    await createPanel.getByRole('button', { name: '保存', exact: true }).click();

    const card = page.locator('.resume-card', { hasText: title });
    await expect(card).toBeVisible({ timeout: 20_000 });
    await card.click();
    await ensureResumeIsDefault(page, card);

    await page.getByRole('link', { name: /对话台/ }).click();
    await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 15_000 });

    const input = page.locator('input[placeholder="说说你想做什么..."]');
    const sendBtn = page.getByRole('button', { name: '↑' });
    await input.fill('帮我分析简历');
    await expect(sendBtn).toBeEnabled({ timeout: 15_000 });
    await sendBtn.click();
    await expect(page.locator('.user-bubble', { hasText: '帮我分析简历' })).toBeVisible({
      timeout: 20_000,
    });

    const agentBubble = page.locator('.agent-bubble').last();
    await expect(agentBubble).toContainText(title, { timeout: 45_000 });
    await expect(agentBubble).toContainText(/我已读取你的默认简历|Mock 简历分析结果/, {
      timeout: 45_000,
    });

    await page.getByRole('button', { name: /刷新 Trace/ }).click();
    await expect(page.locator('.tool-log', { hasText: /resume_context/ })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.locator('.tool-log', { hasText: title })).toBeVisible({ timeout: 10_000 });

    await page.getByRole('link', { name: /简历/ }).click();
    await card.click();
    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('.detail-panel').getByRole('button', { name: '删除' }).click();
    await expect(card).not.toBeVisible({ timeout: 15_000 });
  });

  test('4. 岗位匹配页', async ({ page }) => {
    await ensureInApp(page);
    await page.getByRole('link', { name: /岗位匹配/ }).click();
    await expect(page).toHaveURL(/#\/match/);
    await expect(page.getByRole('heading', { name: /岗位匹配/ })).toBeVisible();
    await expect(page.getByRole('button', { name: '开始匹配' })).toBeVisible();
    await assertNotBlank(page);
    await assertNoFatalErrors(page);
  });

  test('4b. 岗位匹配 JD 分析', async ({ page }) => {
    await ensureInApp(page);
    const resumeTitle = 'e2e_resume_match_context';
    const jobTitle = `e2e_job_java_backend_${Date.now()}`;

    await page.getByRole('link', { name: /简历/ }).click();
    const createPanel = page.locator('.create-panel');
    const existing = page.locator('.resume-card', { hasText: resumeTitle });
    if ((await existing.count()) === 0) {
      await page.getByRole('button', { name: /创建简历|新建/ }).first().click();
      await createPanel.getByPlaceholder('例如：Java 后端简历').fill(resumeTitle);
      await createPanel.locator('.field-textarea').fill(
        'Java, Spring Boot, PostgreSQL, Redis, RAGForge 项目经验'
      );
      await createPanel.getByRole('button', { name: '保存', exact: true }).click();
      await expect(page.locator('.resume-card', { hasText: resumeTitle })).toBeVisible({
        timeout: 20_000,
      });
    }
    const resumeCard = page.locator('.resume-card', { hasText: resumeTitle });
    await resumeCard.click();
    await ensureResumeIsDefault(page, resumeCard);

    await page.getByRole('link', { name: /岗位匹配/ }).click();
    await expect(page).toHaveURL(/#\/match/);

    const analyzePanel = page.locator('.analyze-panel');
    await analyzePanel.getByPlaceholder('例如：Java 后端工程师').fill(jobTitle);
    await analyzePanel.getByPlaceholder('例如：某互联网公司').fill('e2e_company');
    await analyzePanel.getByPlaceholder(/粘贴岗位描述/).fill(
      'Java, Spring Boot, Redis, Elasticsearch, Docker'
    );
    await page.getByRole('button', { name: '开始匹配' }).click();

    const modal = page.locator('.modal-card');
    await expect(modal).toBeVisible({ timeout: 25_000 });
    await expect(modal).toContainText(jobTitle);
    await expect(modal).toContainText('Java');
    await expect(modal).toContainText('Spring Boot');
    await expect(modal).toContainText('Redis');
    await expect(modal).toContainText('Elasticsearch');
    await expect(modal).toContainText('Docker');

    await modal.locator('.modal-close').click();
    const matchCard = page.locator('.job-card', { hasText: jobTitle });
    await expect(matchCard).toBeVisible({ timeout: 10_000 });

    page.once('dialog', (dialog) => dialog.accept());
    await matchCard.getByRole('button', { name: '删除' }).click();
    await expect(matchCard).not.toBeVisible({ timeout: 15_000 });
  });

  test('5. 面试特训页', async ({ page }) => {
    await ensureInApp(page);
    await page.getByRole('link', { name: /面试特训/ }).click();
    await expect(page).toHaveURL(/#\/interview/);
    await expect(page.getByRole('heading', { name: /面试特训/ })).toBeVisible();
    await assertNotBlank(page);
    await assertNoFatalErrors(page);
  });

  test('6. 求职看板页', async ({ page }) => {
    await ensureInApp(page);
    await page.getByRole('link', { name: /求职看板/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/);
    await expect(page.getByRole('heading', { name: /求职看板/ })).toBeVisible();
    await assertNotBlank(page);
    await assertNoFatalErrors(page);
  });

  test('7. 底部导航往返', async ({ page }) => {
    await ensureInApp(page);
    const links = [
      { name: /对话台/, url: /#\/$/ },
      { name: /简历/, url: /#\/resume/ },
      { name: /岗位匹配/, url: /#\/match/ },
      { name: /面试特训/, url: /#\/interview/ },
      { name: /求职看板/, url: /#\/dashboard/ },
    ];
    for (const link of links) {
      await page.getByRole('link', { name: link.name }).click();
      await expect(page).toHaveURL(link.url);
      await assertNotBlank(page);
    }
    await assertNoFatalErrors(page);
  });
});

test.describe('手机端 · 本机 Chrome 功能展示', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-mobile', '仅 mobile project');
  });

  test('各页面展示与横向滚动', async ({ page }) => {
    await ensureInApp(page);
    await assertNoHorizontalScroll(page);
    await assertNotBlank(page);

    const routes = [
      { link: /对话台/, url: /#\/$/, title: 'Agent 对话台', isHeading: false },
      { link: /简历/, url: /#\/resume/, title: /简历工作室/, isHeading: true },
      { link: /岗位匹配/, url: /#\/match/, title: /岗位匹配/, isHeading: true },
      { link: /面试特训/, url: /#\/interview/, title: /面试特训/, isHeading: true },
      { link: /求职看板/, url: /#\/dashboard/, title: /求职看板/, isHeading: true },
    ];

    for (const route of routes) {
      await page.getByRole('link', { name: route.link }).click();
      await expect(page).toHaveURL(route.url);
      const titleLocator = route.isHeading
        ? page.getByRole('heading', { name: route.title })
        : page.getByText(route.title, { exact: true });
      await expect(titleLocator).toBeVisible({ timeout: 15_000 });
      await assertNotBlank(page);
      await assertNoHorizontalScroll(page);
      await assertNoFatalErrors(page);
      await page.waitForTimeout(600);
    }
  });
});

test.afterAll(() => {
  printCreatedAccountsReport();
});
