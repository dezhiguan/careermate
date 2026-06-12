// @ts-check
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  waitStable,
  enterApplicationAsUser,
  gotoApp,
  ensureResumeIsDefault,
  assertNoHorizontalScroll,
} = require('./e2e-env');

const RESUME_TITLE = 'e2e_resume_interview';
const JOB_TITLE = 'e2e_job_interview';

const LONG_ANSWER =
  '我在项目中使用 Java 和 Spring Boot 开发核心服务，引入 Redis 做缓存与热点数据治理，' +
  '并针对 Elasticsearch 与 Docker 相关能力做了学习与 PoC 验证，在压测中将接口 P99 延迟降低约 25%，' +
  '同时通过监控告警与灰度发布保障了上线稳定性，团队协作中我负责方案评审与关键模块交付。';

/** @type {{ username: string; email: string; password: string } | null} */
let jwtTestAccount = null;

/**
 * @param {import('@playwright/test').Page} page
 */
async function ensureInApp(page) {
  jwtTestAccount = await enterApplicationAsUser(page, jwtTestAccount);
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function gotoInterview(page) {
  await page.getByRole('link', { name: /面试特训/ }).click();
  await expect(page).toHaveURL(/#\/interview/);
  await expect(page.getByRole('heading', { name: /面试特训/ })).toBeVisible({ timeout: 15_000 });
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function deleteAllResumesViaUi(page) {
  await page.getByRole('link', { name: /简历/ }).click();
  await expect(page).toHaveURL(/#\/resume/);
  for (let i = 0; i < 30; i++) {
    const card = page.locator('.resume-card').first();
    if (!(await card.isVisible().catch(() => false))) {
      break;
    }
    await card.click();
    page.once('dialog', (dialog) => dialog.accept());
    await page.locator('.detail-panel').getByRole('button', { name: '删除' }).click();
    await card.waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
  }
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function ensureE2eResume(page) {
  await page.getByRole('link', { name: /简历/ }).click();
  let resumeCard = page.locator('.resume-card', { hasText: RESUME_TITLE });
  if ((await resumeCard.count()) === 0) {
    await page.getByRole('button', { name: /创建简历|新建/ }).first().click();
    const createPanel = page.locator('.create-panel');
    await createPanel.getByPlaceholder('例如：Java 后端简历').fill(RESUME_TITLE);
    await createPanel.locator('.field-textarea').fill('Java, Spring Boot, Redis');
    await createPanel.getByRole('button', { name: '保存', exact: true }).click();
    await expect(page.locator('.resume-card', { hasText: RESUME_TITLE })).toBeVisible({
      timeout: 20_000,
    });
  }
  resumeCard = page.locator('.resume-card', { hasText: RESUME_TITLE });
  await resumeCard.click();
  await ensureResumeIsDefault(page, resumeCard);
  return resumeCard;
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function ensureE2eJobMatch(page) {
  await page.getByRole('link', { name: /岗位匹配/ }).click();
  const analyzePanel = page.locator('.analyze-panel');
  const matchCard = page.locator('.job-card', { hasText: JOB_TITLE });
  if ((await matchCard.count()) === 0) {
    await analyzePanel.getByPlaceholder('例如：Java 后端工程师').fill(JOB_TITLE);
    await analyzePanel.getByPlaceholder(/粘贴岗位描述/).fill(
      'Java, Spring Boot, Redis, Elasticsearch, Docker'
    );
    await page.getByRole('button', { name: '开始匹配' }).click();
    await expect(page.locator('.modal-card')).toBeVisible({ timeout: 25_000 });
    await page.locator('.modal-card .modal-close').click();
  }
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {string | null} sessionTitle
 * @param {boolean} [removeResume]
 */
async function cleanupE2eData(page, sessionTitle, removeResume = true) {
  await gotoInterview(page);
  if (sessionTitle) {
    const sessionCard = page.locator('.session-card', { hasText: sessionTitle });
    if (await sessionCard.count()) {
      page.once('dialog', (dialog) => dialog.accept());
      await sessionCard.first().getByRole('button', { name: '删除' }).click();
      await expect(sessionCard.first()).not.toBeVisible({ timeout: 15_000 });
    }
  }

  await page.getByRole('link', { name: /岗位匹配/ }).click();
  const jobCard = page.locator('.job-card', { hasText: JOB_TITLE });
  if (await jobCard.isVisible().catch(() => false)) {
    page.once('dialog', (dialog) => dialog.accept());
    await jobCard.getByRole('button', { name: '删除' }).click();
    await expect(jobCard).not.toBeVisible({ timeout: 15_000 });
  }

  if (removeResume) {
    await page.getByRole('link', { name: /简历/ }).click();
    const resumeCard = page.locator('.resume-card', { hasText: RESUME_TITLE });
    if (await resumeCard.isVisible().catch(() => false)) {
      await resumeCard.click();
      page.once('dialog', (dialog) => dialog.accept());
      await page.locator('.detail-panel').getByRole('button', { name: '删除' }).click();
      await expect(resumeCard).not.toBeVisible({ timeout: 15_000 });
    }
  }
}

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('面试特训 V1 · 桌面端', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop');
  });

  /** @type {string} */
  let sessionTitle = '';

  test('1. 无默认简历时不能开始训练', async ({ page }) => {
    await ensureInApp(page);
    await deleteAllResumesViaUi(page);
    await gotoInterview(page);
    await page.getByRole('button', { name: '开始新的面试训练' }).click();

    const warnBanner = page.locator('.banner.warn');
    await expect(warnBanner).toBeVisible({ timeout: 10_000 });
    await expect(warnBanner).toContainText(/请先到简历页创建并设置默认简历/);
    await expect(page.locator('.question-nav .nav-item')).toHaveCount(0);
    await expect(page.locator('.session-bar-title')).toHaveCount(0);
  });

  test('2. 创建训练并生成 5 个问题', async ({ page }) => {
    await ensureInApp(page);
    await ensureE2eResume(page);
    await ensureE2eJobMatch(page);
    await gotoInterview(page);

    await page.getByRole('button', { name: '开始新的面试训练' }).click();
    await expect(page.locator('.question-nav .nav-item')).toHaveCount(5, { timeout: 20_000 });
    await expect(page.locator('.question-card .question-text')).toBeVisible();

    sessionTitle = (await page.locator('.session-bar-title').textContent())?.trim() || '';
    expect(sessionTitle.length).toBeGreaterThan(0);

    const firstNav = page.locator('.question-nav .nav-item').first();
    await firstNav.click();
    await expect(page.locator('.answer-input')).toBeVisible();
  });

  test('3. 提交回答显示分数与反馈', async ({ page }) => {
    await ensureInApp(page);
    if (!sessionTitle) {
      await ensureE2eResume(page);
      await gotoInterview(page);
      await page.getByRole('button', { name: '开始新的面试训练' }).click();
      await expect(page.locator('.question-nav .nav-item')).toHaveCount(5, { timeout: 20_000 });
      sessionTitle = (await page.locator('.session-bar-title').textContent())?.trim() || '';
    } else {
      await gotoInterview(page);
      const card = page.locator('.session-card', { hasText: sessionTitle || /面试训练/ }).first();
      if (await card.isVisible().catch(() => false)) {
        await card.click();
      } else {
        await page.getByRole('button', { name: '开始新的面试训练' }).click();
        await expect(page.locator('.question-nav .nav-item')).toHaveCount(5, { timeout: 20_000 });
      }
    }

    await page.locator('.question-nav .nav-item').first().click();
    expect(LONG_ANSWER.length).toBeGreaterThanOrEqual(100);
    await page.locator('.answer-input').fill(LONG_ANSWER);
    await page.getByRole('button', { name: '提交回答' }).click();

    const feedback = page.locator('.feedback-card');
    await expect(feedback).toBeVisible({ timeout: 20_000 });
    await expect(feedback).toContainText(/\d+\s*分/);
    await expect(feedback).toContainText(/反馈|优势|改进/);
    await expect(feedback.locator('.fb-label', { hasText: '优势' })).toBeVisible();
    await expect(feedback.locator('.fb-label', { hasText: '改进建议' })).toBeVisible();

    await expect(page.locator('.question-nav .nav-item').first()).toHaveClass(/answered/);
    await expect(page.getByRole('button', { name: '已提交' })).toBeVisible();
  });

  test('4. 完成训练显示 COMPLETED 与 summary', async ({ page }) => {
    await ensureInApp(page);
    await gotoInterview(page);

    if (sessionTitle) {
      const card = page.locator('.session-card', { hasText: sessionTitle }).first();
      if (await card.isVisible().catch(() => false)) {
        await card.click();
      }
    }

    await page.getByRole('button', { name: '完成训练' }).click();
    await expect(page.getByRole('button', { name: '已完成' })).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.session-bar-meta')).toContainText('已完成');

    await page.getByRole('button', { name: '← 返回列表' }).click();
    const listCard = page.locator('.session-card', { hasText: sessionTitle || /面试训练/ }).first();
    await expect(listCard).toBeVisible({ timeout: 10_000 });
    await expect(listCard).toContainText('已完成');
    await expect(listCard).toContainText(/平均得分|已答/);
    await expect(listCard.locator('.session-summary')).not.toBeEmpty();
  });

  test('5. 删除训练后列表与刷新均不显示', async ({ page }) => {
    await ensureInApp(page);
    await gotoInterview(page);

    const listCard = page.locator('.session-card', { hasText: sessionTitle || /面试训练/ }).first();
    await expect(listCard).toBeVisible({ timeout: 10_000 });
    const countBefore = await page.locator('.session-card').count();

    page.once('dialog', (dialog) => dialog.accept());
    await listCard.getByRole('button', { name: '删除' }).click();
    await expect(page.locator('.session-card')).toHaveCount(countBefore - 1, { timeout: 15_000 });

    await page.reload();
    await waitStable(page);
    await expect(page.locator('.session-card', { hasText: sessionTitle })).toHaveCount(0);

    await cleanupE2eData(page, null, true);
    sessionTitle = '';
  });
});

test.describe('面试特训 V1 · 手机端 390x844', () => {
  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-mobile', '仅 mobile');
  });

  test('6. 手机端创建答题无横向滚动', async ({ page }) => {
    await ensureInApp(page);
    const resume = await ensureE2eResume(page);
    await gotoInterview(page);

    await assertNoHorizontalScroll(page);
    await page.getByRole('button', { name: '开始新的面试训练' }).click();
    await expect(page.locator('.question-nav .nav-item')).toHaveCount(5, { timeout: 20_000 });
    await assertNoHorizontalScroll(page);

    await page.locator('.question-nav .nav-item').first().click();
    const textarea = page.locator('.answer-input');
    await expect(textarea).toBeVisible();
    await textarea.fill(LONG_ANSWER);
    await assertNoHorizontalScroll(page);

    await page.getByRole('button', { name: '提交回答' }).click();
    await expect(page.locator('.feedback-card')).toBeVisible({ timeout: 20_000 });
    await assertNoHorizontalScroll(page);

    const sessionTitle = (await page.locator('.session-bar-title').textContent())?.trim() || '';
    await page.getByRole('button', { name: '完成训练' }).click();
    await expect(page.getByRole('button', { name: '已完成' })).toBeVisible({ timeout: 15_000 });
    await assertNoHorizontalScroll(page);

    await page.getByRole('button', { name: '← 返回列表' }).click();
    await cleanupE2eData(page, sessionTitle, true);
  });
});
