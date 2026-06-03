// @ts-check
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
  ensureResumeIsDefault,
  assertNoHorizontalScroll,
} = require('./e2e-env');

const RESUME_TITLE = 'e2e_resume_dashboard';
const JOB_TITLE = 'e2e_job_dashboard';

const LONG_ANSWER =
  '我在项目中使用 Java 和 Spring Boot 开发核心服务，引入 Redis 做缓存与热点数据治理，' +
  '并针对 Elasticsearch 与 Docker 相关能力做了学习与 PoC 验证，在压测中将接口 P99 延迟降低约 25%，' +
  '同时通过监控告警与灰度发布保障了上线稳定性，团队协作中我负责方案评审与关键模块交付。';

/** @type {'single-user' | 'jwt'} */
let detectedAuthMode = 'jwt';
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

async function gotoDashboard(page) {
  await page.getByRole('link', { name: /求职看板/ }).click();
  await expect(page).toHaveURL(/#\/dashboard/);
  await expect(page.getByRole('heading', { name: /求职看板/ })).toBeVisible({ timeout: 15_000 });
}

async function waitDashboardReady(page) {
  await expect(page.locator('.state-hint')).not.toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.banner.error')).not.toBeVisible({ timeout: 5_000 }).catch(() => {});
  await expect(page.locator('.stats-grid')).toBeVisible({ timeout: 20_000 });
}

/** @param {import('@playwright/test').Page} page @param {string} label */
function statValueLocator(page, label) {
  return page.locator('.stat-card').filter({ hasText: label }).locator('.stat-value');
}

async function deleteAllResumesViaUi(page) {
  await page.getByRole('link', { name: /简历/ }).click();
  for (let i = 0; i < 30; i++) {
    const card = page.locator('.resume-card').first();
    if (!(await card.isVisible().catch(() => false))) break;
    await card.click();
    page.once('dialog', (d) => d.accept());
    await page.locator('.detail-panel').getByRole('button', { name: '删除' }).click();
    await card.waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
  }
}

async function deleteAllJobMatchesViaUi(page) {
  await page.getByRole('link', { name: /岗位匹配/ }).click();
  for (let i = 0; i < 30; i++) {
    const card = page.locator('.job-card').first();
    if (!(await card.isVisible().catch(() => false))) break;
    page.once('dialog', (d) => d.accept());
    await card.getByRole('button', { name: '删除' }).click();
    await card.waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
  }
}

async function deleteAllInterviewSessionsViaUi(page) {
  await page.getByRole('link', { name: /面试特训/ }).click();
  for (let i = 0; i < 30; i++) {
    const card = page.locator('.session-card').first();
    if (!(await card.isVisible().catch(() => false))) break;
    page.once('dialog', (d) => d.accept());
    await card.getByRole('button', { name: '删除' }).click();
    await card.waitFor({ state: 'hidden', timeout: 15_000 }).catch(() => {});
  }
}

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
}

async function ensureE2eJobMatch(page) {
  await page.getByRole('link', { name: /岗位匹配/ }).click();
  const analyzePanel = page.locator('.analyze-panel');
  if ((await page.locator('.job-card', { hasText: JOB_TITLE }).count()) === 0) {
    await analyzePanel.getByPlaceholder('例如：Java 后端工程师').fill(JOB_TITLE);
    await analyzePanel.getByPlaceholder(/粘贴岗位描述/).fill(
      'Java, Spring Boot, Redis, Elasticsearch, Docker'
    );
    await page.getByRole('button', { name: '开始匹配' }).click();
    await expect(page.locator('.modal-card')).toBeVisible({ timeout: 25_000 });
    await page.locator('.modal-card .modal-close').click();
  }
}

async function ensureE2eInterviewAnswered(page) {
  await page.getByRole('link', { name: /面试特训/ }).click();
  const inDetail = (await page.locator('.question-nav .nav-item').count()) > 0;
  if (!inDetail) {
    await page.getByRole('button', { name: '开始新的面试训练' }).click();
    await expect(page.locator('.question-nav .nav-item')).toHaveCount(5, { timeout: 20_000 });
  }
  const pendingNav = page.locator('.question-nav .nav-item:not(.answered)').first();
  if (await pendingNav.isVisible().catch(() => false)) {
    await pendingNav.click();
  } else {
    await page.locator('.question-nav .nav-item').first().click();
  }
  const submitBtn = page.getByRole('button', { name: '提交回答' });
  if (await submitBtn.isEnabled({ timeout: 5_000 }).catch(() => false)) {
    await page.locator('.answer-input').fill(LONG_ANSWER);
    await submitBtn.click();
    await expect(page.locator('.feedback-card')).toBeVisible({ timeout: 20_000 });
  }
}

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  detectedAuthMode = await detectAuthMode(request);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('求职看板 V1 · 桌面端', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop');
  });

  test('1. 空数据看板', async ({ page }) => {
    await ensureInApp(page);
    await deleteAllInterviewSessionsViaUi(page);
    await deleteAllJobMatchesViaUi(page);
    await deleteAllResumesViaUi(page);

    await gotoDashboard(page);
    await waitDashboardReady(page);

    await expect(statValueLocator(page, '简历数')).toHaveText('0');
    await expect(statValueLocator(page, '岗位匹配数')).toHaveText('0');
    await expect(statValueLocator(page, '面试训练数')).toHaveText('0');
    await expect(page.locator('.summary-panel')).toContainText('未设置');
    await expect(page.locator('.suggestion-card', { hasText: '创建默认简历' })).toBeVisible();
    await expect(page.locator('.suggestion-card .card-link', { hasText: /去创建简历/ })).toBeVisible();
  });

  test('2. 有默认简历', async ({ page }) => {
    await ensureInApp(page);
    await ensureE2eResume(page);

    await gotoDashboard(page);
    await waitDashboardReady(page);

    const resumeCount = Number(await statValueLocator(page, '简历数').innerText());
    expect(resumeCount).toBeGreaterThan(0);
    await expect(page.locator('.summary-panel')).toContainText(RESUME_TITLE);
    await expect(page.locator('.suggestion-card', { hasText: '录入岗位 JD' })).toBeVisible();
    await expect(page.locator('.suggestion-card .card-link', { hasText: /去岗位匹配/ })).toBeVisible();
  });

  test('3. 有岗位匹配', async ({ page }) => {
    await ensureInApp(page);
    await ensureE2eJobMatch(page);

    await gotoDashboard(page);
    await waitDashboardReady(page);

    const matchCount = Number(await statValueLocator(page, '岗位匹配数').innerText());
    expect(matchCount).toBeGreaterThan(0);
    await expect(page.locator('.summary-panel')).toContainText(JOB_TITLE);
    await expect(page.locator('.summary-panel')).toContainText(/匹配\s*\d+%/);
    await expect(page.locator('.timeline-item', { hasText: '岗位匹配' })).toBeVisible();
    await expect(page.locator('.timeline-item', { hasText: JOB_TITLE })).toBeVisible();
    await expect(page.locator('.suggestion-card', { hasText: '开始面试训练' })).toBeVisible();
  });

  test('4. 有面试训练', async ({ page }) => {
    await ensureInApp(page);
    await ensureE2eInterviewAnswered(page);

    await gotoDashboard(page);
    await waitDashboardReady(page);

    let sessionCount = Number(await statValueLocator(page, '面试训练数').innerText());
    if (sessionCount === 0) {
      await page.reload();
      await waitDashboardReady(page);
      sessionCount = Number(await statValueLocator(page, '面试训练数').innerText());
    }
    expect(sessionCount).toBeGreaterThan(0);

    await expect(page.locator('.summary-panel')).toContainText(/面试训练|进行中|含已完成/);
    await expect(page.locator('.timeline-item').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.locator('.timeline')).toContainText(/创建面试训练|完成面试训练|面试训练/);

    const avgStat = page.locator('.stat-card', { hasText: '平均面试分' });
    if (await avgStat.isVisible().catch(() => false)) {
      await expect(avgStat.locator('.stat-value')).not.toHaveText('');
    }
    await expect(page.locator('.suggestion-card').first()).toBeVisible();
  });

  test('5. 建议链接可跳转', async ({ page }) => {
    await ensureInApp(page);
    await gotoDashboard(page);
    await waitDashboardReady(page);

    const link = page.locator('.suggestion-card .card-link').first();
    await expect(link).toBeVisible();
    await link.click();
    await expect(page).not.toHaveURL(/#\/dashboard$/);
  });
});

test.describe('求职看板 V1 · 手机端 390x844', () => {
  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-mobile', '仅 mobile');
  });

  test('6. 手机端统计/建议/动态可读且无横向滚动', async ({ page }) => {
    await ensureInApp(page);
    await ensureE2eResume(page);
    await ensureE2eJobMatch(page);
    await ensureE2eInterviewAnswered(page);

    await gotoDashboard(page);
    await waitDashboardReady(page);

    await expect(page.locator('.stats-grid')).toBeVisible();
    await expect(page.locator('.summary-panel')).toBeVisible();
    await expect(page.locator('.suggestion-card').first()).toBeVisible();
    await expect(
      page.locator('.timeline-item, .empty-state').first()
    ).toBeVisible();

    await assertNoHorizontalScroll(page);

    const suggestion = page.locator('.suggestion-card').first();
    await expect(suggestion).toBeVisible();
    await assertNoHorizontalScroll(page);

    const timeline = page.locator('.timeline, .empty-state').first();
    await expect(timeline).toBeVisible();
    await assertNoHorizontalScroll(page);
  });
});
