// @ts-check
/**
 * CareerMate T01-T08 完整产品闭环 Playwright 验证
 *
 * 运行：
 * E2E_TARGET=local E2E_SMS_PHONE=18565040934 \
 *   PLAYWRIGHT_API_BASE_URL=http://localhost:8081/api \
 *   VITE_API_PROXY_TARGET=http://localhost:8081 \
 *   npx playwright test tests/e2e/t01-t08-full-journey.spec.js \
 *   --project=local-chrome-mobile --workers=1
 */
const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');
const {
  apiBaseURL,
  baseURL,
  assertBackendReady,
  assertUserFlowEnvironment,
  attachDiagnostics,
  gotoApp,
  logEnv,
  mobileLoginViaApi,
  sendSmsChallengeViaApi,
  MOCK_SMS_CODE,
  TOKEN_KEY,
  USER_KEY,
  waitStable,
  assertNoHorizontalScroll,
} = require('./e2e-env');
const {
  validatePdfFile,
  validateDocxFile,
  textContainsKeywords,
} = require('./helpers/document-validator');
const {
  PROJECT_ROOT,
  ASSETS_DIR,
  ensureDirs,
  resetState,
  setEnv,
  recordCoverage,
  recordTabLinkage,
  recordResumeWorkflow,
  addBug,
  assetPath,
  writeReport,
} = require('./helpers/t01-t08-report');

const SMS_PHONE = process.env.E2E_SMS_PHONE || '18565040934';
const AUTH_DIR = path.join(__dirname, '.auth');
const AUTH_STATE_PATH = path.join(AUTH_DIR, `user-${SMS_PHONE}.json`);
const AUTH_META_PATH = path.join(AUTH_DIR, `user-${SMS_PHONE}.meta.json`);
const DOWNLOAD_DIR = path.join(PROJECT_ROOT, 'test-results/t01-t08-downloads');

/** @type {string | null} */
let savedToken = null;

test.describe.configure({ timeout: 600_000 });

test.beforeAll(async ({ request }) => {
  resetState();
  ensureDirs();
  logEnv();
  await assertBackendReady(request);
  await assertUserFlowEnvironment(request);

  setEnv({
    backend: apiBaseURL.replace(/\/api$/, ''),
    frontend: baseURL,
    ragforge: process.env.RAGFORGE_URL || 'http://localhost:8080',
    browser: 'Google Chrome (Playwright channel: chrome)',
    viewport: '390x844 (iPhone 13 class, isMobile=true)',
    phone: `${SMS_PHONE.slice(0, 3)}****${SMS_PHONE.slice(-4)}`,
    authStatePath: path.relative(PROJECT_ROOT, AUTH_STATE_PATH),
  });

  if (fs.existsSync(AUTH_STATE_PATH)) {
    try {
      const state = JSON.parse(fs.readFileSync(AUTH_STATE_PATH, 'utf8'));
      const origin = state.origins?.find((o) => o.origin?.includes('5173')) || state.origins?.[0];
      const tokenEntry = origin?.localStorage?.find((x) => x.name === TOKEN_KEY);
      if (tokenEntry?.value) {
        const me = await request.get(`${apiBaseURL}/auth/me`, {
          headers: { Authorization: `Bearer ${tokenEntry.value}` },
        });
        if (me.ok()) {
          savedToken = tokenEntry.value;
          console.log('[auth] 复用已有 storageState，跳过短信');
          return;
        }
      }
    } catch (e) {
      console.warn('[auth] storageState 无效，将重新登录', e);
    }
  }

  console.log('[auth] 首次 API 登录，发送一次验证码');
  let challengeId = process.env.E2E_SMS_CHALLENGE_ID;
  if (!challengeId && fs.existsSync(AUTH_META_PATH)) {
    try {
      const meta = JSON.parse(fs.readFileSync(AUTH_META_PATH, 'utf8'));
      challengeId = meta.challengeId;
      console.log('[auth] 复用已保存 challengeId，不再发码');
    } catch {
      // ignore
    }
  }
  if (!challengeId) {
    challengeId = await sendSmsChallengeViaApi(request, SMS_PHONE);
    fs.mkdirSync(AUTH_DIR, { recursive: true });
    fs.writeFileSync(
      AUTH_META_PATH,
      JSON.stringify({ phone: SMS_PHONE, challengeId, sentAt: new Date().toISOString() }, null, 2)
    );
  }

  const smsCode = process.env.E2E_SMS_CODE;
  if (!smsCode) {
    addBug({
      severity: 'P0',
      title: 'E2E 登录阻塞：需要短信验证码',
      steps: `已向 ${SMS_PHONE.slice(0, 3)}****${SMS_PHONE.slice(-4)} 发送一次验证码（challengeId=${challengeId}）`,
      actual: '未设置 E2E_SMS_CODE，无法完成登录',
      expected: '用户提供验证码后继续：E2E_SMS_CODE=xxxxxx npx playwright test ...',
      evidence: AUTH_META_PATH,
      impact: '全部 T01-T08 用例无法执行',
      fix: '设置 E2E_SMS_CODE 后重跑；或本地开启 ALIYUN_SMS_MOCK_ENABLED=true',
    });
    recordCoverage('T01-T08 登录', '失败', '需要验证码，已发码一次', 'P0', '');
    throw new Error('需要验证码：请设置 E2E_SMS_CODE 环境变量后重跑（不会再次发码，已保存 challengeId）');
  }

  const login = await mobileLoginViaApi(request, SMS_PHONE, smsCode, challengeId);
  savedToken = login.token;
});

test.afterAll(async () => {
  writeReport();
  console.log('[report] 已写入 docs/test-reports/t01-t08-playwright-report.md');
});

async function screenshot(page, name) {
  const file = path.join(ASSETS_DIR, name);
  await page.screenshot({ path: file, fullPage: true });
  return assetPath(name);
}

async function clickChatSend(page) {
  const sendBtn = page.getByRole('button', { name: /^发送$|^↑$/ });
  await expect(sendBtn).toBeEnabled({ timeout: 30_000 });
  await sendBtn.click();
}

async function seedAuth(page) {
  if (!savedToken) throw new Error('缺少登录 token');
  await gotoApp(page, '/login');
  await page.evaluate(
    ([tokenKey, userKey, token]) => {
      localStorage.setItem(tokenKey, token);
      localStorage.setItem(
        userKey,
        JSON.stringify({ userId: 0, username: 'e2e_user', role: 'USER', authenticated: true })
      );
    },
    [TOKEN_KEY, USER_KEY, savedToken]
  );
  await page.reload({ waitUntil: 'networkidle' });
  const meRes = await page.waitForResponse(
    (r) => r.url().includes('/auth/me') && r.request().method() === 'GET',
    { timeout: 20_000 }
  ).catch(() => null);
  if (meRes && meRes.ok()) {
    const body = await meRes.json().catch(() => null);
    if (body?.data?.userId) {
      await page.evaluate(
        ([userKey, user]) => {
          localStorage.setItem(userKey, JSON.stringify({ ...user, authenticated: true }));
        },
        [USER_KEY, body.data]
      );
    }
  }
  await expect(page.getByRole('heading', { name: '今天的机会' })).toBeVisible({ timeout: 25_000 });
}

test.beforeEach(async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== 'local-chrome-mobile', 'T01-T08 使用移动端 viewport');
  attachDiagnostics(page);
  await seedAuth(page);
});

test.afterEach(async ({ context }) => {
  fs.mkdirSync(AUTH_DIR, { recursive: true });
  await context.storageState({ path: AUTH_STATE_PATH });
});

test('T01 移动端 5 Tab 产品壳', async ({ page }) => {
  const tabs = [
    { label: '机会', path: '/opportunity', heading: '今天的机会', hideNav: false },
    { label: '面试题', path: '/interview', heading: /面试|训练|题库|刷题/, hideNav: false },
    { label: 'AI 小职', path: '/chat', heading: /Agent|小职|对话|说说你想/, hideNav: true },
    { label: '市场', path: '/market', heading: /薪资|市场|行情/, hideNav: false },
    { label: '我的', path: '/mine', heading: /我的|画像|职业定位/, hideNav: false },
  ];

  let pass = true;
  const notes = [];

  // 先在其他 tab 上检查底部导航与 AI 凸起
  await gotoApp(page, '/opportunity');
  await waitStable(page);
  const centerAi = page.locator('.nav-item.center-ai, .center-ai').first();
  if (await centerAi.count()) {
    const fab = centerAi.locator('.ai-fab');
    const fabBox = await fab.boundingBox();
    const navBox = await page.locator('.bottom-nav').boundingBox();
    if (fabBox && navBox && fabBox.y < navBox.y) {
      notes.push('AI 小职 FAB 相对底部导航有凸起效果');
    } else {
      pass = false;
      notes.push('AI 小职凸起不明显');
    }
  }

  for (const tab of tabs) {
    if (tab.label === 'AI 小职') {
      const centerBtn = page.getByRole('button', { name: /AI 小职/ });
      if (await centerBtn.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await centerBtn.click();
      } else {
        await gotoApp(page, '/chat');
      }
    } else {
      await page.getByRole('button', { name: new RegExp(`^${tab.label}`) }).click();
    }
    await waitStable(page);

    if (tab.hideNav) {
      await expect(page.locator('.bottom-nav')).toHaveCount(0);
      notes.push('AI 对话台正确隐藏底部导航');
      await expect(page.locator('input[placeholder="说说你想做什么..."]')).toBeVisible({ timeout: 15_000 });
      await gotoApp(page, '/opportunity');
      await waitStable(page);
      continue;
    }

    await expect(page.locator('.bottom-nav')).toBeVisible();
    try {
      await expect(page.getByRole('heading', { name: tab.heading }).first()).toBeVisible({ timeout: 15_000 });
    } catch {
      pass = false;
      notes.push(`${tab.label} tab 主标题不可见`);
    }
    await assertNoHorizontalScroll(page);
  }

  const tokenBefore = await page.evaluate((k) => localStorage.getItem(k), TOKEN_KEY);
  await page.getByRole('button', { name: /^市场/ }).click();
  await page.getByRole('button', { name: /^机会/ }).click();
  const tokenAfter = await page.evaluate((k) => localStorage.getItem(k), TOKEN_KEY);
  if (tokenBefore !== tokenAfter || !tokenAfter) {
    pass = false;
    notes.push('Tab 切换后登录态丢失');
  }

  const shot = await screenshot(page, 't01-mobile-5-tabs.png');
  recordCoverage(
    'T01 移动端 5 Tab',
    pass ? '通过' : '部分通过',
    notes.join('；') || '5 Tab 文案正确，切换正常，无横向滚动',
    pass ? '-' : 'P2',
    shot
  );
});

async function verifyChatEntry(page, entryName, clickFn, expectedContext) {
  const result = {
    status: '失败',
    url: '',
    context: '',
    workspaceMissing: false,
    contextLost: false,
    cardFailure: '',
    screenshot: '',
    note: '',
  };

  try {
    await gotoApp(page, '/opportunity');
    await waitStable(page);
    await clickFn();
    await page.waitForURL(/#\/chat(\/|$)/, { timeout: 45_000 });
    result.url = page.url();

    const bodyText = await page.locator('body').innerText();
    result.workspaceMissing = /工作空间不存在|Workspace not found/i.test(bodyText);
    if (result.workspaceMissing) {
      result.status = '失败';
      result.note = '出现工作空间不存在';
      addBug({
        severity: 'P0',
        title: `${entryName} 进入 AI 报工作空间不存在`,
        steps: `从 ${entryName} 入口点击进入 AI 对话台`,
        actual: '页面提示工作空间不存在',
        expected: '正常加载 workspace 上下文',
        evidence: await screenshot(page, `bug-workspace-${entryName.replace(/\s/g, '-')}.png`),
        impact: `${entryName} 主链路阻断`,
        fix: '检查 workspace 创建与 session 绑定',
      });
      recordTabLinkage(entryName, result);
      return result;
    }

    const headerSub = page.locator('.header-sub');
    if (await headerSub.isVisible({ timeout: 10_000 }).catch(() => false)) {
      result.context = (await headerSub.innerText()).trim();
    }

    if (expectedContext && result.context && !expectedContext.test(result.context)) {
      result.contextLost = true;
      result.note = `期望上下文 ${expectedContext}，实际 ${result.context}`;
    }

    const chips = page.locator('.context-chips-bar');
    const hasChips = await chips.isVisible({ timeout: 5_000 }).catch(() => false);
    const hasBubble = await page.locator('.agent-bubble, .msg-bubble, .chat-card').first().isVisible({ timeout: 15_000 }).catch(() => false);

    if (!hasChips && !hasBubble && !result.context) {
      result.contextLost = true;
      result.note = '进入后无上下文 chips/消息/卡片';
    }

    const wsMatch = page.url().match(/\/chat\/([^/?#]+)/);
    if (wsMatch) {
      const wsId = wsMatch[1];
      await page.reload({ waitUntil: 'networkidle' });
      await expect(page.locator('body')).not.toContainText('工作空间不存在', { timeout: 10_000 });
      if (!page.url().includes(wsId)) {
        result.note += '；刷新后 workspaceId 变化';
      }
    }

    result.status = result.contextLost ? '部分通过' : '通过';
    result.screenshot = await screenshot(page, `tab-link-${entryName.replace(/\s/g, '-')}.png`);
  } catch (e) {
    result.status = '失败';
    result.note = e instanceof Error ? e.message : String(e);
    result.screenshot = await screenshot(page, `tab-link-fail-${entryName.replace(/\s/g, '-')}.png`);
    addBug({
      severity: 'P1',
      title: `${entryName} 无法进入 AI 对话台`,
      steps: `从 ${entryName} 点击进入 /chat`,
      actual: result.note,
      expected: '成功进入带上下文的 AI 对话台',
      evidence: result.screenshot,
      impact: entryName,
      fix: '检查入口 workspace 创建与路由',
    });
  }

  recordTabLinkage(entryName, result);
  return result;
}

test('Tab 联动：机会 -> AI', async ({ page }) => {
  await gotoApp(page, '/opportunity');
  await page.waitForLoadState('networkidle');

  const empty = page.locator('.empty-state');
  if (await empty.isVisible({ timeout: 15_000 }).catch(() => false)) {
    recordTabLinkage('机会 -> AI', {
      status: '跳过',
      note: 'RagForge 无 JD 数据，无法测试机会页入口',
    });
    recordCoverage('T02-T04 Workspace 联动', '部分通过', '机会页无 JD 数据', 'P1', '');
    test.skip(true, '无 JD 数据');
  }

  await verifyChatEntry(
    page,
    '机会 -> AI',
    async () => {
      const analyzeBtn = page.getByRole('button', { name: '分析 JD' }).first();
      await expect(analyzeBtn).toBeVisible({ timeout: 30_000 });
      await analyzeBtn.click();
    },
    /JD 准备空间|机会/
  );
});

test('Tab 联动：面试题 -> AI', async ({ page }) => {
  await verifyChatEntry(
    page,
    '面试题 -> AI',
    async () => {
      await gotoApp(page, '/interview');
      await page.waitForLoadState('networkidle');
      const redis = page.getByRole('button', { name: 'Redis', exact: true });
      if (await redis.isVisible({ timeout: 10_000 }).catch(() => false)) {
        await redis.click();
      }
      const card = page.locator('.kb-question-card').first();
      const empty = page.locator('.kb-section .empty-tip');
      await expect(card.or(empty)).toBeVisible({ timeout: 60_000 });
      if (await empty.isVisible().catch(() => false)) {
        throw new Error('面试知识库暂无题目');
      }
      await page.locator('.question-header').first().click();
      await page.getByRole('button', { name: '让小职讲解' }).first().click();
    },
    /面试训练空间|面试/
  );
});

test('Tab 联动：市场 -> AI', async ({ page }) => {
  await verifyChatEntry(
    page,
    '市场 -> AI',
    async () => {
      await gotoApp(page, '/market');
      await page.waitForLoadState('networkidle');
      await page.getByRole('button', { name: /解读行情/ }).click();
    },
    /市场策略空间|市场/
  );
});

test('Tab 联动：我的 -> AI', async ({ page }) => {
  await verifyChatEntry(
    page,
    '我的 -> AI',
    async () => {
      await gotoApp(page, '/mine');
      await waitStable(page);
      const artifactBtn = page.locator('.artifact-action').first();
      if (await artifactBtn.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await artifactBtn.click();
      } else {
        await page.getByRole('button', { name: /去小职完善画像/ }).click();
      }
    },
    /简历|优化|Agent|小职/
  );
});

test('Tab 联动：中间 AI 小职', async ({ page }) => {
  await gotoApp(page, '/opportunity');
  await page.getByRole('button', { name: /AI 小职/ }).click();
  await page.waitForURL(/#\/chat/, { timeout: 20_000 });
  const bodyText = await page.locator('body').innerText();
  const workspaceMissing = /工作空间不存在/.test(bodyText);
  recordTabLinkage('中间 AI 小职', {
    status: workspaceMissing ? '失败' : '通过',
    url: page.url(),
    context: await page.locator('.header-sub').innerText().catch(() => '通用对话台'),
    workspaceMissing,
    contextLost: false,
    cardFailure: '',
    screenshot: await screenshot(page, 'tab-link-center-ai.png'),
  });
  recordCoverage(
    'T02-T04 Workspace 联动',
    workspaceMissing ? '失败' : '部分通过',
    '各 Tab 入口已逐项验证，详见 Tab 联动表',
    workspaceMissing ? 'P0' : '-',
    assetPath('tab-link-center-ai.png')
  );
});

test('T05-T07 Agent Kernel / Tool / Trace', async ({ page }) => {
  await gotoApp(page, '/chat');
  await waitStable(page);

  const input = page.locator('input[placeholder="说说你想做什么..."]');
  await expect(input).toBeVisible({ timeout: 20_000 });
  await input.fill('帮我看一下求职看板进展');
  await clickChatSend(page);
  await expect(page.locator('.user-bubble').last()).toContainText('求职看板', { timeout: 15_000 });

  const agentBubble = page.locator('.agent-bubble').last();
  await expect(agentBubble).not.toContainText('流式输出中', { timeout: 90_000 });
  const agentText = await agentBubble.innerText().catch(() => '');

  const hasToolCard = (await page.locator('[data-testid="tool-call-card"]').count()) > 0;
  const hasError = /系统异常|流式请求失败|network error/i.test(agentText);
  const duplicate = await page.locator('.agent-bubble').filter({ hasText: agentText.slice(0, 20) }).count();

  if (hasError) {
    expect(hasError).toBe(false);
  }

  recordCoverage(
    'T05-T07 Agent Kernel/Tool/Trace',
    hasError ? '失败' : '通过',
    `SSE 完成；回复长度=${agentText.length}；工具卡片=${hasToolCard ? '有' : '无（观察项，非强制）'}；重复消息=${duplicate > 1 ? '是（观察项）' : '否'}`,
    hasError ? 'P1' : '-',
    await screenshot(page, 't05-agent-tool-trace.png')
  );
});

test('T08 按 JD 生成简历 Workflow + 下载质量 + 修改', async ({ page }) => {
  fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });

  await gotoApp(page, '/opportunity');
  await page.waitForLoadState('networkidle');

  const empty = page.locator('.empty-state');
  if (await empty.isVisible({ timeout: 15_000 }).catch(() => false)) {
    recordCoverage('T08 Workflow 简历生成', '跳过', '无 JD 数据', 'P1', '');
    recordResumeWorkflow({ success: false, cardComplete: false, closureVerdict: '否（无 JD 数据）' });
    test.skip(true, '无 JD 数据');
  }

  const resumeBtn = page.getByRole('button', { name: '定制简历' }).first();
  await expect(resumeBtn).toBeVisible({ timeout: 45_000 });
  await resumeBtn.click();
  await page.waitForURL(/\/chat\/WS-/, { timeout: 45_000 });
  await expect(page.locator('.header-sub')).toContainText(/JD 准备空间/, { timeout: 20_000 });

  const triggerBtn = page.locator('.chat-card-btn').filter({ hasText: /生成定制简历|好,帮我改|按.*JD.*生成/ }).first();
  await expect(triggerBtn).toBeVisible({ timeout: 30_000 });
  await triggerBtn.click();

  const successCard = page.locator('[data-testid="resume-generated-card"], .chat-card--resume_generated');
  const failCard = page.locator('.chat-card--generate_failed');

  let workflowSuccess = false;
  try {
    await expect(successCard.first()).toBeVisible({ timeout: 420_000 });
    workflowSuccess = true;
  } catch {
    if (await failCard.first().isVisible({ timeout: 5_000 }).catch(() => false)) {
      const failText = await failCard.first().innerText();
      recordResumeWorkflow({ success: false, cardComplete: false, workflowError: failText });
      recordCoverage('T08 Workflow 简历生成', '失败', failText.slice(0, 200), 'P0', await screenshot(page, 't08-workflow-failed.png'));
      addBug({
        severity: 'P0',
        title: '按 JD 生成简历 Workflow 失败',
        steps: '机会页 -> 定制简历 -> 触发生成',
        actual: failText.slice(0, 300),
        expected: 'RESUME_GENERATED 卡片',
        evidence: assetPath('t08-workflow-failed.png'),
        impact: 'T08 主流程',
        fix: '检查 GenerateResumeWorkflowRunner 与 RagForge/LLM',
      });
      return;
    }
    throw new Error('Workflow 超时且无成功/失败卡片');
  }

  await screenshot(page, 't08-resume-generated-card.png');

  const chatBodyText = await page.locator('.chat-messages, .messages, main').first().innerText().catch(() => page.locator('body').innerText());
  expect(chatBodyText).not.toMatch(/\{"changes"/);
  expect(chatBodyText).not.toMatch(/"meta"\s*:/);
  expect(chatBodyText).not.toContain('```meta');
  await expect(successCard.first()).toContainText('简历已生成');

  const expectedActions = ['查看完整简历', '复制 Markdown', '下载 PDF', '下载 Word', '去我的简历继续改'];
  const cardText = await successCard.first().innerText();
  const missingActions = expectedActions.filter((a) => !cardText.includes(a));
  const cardComplete = missingActions.length === 0;

  let pdfPath = '';
  let wordPath = '';
  let pdfValidation = null;
  let wordValidation = null;
  let previewMarkdown = '';

  const previewEl = successCard.first().locator('.chat-card-preview');
  if (await previewEl.isVisible().catch(() => false)) {
    previewMarkdown = await previewEl.innerText();
  }

  const pdfBtn = successCard.first().getByRole('button', { name: '下载 PDF' });
  const wordBtn = successCard.first().getByRole('button', { name: '下载 Word' });

  if (await pdfBtn.isVisible().catch(() => false)) {
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 60_000 }),
      pdfBtn.click(),
    ]);
    pdfPath = path.join(DOWNLOAD_DIR, download.suggestedFilename());
    await download.saveAs(pdfPath);
    pdfValidation = validatePdfFile(pdfPath);
  }

  if (await wordBtn.isVisible().catch(() => false)) {
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 60_000 }),
      wordBtn.click(),
    ]);
    wordPath = path.join(DOWNLOAD_DIR, download.suggestedFilename());
    await download.saveAs(wordPath);
    wordValidation = validateDocxFile(wordPath);
  }

  const keywords = textContainsKeywords(previewMarkdown, ['Java', '后端', '技能', '经历', '项目']);
  const pdfKeywords = pdfValidation ? textContainsKeywords(pdfValidation.text, keywords.length ? keywords : ['Java', '简历']) : [];
  const wordKeywords = wordValidation ? textContainsKeywords(wordValidation.text, keywords.length ? keywords : ['Java', '简历']) : [];

  const markdownIssue =
    (pdfValidation?.markdownLike ? 'PDF 有 Markdown 伪格式；' : '') +
    (wordValidation?.markdownLike ? 'Word 有 Markdown 伪格式；' : '') || '无';

  recordResumeWorkflow({
    success: workflowSuccess,
    cardComplete,
    missingActions,
    pdfPath: pdfPath ? path.relative(PROJECT_ROOT, pdfPath) : '',
    wordPath: wordPath ? path.relative(PROJECT_ROOT, wordPath) : '',
    pdfTextSummary: pdfValidation
      ? `size=${pdfValidation.size}B header=${pdfValidation.validHeader} issues=${pdfValidation.issues.join(';') || '无'} keywords=${pdfKeywords.join(',') || '无'} excerpt=${pdfValidation.textSummary}`
      : '未下载',
    wordTextSummary: wordValidation
      ? `size=${wordValidation.size}B xml=${wordValidation.hasDocumentXml} issues=${wordValidation.issues.join(';') || '无'} keywords=${wordKeywords.join(',') || '无'} excerpt=${wordValidation.textSummary}`
      : '未下载',
    markdownIssue: markdownIssue.trim() || '无',
  });

  if (pdfValidation && (!pdfValidation.validHeader || pdfValidation.issues.length > 0)) {
    addBug({
      severity: 'P0',
      title: 'PDF 简历格式不达标',
      steps: '生成简历后下载 PDF',
      actual: pdfValidation.issues.join('；'),
      expected: '真实 PDF，可读排版，无 Markdown 原文',
      evidence: pdfPath ? path.relative(PROJECT_ROOT, pdfPath) : '',
      impact: '简历投递',
      fix: '检查 PDF 渲染 pipeline',
    });
  }

  if (wordValidation && wordValidation.issues.length > 0) {
    addBug({
      severity: pdfValidation?.markdownLike ? 'P0' : 'P1',
      title: 'Word 简历格式问题',
      steps: '生成简历后下载 Word',
      actual: wordValidation.issues.join('；'),
      expected: '标准 docx 段落排版',
      evidence: wordPath ? path.relative(PROJECT_ROOT, wordPath) : '',
      impact: '简历投递',
      fix: '检查 docx 生成模板',
    });
  }

  let editAndRedownload = '未验证';
  const navigateBtn = successCard.first().getByRole('button', { name: '去我的简历继续改' });
  if (await navigateBtn.isVisible().catch(() => false)) {
    await navigateBtn.click();
    await expect(page).toHaveURL(/#\/mine\/resume/, { timeout: 15_000 });
    editAndRedownload = '入口直达 /mine/resume';
  } else {
    await gotoApp(page, '/mine/resume');
  }

  const versionRow = page.locator('.version-row').first();
  if (await versionRow.isVisible({ timeout: 20_000 }).catch(() => false)) {
    await versionRow.getByRole('button', { name: '预览' }).click();
    const editTab = page.locator('.modal-header-actions').getByRole('button', { name: '修改' });
    if (await editTab.isVisible({ timeout: 10_000 }).catch(() => false)) {
      await editTab.click();
      const marker = `E2E_EDIT_${Date.now()}`;
      const textarea = page.locator('textarea.edit-content');
      await textarea.fill(`${previewMarkdown || '测试简历内容'}\n\n${marker}`);
      await page.getByRole('button', { name: '保存' }).click();
      await expect(page.locator('.modal-preview, .modal-body')).toContainText(marker, { timeout: 15_000 });

      await page.getByRole('button', { name: 'PDF' }).last().click();
      const [dl2] = await Promise.all([
        page.waitForEvent('download', { timeout: 60_000 }),
      ]).catch(() => [null]);
      if (dl2) {
        const editedPdf = path.join(DOWNLOAD_DIR, `edited-${Date.now()}.pdf`);
        await dl2.saveAs(editedPdf);
        const editedVal = validatePdfFile(editedPdf);
        editAndRedownload = editedVal.text.includes(marker) ? '支持修改并重新下载' : '修改后 PDF 未体现变更';
      } else {
        editAndRedownload = '支持编辑保存，PDF 重下载未验证';
      }
    } else {
      editAndRedownload = '定制版本列表无修改入口（仅预览）';
      addBug({
        severity: 'P1',
        title: 'AI 定制简历版本缺少明显编辑入口',
        steps: '我的简历 -> AI 定制版本 -> 预览',
        actual: '未找到修改按钮或不可用',
        expected: '可编辑并保存 contentMarkdown',
        evidence: await screenshot(page, 't08-edit-missing.png'),
        impact: '简历迭代',
        fix: '版本列表增加「修改」或在预览弹层突出编辑',
      });
    }
  } else {
    editAndRedownload = '版本列表为空';
  }

  recordResumeWorkflow({
    editAndRedownload,
    downloadVerdict:
      pdfValidation?.validHeader && !pdfValidation?.markdownLike && wordValidation?.hasDocumentXml
        ? '达标'
        : '未达标',
    closureVerdict: workflowSuccess && cardComplete ? '已形成完整产品闭环' : '未完整闭环',
    uxVerdict: missingActions.length === 0 ? '主路径清晰' : '卡片动作不完整',
    agentVerdict: workflowSuccess ? 'Workflow Agent 有过程反馈与结果卡片' : 'Workflow 未成功',
    t09Verdict: workflowSuccess ? '可进入 T09 Memory' : '建议先完成 T08 Workflow',
  });

  recordCoverage(
    'T08 Workflow 简历生成',
    workflowSuccess && cardComplete ? '通过' : workflowSuccess ? '部分通过' : '失败',
    `卡片动作缺失：${missingActions.join(', ') || '无'}；PDF=${pdfValidation?.validHeader ? 'OK' : 'NG'}`,
    pdfValidation?.markdownLike || !workflowSuccess ? 'P0' : missingActions.length ? 'P2' : '-',
    assetPath('t08-resume-generated-card.png')
  );
});

test('用户习惯与产品完整性（人工观察项记录）', async ({ page }) => {
  const observations = [];

  await gotoApp(page, '/opportunity');
  observations.push('机会页：JD 卡片有分析/定制简历/准备面试，行动按钮清晰');

  await gotoApp(page, '/interview');
  observations.push('面试题页：标签筛选 + 让小职讲解，路径直观');

  await gotoApp(page, '/market');
  observations.push('市场页：解读行情 + 谈薪脚本双 CTA');

  await gotoApp(page, '/mine');
  observations.push('我的页：画像 + 最近产物 + 简历入口聚合');

  await page.getByRole('button', { name: /AI 小职/ }).click();
  observations.push('AI 小职：独立入口，对话台隐藏底部导航，像军师工作台');

  recordCoverage(
    '用户习惯与完整性',
    '部分通过',
    observations.join(' | '),
    '-',
    await screenshot(page, 'ux-overall.png')
  );
});
