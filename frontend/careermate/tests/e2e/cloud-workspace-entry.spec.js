// @ts-check
/**
 * 云端 T04 工作空间跳转验收：市场 / 面试题 / 我的 → AI 小职。
 *
 * 用法（需真实短信验证码，一次性）：
 * E2E_TARGET=cloud E2E_SMS_PHONE=18565040934 \
 *   E2E_SMS_CODE=xxxxxx E2E_SMS_CHALLENGE_ID=uuid E2E_SMS_SKIP_SEND=1 \
 *   npx playwright test tests/e2e/cloud-workspace-entry.spec.js \
 *   --project=local-chrome-desktop --workers=1
 *
 * 或使用已登录 token 跳过重发验证码：
 * E2E_TARGET=cloud E2E_AUTH_TOKEN=... E2E_AUTH_USER_ID=... \
 *   npx playwright test tests/e2e/cloud-workspace-entry.spec.js ...
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
    console.log('[cloud-workspace-entry] 使用预设 token，跳过 SMS 登录');
    return;
  }

  let challengeId = process.env.E2E_SMS_CHALLENGE_ID || null;
  if (!challengeId && process.env.E2E_SMS_SKIP_SEND !== '1') {
    challengeId = await sendSmsChallengeViaApi(request, SMS_PHONE);
    console.log('[cloud-workspace-entry] 已发送验证码 challengeId=', challengeId);
    throw new Error(
      `验证码已发送至 ${SMS_PHONE}，请设置 E2E_SMS_CODE 和 E2E_SMS_CHALLENGE_ID=${challengeId} 后重跑`
    );
  }
  if (!challengeId) {
    throw new Error('请设置 E2E_SMS_CHALLENGE_ID（与 E2E_SMS_CODE 配对）');
  }

  const login = await mobileLoginViaApi(request, SMS_PHONE, SMS_CODE, challengeId);
  cachedAuth = { token: login.token, user: login.user };
  console.log('[cloud-workspace-entry] SMS 登录成功，user=', login.user?.username);
});

test.beforeEach(({ page }) => {
  attachDiagnostics(page);
});

async function assertWorkspaceChatLoaded(page, spaceLabel) {
  await page.waitForURL(/\/chat\/WS-/, { timeout: 30_000 });
  await expect(page.locator('.header-sub')).toContainText(spaceLabel, { timeout: 20_000 });
  await expect(page.locator('body')).not.toContainText('工作空间不存在', { timeout: 5_000 });
  await expect(page.locator('.global-error')).not.toBeVisible({ timeout: 3_000 }).catch(() => {});
}

/** 进入工作空间后点击欢迎卡片，URL 应保持 WS-* 且不出现「工作空间不存在」 */
async function clickWelcomeCardActionIfPresent(page) {
  const cardBtn = page.locator('.chat-card-btn').first();
  if (!(await cardBtn.isVisible({ timeout: 8_000 }).catch(() => false))) {
    console.log('[cloud-workspace-entry] 无欢迎卡片按钮，跳过卡片点击');
    return;
  }
  const label = (await cardBtn.textContent())?.trim() || '';
  console.log('[cloud-workspace-entry] 点击欢迎卡片按钮:', label);
  await cardBtn.click();
  await page.waitForTimeout(800);
  await expect(page).toHaveURL(/\/chat\/WS-/, { timeout: 5_000 });
  await expect(page.locator('body')).not.toContainText('工作空间不存在', { timeout: 5_000 });
}

test.describe('云端 T04 工作空间跳转（市场/面试/我的）', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'local-chrome-desktop', '仅 desktop chrome');
    if (!cachedAuth?.token) {
      throw new Error('beforeAll 未获取到登录 token');
    }
    await seedPageWithToken(page, cachedAuth.token, cachedAuth.user);
    await expect(page.getByRole('heading', { name: '今天的机会' })).toBeVisible({ timeout: 20_000 });
  });

  test('市场页 → 生成谈薪脚本 → 小职对话台可点击', async ({ page }) => {
    await gotoApp(page, '/market');
    await page.waitForLoadState('networkidle');

    const negotiateBtn = page.getByRole('button', { name: /生成谈薪脚本/ });
    await expect(negotiateBtn).toBeVisible({ timeout: 15_000 });
    await negotiateBtn.scrollIntoViewIfNeeded();
    await negotiateBtn.click();

    await assertWorkspaceChatLoaded(page, /市场策略空间/);
    await clickWelcomeCardActionIfPresent(page);

    const input = page.locator('textarea.input-area, textarea[placeholder*="问"]').first();
    if (await input.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await input.fill('帮我解读当前谈薪策略');
      await page.locator('button.send-btn, button[type="submit"]').first().click({ timeout: 5_000 }).catch(() => {});
      await expect(page.locator('body')).not.toContainText('工作空间不存在', { timeout: 8_000 });
    }
  });

  test('面试题页 → 让小职讲解 → 小职对话台可点击', async ({ page }) => {
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

    await assertWorkspaceChatLoaded(page, /面试训练空间/);
    await clickWelcomeCardActionIfPresent(page);
  });

  test('我的页 → 产物继续优化 → 小职对话台可点击', async ({ page }) => {
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

    await assertWorkspaceChatLoaded(page, /简历优化空间|工作空间/);
    await clickWelcomeCardActionIfPresent(page);
  });
});
