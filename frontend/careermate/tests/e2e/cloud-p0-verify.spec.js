// @ts-check
/**
 * 云端 P0（T01-T04）验收：手机号登录后验证 5 tab 壳、Workspace、产物、四 tab 联动小职。
 *
 * E2E_TARGET=cloud E2E_SMS_PHONE=18565040934 E2E_SMS_CODE=xxxxxx \
 *   E2E_SMS_CHALLENGE_ID=uuid E2E_SMS_SKIP_SEND=1 \
 *   npx playwright test tests/e2e/cloud-p0-verify.spec.js \
 *   --project=local-chrome-mobile --workers=1
 */
const { test, expect } = require('@playwright/test');
const {
  attachDiagnostics,
  assertBackendReady,
  assertUserFlowEnvironment,
  gotoApp,
  logEnv,
  mobileLoginViaApi,
  sendSmsChallengeViaApi,
  seedPageWithToken,
  MOCK_SMS_CODE,
} = require('./e2e-env');

const SMS_PHONE = process.env.E2E_SMS_PHONE || '';
const SMS_CODE = process.env.E2E_SMS_CODE || MOCK_SMS_CODE;

/** @type {{ token: string; user: object } | null} */
let cachedAuth = null;

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ request }) => {
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);
  if (!SMS_PHONE) {
    throw new Error('请设置 E2E_SMS_PHONE 环境变量');
  }

  const presetToken = process.env.E2E_AUTH_TOKEN;
  if (presetToken) {
    cachedAuth = {
      token: presetToken,
      user: {
        userId: Number(process.env.E2E_AUTH_USER_ID || 0),
        username: process.env.E2E_AUTH_USERNAME || 'cloud_user',
        role: 'USER',
      },
    };
    console.log('[cloud-p0] 使用预设 token，跳过 SMS 登录');
    return;
  }

  let challengeId = process.env.E2E_SMS_CHALLENGE_ID || null;
  if (!challengeId && process.env.E2E_SMS_SKIP_SEND !== '1') {
    challengeId = await sendSmsChallengeViaApi(request, SMS_PHONE);
    console.log('[cloud-p0] 已发送验证码 challengeId=', challengeId);
    throw new Error('验证码已发送，请设置 E2E_SMS_CODE 和 E2E_SMS_CHALLENGE_ID 后重跑');
  }
  if (!challengeId) {
    throw new Error('请设置 E2E_SMS_CHALLENGE_ID（与 E2E_SMS_CODE 配对）');
  }

  const login = await mobileLoginViaApi(request, SMS_PHONE, SMS_CODE, challengeId);
  cachedAuth = { token: login.token, user: login.user };
  console.log('[cloud-p0] SMS 登录成功，user=', login.user?.username);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

test.describe('云端 P0 验收 T01-T04', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    test.skip(
      !['local-chrome-mobile', 'local-chrome-desktop'].includes(testInfo.project.name),
      '仅 mobile/desktop chrome'
    );
    if (!cachedAuth?.token) {
      throw new Error('beforeAll 未获取到登录 token');
    }
    await seedPageWithToken(page, cachedAuth.token, cachedAuth.user);
    await expect(page.getByRole('heading', { name: '今天的机会' })).toBeVisible({ timeout: 20_000 });
  });

  test('T01 移动端 5 tab 产品壳', async ({ page }) => {
    const nav = page.getByRole('navigation', { name: '主导航' });
    await expect(nav).toBeVisible();
    await expect(nav.getByRole('button', { name: /机会/ })).toBeVisible();
    await expect(nav.getByRole('button', { name: /面试题/ })).toBeVisible();
    await expect(nav.getByRole('button', { name: /AI 小职/ })).toBeVisible();
    await expect(nav.getByRole('button', { name: /市场/ })).toBeVisible();
    await expect(nav.getByRole('button', { name: /我的/ })).toBeVisible();

    await nav.getByRole('button', { name: /AI 小职/ }).click();
    await page.waitForURL(/\/chat/, { timeout: 15_000 });
    await expect(nav).not.toBeVisible();
  });

  test('T04 市场页生成谈薪脚本进入小职', async ({ page }) => {
    await gotoApp(page, '/market');
    await page.waitForLoadState('networkidle');

    const negotiateBtn = page.getByRole('button', { name: /生成谈薪脚本/ });
    await expect(negotiateBtn).toBeVisible({ timeout: 15_000 });
    await negotiateBtn.scrollIntoViewIfNeeded();
    await negotiateBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/市场策略空间/, { timeout: 20_000 });
    await expect(page.locator('.context-chips-bar')).toContainText(/市场行情|广州|Java/, {
      timeout: 20_000,
    });
  });

  test('T04 机会页定制简历进入 JD 准备空间', async ({ page }) => {
    await gotoApp(page, '/opportunity');
    await page.waitForLoadState('networkidle');

    const emptyState = page.locator('.empty-state');
    if (await emptyState.isVisible({ timeout: 12_000 }).catch(() => false)) {
      test.skip(true, '云端暂无 JD 数据');
    }

    const resumeBtn = page.getByRole('button', { name: '定制简历' }).first();
    await expect(resumeBtn).toBeVisible({ timeout: 30_000 });
    await resumeBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/JD 准备空间/, { timeout: 20_000 });
  });

  test('T04 面试题页让小职讲解进入面试训练空间', async ({ page }) => {
    await gotoApp(page, '/interview');
    await page.waitForLoadState('networkidle');

    const redisTag = page.getByRole('button', { name: 'Redis', exact: true });
    await expect(redisTag).toBeVisible({ timeout: 10_000 });
    await redisTag.click();

    const emptyTip = page.locator('.kb-section .empty-tip');
    const questionCard = page.locator('.kb-question-card').first();
    await expect(questionCard.or(emptyTip)).toBeVisible({ timeout: 60_000 });
    if (await emptyTip.isVisible().catch(() => false)) {
      test.skip(true, '面试知识库暂无 Redis 题目');
    }

    await page.locator('.question-header').first().click();
    const explainBtn = page.getByRole('button', { name: '让小职讲解' }).first();
    await expect(explainBtn).toBeVisible({ timeout: 10_000 });
    await explainBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toContainText(/面试训练空间/, { timeout: 20_000 });
  });

  test('T03 我的页最近产物可继续优化', async ({ page }) => {
    await gotoApp(page, '/mine');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.profile-page, .mine-page, .profile-section').first()).toBeVisible({
      timeout: 15_000,
    });

    const artifactSection = page.locator('.artifacts-section');
    if (!(await artifactSection.isVisible({ timeout: 8_000 }).catch(() => false))) {
      test.skip(true, '当前账号暂无产物记录');
    }

    const actionBtn = artifactSection.locator('.artifact-action').first();
    await expect(actionBtn).toBeVisible();
    await actionBtn.click();

    await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
    await expect(page.locator('.header-sub')).toBeVisible({ timeout: 20_000 });
  });
});
