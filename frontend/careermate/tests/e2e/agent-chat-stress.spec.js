// @ts-check
/**
 * Agent 对话台多轮压力：双用户并行，探测第几轮后会卡住。
 * 运行：AGENT_STRESS_MAX_ROUNDS=30 npx playwright test tests/e2e/agent-chat-stress.spec.js --project=local-chrome-desktop
 */
const { test, expect } = require('@playwright/test');
const {
  logEnv,
  e2ePrefix,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  registerViaUi,
  gotoApp,
  waitStable,
  printCreatedAccountsReport,
} = require('./e2e-env');

const MAX_ROUNDS = Number(process.env.AGENT_STRESS_MAX_ROUNDS || 30);
const ROUND_TIMEOUT_MS = Number(process.env.AGENT_STRESS_ROUND_TIMEOUT_MS || 90_000);
/** 并行双开浏览器；设为 0 则两个用户串行跑 */
const PARALLEL_USERS = process.env.AGENT_STRESS_PARALLEL !== '0';
/** 1=只跑用户A（单会话压力）；2=双用户（默认） */
const USER_COUNT = Number(process.env.AGENT_STRESS_USER_COUNT || 2);
/** @type {string} */
const AUTH_MODE = 'jwt';


function createStressAccount(userLabel) {
  const ts = Date.now();
  const rand = Math.random().toString(36).slice(2, 8);
  const prefix = e2ePrefix();
  const account = {
    username: `${prefix}_stress_${userLabel}_${ts}_${rand}`,
    email: `${prefix}_stress_${userLabel}_${ts}_${rand}@careermate.test`,
    password: 'Test123456!',
  };
  console.log('[stress-account]', userLabel, account.username);
  return account;
}

const ROUND_MESSAGES = [
  '帮我看一下我的默认简历',
  '我和最近岗位的差距在哪里',
  '你好，请用一句话回复',
  '看一下我的求职进展',
  '帮我创建一次面试训练',
  '今天天气怎么样',
  '给我一句求职鼓励',
  '帮我分析默认简历',
];

/**
 * @param {import('@playwright/test').Page} page
 * @param {string} text
 */
async function waitAgentSessionReady(page) {
  const sections = page.locator('.panel-section');
  const n = await sections.count();
  for (let i = 0; i < n; i++) {
    const label = await sections.nth(i).locator('.panel-label').innerText().catch(() => '');
    if (label.includes('sessionId')) {
      const value = sections.nth(i).locator('.panel-value');
      await expect(value).not.toHaveText(/创建中/, { timeout: 25_000 });
      const sid = (await value.innerText()).trim();
      expect(sid.length).toBeGreaterThan(3);
      return sid;
    }
  }
  throw new Error('侧栏未找到 sessionId');
}

async function sendAgentMessage(page, text) {
  const input = page.locator('input[placeholder="说说你想做什么..."]');
  const sendBtn = page.getByRole('button', { name: '↑' });
  await input.fill(text);
  await expect(sendBtn).toBeEnabled({ timeout: 20_000 });
  await sendBtn.click();
  await expect(page.locator('.user-bubble', { hasText: text })).toBeVisible({ timeout: 20_000 });
}

/**
 * @param {import('@playwright/test').Page} page
 */
async function readStreamStatus(page) {
  const streamFlagCount = await page.locator('.stream-flag').count();
  const sendEnabled = await page
    .getByRole('button', { name: '↑' })
    .isEnabled()
    .catch(() => false);
  const inputEnabled = await page
    .locator('input[placeholder="说说你想做什么..."]')
    .isEnabled()
    .catch(() => false);
  let panelStatus = '';
  const sections = page.locator('.panel-section');
  const n = await sections.count();
  for (let i = 0; i < n; i++) {
    const label = await sections.nth(i).locator('.panel-label').innerText().catch(() => '');
    if (label.includes('当前状态')) {
      panelStatus = (await sections.nth(i).locator('.panel-value').innerText()).trim();
      break;
    }
  }
  return { streamFlagCount, sendEnabled, inputEnabled, panelStatus };
}

/**
 * @param {import('@playwright/test').Page} page
 * @param {number} round
 */
async function waitAgentRoundComplete(page, round) {
  const agentBubble = page.locator('.agent-bubble').last();
  const deadline = Date.now() + ROUND_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const s = await readStreamStatus(page);
    const streaming =
      s.streamFlagCount > 0 ||
      s.panelStatus.includes('流式生成中') ||
      s.panelStatus.includes('会话创建中');
    const globalErr = await page.locator('.global-error').textContent().catch(() => '');
    if (globalErr && globalErr.trim()) {
      throw new Error(`第 ${round} 轮全局错误: ${globalErr.trim()}`);
    }
    if (!streaming && s.inputEnabled) {
      await expect(agentBubble).not.toContainText('流式输出中', { timeout: 2_000 }).catch(() => {});
      const bubbleText = (await agentBubble.innerText().catch(() => '')).trim();
      if (bubbleText.length > 0) {
        return s;
      }
    }
    await page.waitForTimeout(400);
  }
  const s = await readStreamStatus(page);
  throw new Error(
    `第 ${round} 轮超时（${ROUND_TIMEOUT_MS}ms）：streamFlag=${s.streamFlagCount} input=${s.inputEnabled} status=${s.panelStatus}`
  );
}

/**
 * @param {import('playwright').Browser} browser
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} userLabel
 */
async function runUserStress(browser, request, userLabel) {
  const account = createStressAccount(userLabel);
  const context = await browser.newContext();
  const page = await context.newPage();
  attachDiagnostics(page);

  /** @type {{ userLabel: string; username: string; completedRounds: number; stuck: boolean; stuckRound: number | null; error: string | null }} */
  const result = {
    userLabel,
    username: account.username,
    completedRounds: 0,
    stuck: false,
    stuckRound: null,
    error: null,
  };

  try {
    await registerViaUi(page, account, request);
    await page.getByRole('link', { name: '💬 对话台', exact: true }).click();
    await expect(page.getByText('Agent 对话台')).toBeVisible({ timeout: 20_000 });
    await waitAgentSessionReady(page);

    console.log(
      `[stress:${userLabel}] 开始多轮对话，上限 ${MAX_ROUNDS} 轮，账号 ${account.username}，模式 ${AUTH_MODE}`
    );

    for (let round = 1; round <= MAX_ROUNDS; round++) {
      const msg = `${ROUND_MESSAGES[(round - 1) % ROUND_MESSAGES.length]}（${userLabel}-R${round}）`;
      console.log(`[stress:${userLabel}] >>> 第 ${round}/${MAX_ROUNDS} 轮`);
      await sendAgentMessage(page, msg);
      await waitAgentRoundComplete(page, round);
      result.completedRounds = round;
    }
  } catch (e) {
    result.stuck = true;
    result.stuckRound = result.completedRounds + 1;
    result.error = e instanceof Error ? e.message : String(e);
    const snap = await readStreamStatus(page).catch(() => null);
    console.error(`[stress:${userLabel}] 卡住于第 ${result.stuckRound} 轮: ${result.error}`, snap);
  } finally {
    await context.close();
  }

  return result;
}

function printStressReport(results) {
  console.log('\n========== Agent 对话台多轮压力报告 ==========');
  console.log(`目标轮数: ${MAX_ROUNDS}，单轮超时: ${ROUND_TIMEOUT_MS}ms，前缀: ${e2ePrefix()}`);
  for (const r of results) {
    if (r.stuck) {
      console.log(
        `[${r.userLabel}] 用户 ${r.username}：在第 ${r.stuckRound} 轮卡住（已完成 ${r.completedRounds} 轮）`
      );
      console.log(`  原因: ${r.error}`);
    } else {
      console.log(`[${r.userLabel}] 用户 ${r.username}：完成全部 ${r.completedRounds} 轮，未检测到卡住`);
    }
  }
  const anyStuck = results.some((r) => r.stuck);
  const minCompleted = Math.min(...results.map((r) => r.completedRounds));
  if (!anyStuck) {
    console.log(`结论: 双用户均在 ${MAX_ROUNDS} 轮内正常，未复现卡死（至少通过 ${minCompleted} 轮）。`);
  } else {
    const firstStuck = Math.min(
      ...results.filter((r) => r.stuck).map((r) => r.stuckRound ?? MAX_ROUNDS + 1)
    );
    console.log(`结论: 至少有一用户在第 ${firstStuck} 轮出现卡死迹象。`);
  }
  console.log('==============================================\n');
}

test.describe('Agent 对话台多轮压力（双用户）', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeAll(async ({ request }) => {
    logEnv();
    await assertBackendReady(request);
    await assertUserFlowEnvironment(request);
    console.log(`[stress] 认证模式: ${AUTH_MODE}，双用户并行: ${PARALLEL_USERS}`);
  });

  test.afterAll(() => {
    printCreatedAccountsReport();
  });

  test('双用户并行多轮会话直至上限或卡死', async ({ browser, request }) => {
    test.setTimeout(Math.max(600_000, MAX_ROUNDS * ROUND_TIMEOUT_MS * 2));

    const runA = () => runUserStress(browser, request, '用户A');
    const runB = () => runUserStress(browser, request, '用户B');

    /** @type {Awaited<ReturnType<typeof runUserStress>>[]} */
    let results;
    if (USER_COUNT < 2) {
      results = [await runA()];
    } else if (PARALLEL_USERS) {
      results = await Promise.all([runA(), runB()]);
    } else {
      results = [await runA(), await runB()];
    }

    printStressReport(results);

    const failures = results.filter((r) => r.stuck);
    if (failures.length > 0) {
      const detail = failures
        .map((r) => `${r.userLabel}@${r.username} 第${r.stuckRound}轮: ${r.error}`)
        .join('; ');
      expect(failures, `检测到卡死: ${detail}`).toHaveLength(0);
    }

    for (const r of results) {
      expect(r.completedRounds, `${r.userLabel} 未完成 ${MAX_ROUNDS} 轮`).toBe(MAX_ROUNDS);
    }
  });
});
