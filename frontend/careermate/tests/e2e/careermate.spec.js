// @ts-check
const { test, expect } = require('@playwright/test');

const MOCK_REPLY =
  /Mock 简历分析结果|建议你突出项目中的业务指标|Agent Trace|流式完成|DONE|MESSAGE/i;
const FATAL_ERROR = /系统异常|会话创建失败|流式请求失败/;

/**
 * @param {import('@playwright/test').APIRequestContext} request
 */
async function assertBackendReady(request) {
  const base = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:5173';
  let response;
  try {
    response = await request.get(`${base}/api/health`, { timeout: 15_000 });
  } catch (err) {
    throw new Error(
      [
        '无法访问后端 API（/api/health）。',
        '请先启动 backend（默认 http://localhost:8080），SECURITY_MODE=single-user，并确保数据库可用。',
        '前端 Vite 需将 /api 代理到后端（见 .env.development）。',
        `详情: ${err instanceof Error ? err.message : String(err)}`,
      ].join('\n')
    );
  }
  if (!response.ok()) {
    const body = await response.text().catch(() => '');
    throw new Error(`后端健康检查失败 HTTP ${response.status()}。\n${body}`);
  }
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function enterAsSingleUser(page) {
  await page.goto('/');
  const enterBtn = page.getByRole('button', { name: '进入 CareerMate' });
  if (await enterBtn.isVisible({ timeout: 8_000 }).catch(() => false)) {
    await enterBtn.click();
  }
  await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 25_000 });
  await expect(page.locator('.user-badge')).toContainText(/local-user\s*\/\s*USER/);
  await expect(page.getByRole('button', { name: /新会话/ })).toBeVisible();
  await expect(page.locator('input[placeholder="说说你想做什么..."]')).toBeVisible();
  await expect(page.getByText(/^s_/)).toBeVisible({ timeout: 25_000 });
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function assertNoFatalErrors(page) {
  await expect(page.locator('body')).not.toContainText(FATAL_ERROR);
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
async function assertNoHorizontalScroll(page) {
  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
  );
  expect(hasHorizontalOverflow).toBeFalsy();
}

test.beforeAll(async ({ request }) => {
  await assertBackendReady(request);
});

test.describe('桌面端 · 本机 Chrome 完整展示', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 local-chrome-desktop');
  });

  test('1. single-user 进入应用', async ({ page }) => {
    await enterAsSingleUser(page);
    await assertNoFatalErrors(page);
  });

  test('2. Agent 对话 SSE mock', async ({ page }) => {
    await enterAsSingleUser(page);
    const input = page.locator('input[placeholder="说说你想做什么..."]');
    const sendBtn = page.getByRole('button', { name: '↑' });

    await input.fill('帮我分析简历');
    await expect(sendBtn).toBeEnabled({ timeout: 10_000 });
    await sendBtn.click();

    await expect(page.locator('.user-bubble', { hasText: '帮我分析简历' })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.locator('.agent-bubble').last()).toContainText(MOCK_REPLY, {
      timeout: 20_000,
    });
    await assertNoFatalErrors(page);
  });

  test('3. 简历页', async ({ page }) => {
    await enterAsSingleUser(page);
    await page.getByRole('link', { name: /简历/ }).click();
    await expect(page).toHaveURL(/#\/resume/);
    await expect(page.getByRole('heading', { name: /简历工作室/ })).toBeVisible();
    await expect(page.getByText('上传简历')).toBeVisible();
    await expect(page.getByText('Agent 简历分析摘要')).toBeVisible();
    await assertNotBlank(page);
    await assertNoFatalErrors(page);
  });

  test('4. 岗位匹配与详情弹窗', async ({ page }) => {
    await enterAsSingleUser(page);
    await page.getByRole('link', { name: /岗位匹配/ }).click();
    await expect(page).toHaveURL(/#\/match/);
    await expect(page.locator('.job-card').first()).toBeVisible();
    await page.locator('.job-card').first().click();
    const modal = page.locator('.modal-card');
    await expect(modal).toBeVisible({ timeout: 10_000 });
    await expect(modal).toContainText(/匹配/);
    await expect(modal).toContainText(/技能/);
    await expect(modal.getByRole('link', { name: /回对话台查看深度分析/ })).toBeVisible();
    await assertNotBlank(page);
    await assertNoFatalErrors(page);
  });

  test('5. 面试特训页', async ({ page }) => {
    await enterAsSingleUser(page);
    await page.getByRole('link', { name: /面试特训/ }).click();
    await expect(page).toHaveURL(/#\/interview/);
    await expect(page.getByRole('heading', { name: /面试特训/ })).toBeVisible();
    await expect(page.getByText(/第 \d+ 题/)).toBeVisible();
    await expect(page.locator('textarea[placeholder="输入你的回答..."]')).toBeVisible();
    await expect(page.getByRole('button', { name: '提交回答' })).toBeVisible();
    await assertNotBlank(page);
    await assertNoFatalErrors(page);
  });

  test('6. 求职看板页', async ({ page }) => {
    await enterAsSingleUser(page);
    await page.getByRole('link', { name: /求职看板/ }).click();
    await expect(page).toHaveURL(/#\/dashboard/);
    await expect(page.getByRole('heading', { name: /求职看板/ })).toBeVisible();
    await expect(page.getByText('Agent 建议的下一步')).toBeVisible();
    await expect(page.getByText('最近活动')).toBeVisible();
    await assertNotBlank(page);
    await assertNoFatalErrors(page);
  });

  test('7. 底部导航往返', async ({ page }) => {
    await enterAsSingleUser(page);
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
      await expect(page.locator('.bottom-nav')).toBeVisible();
      await assertNotBlank(page);
    }
    await assertNoFatalErrors(page);
  });
});

test.describe('手机端 · 本机 Chrome 完整展示', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-mobile', '仅 local-chrome-mobile');
  });

  test('各页面展示与横向滚动检查', async ({ page }) => {
    await enterAsSingleUser(page);
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
      await expect(page.locator('.bottom-nav')).toBeVisible();
      await assertNotBlank(page);
      await assertNoHorizontalScroll(page);
      await assertNoFatalErrors(page);
      await page.waitForTimeout(800);
    }
  });
});
