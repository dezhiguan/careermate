// @ts-check
const fs = require('fs');
const path = require('path');

const PROJECT_ROOT = path.resolve(__dirname, '../../../../../');
const REPORT_DIR = path.join(PROJECT_ROOT, 'docs/test-reports');
const ASSETS_DIR = path.join(REPORT_DIR, 'assets/t01-t08');
const REPORT_MD = path.join(REPORT_DIR, 't01-t08-playwright-report.md');
const REPORT_JSON = path.join(REPORT_DIR, 't01-t08-playwright-report.json');

/** @type {Record<string, unknown>} */
let state = loadState();

function loadState() {
  if (fs.existsSync(REPORT_JSON)) {
    try {
      return JSON.parse(fs.readFileSync(REPORT_JSON, 'utf8'));
    } catch {
      // fall through
    }
  }
  return {
    env: {},
    coverage: {},
    tabLinkage: {},
    resumeWorkflow: {},
    bugs: [],
    startedAt: new Date().toISOString(),
    finishedAt: null,
  };
}

function persistState() {
  fs.mkdirSync(REPORT_DIR, { recursive: true });
  fs.writeFileSync(REPORT_JSON, JSON.stringify(state, null, 2), 'utf8');
}

function resetState() {
  state = {
    env: {},
    coverage: {},
    tabLinkage: {},
    resumeWorkflow: {},
    bugs: [],
    startedAt: new Date().toISOString(),
    finishedAt: null,
  };
  persistState();
}

function ensureDirs() {
  fs.mkdirSync(ASSETS_DIR, { recursive: true });
  fs.mkdirSync(path.join(PROJECT_ROOT, 'test-results/t01-t08-downloads'), { recursive: true });
}

function setEnv(env) {
  state.env = { ...state.env, ...env };
  persistState();
}

/**
 * @param {string} id
 * @param {'通过'|'部分通过'|'失败'|'跳过'} status
 * @param {string} observation
 * @param {'P0'|'P1'|'P2'|'P3'|'-'} severity
 * @param {string} [screenshot]
 */
function recordCoverage(id, status, observation, severity = '-', screenshot = '') {
  state.coverage[id] = { status, observation, severity, screenshot };
  persistState();
}

/**
 * @param {string} entry
 * @param {Record<string, unknown>} data
 */
function recordTabLinkage(entry, data) {
  state.tabLinkage[entry] = data;
  persistState();
}

/**
 * @param {Record<string, unknown>} data
 */
function recordResumeWorkflow(data) {
  state.resumeWorkflow = { ...state.resumeWorkflow, ...data };
  persistState();
}

/**
 * @param {Record<string, unknown>} bug
 */
function addBug(bug) {
  state.bugs.push(bug);
  persistState();
}

function assetPath(name) {
  return `docs/test-reports/assets/t01-t08/${name}`;
}

function writeReport() {
  state.finishedAt = new Date().toISOString();
  fs.mkdirSync(REPORT_DIR, { recursive: true });
  fs.writeFileSync(REPORT_JSON, JSON.stringify(state, null, 2), 'utf8');

  const env = state.env;
  const lines = [];
  lines.push('# CareerMate T01-T08 Playwright 测试报告');
  lines.push('');
  lines.push(`> 生成时间：${state.finishedAt}`);
  lines.push('');
  lines.push('## 1. 测试环境');
  lines.push('');
  lines.push(`| 项 | 值 |`);
  lines.push(`| --- | --- |`);
  lines.push(`| Backend | ${env.backend || '-'} |`);
  lines.push(`| Frontend | ${env.frontend || '-'} |`);
  lines.push(`| RagForge | ${env.ragforge || '-'} |`);
  lines.push(`| 浏览器 | ${env.browser || '-'} |`);
  lines.push(`| Viewport | ${env.viewport || '-'} |`);
  lines.push(`| 登录账号 | ${env.phone || '-'} |`);
  lines.push(`| 测试开始 | ${state.startedAt} |`);
  lines.push(`| Auth State | ${env.authStatePath || '-'} |`);
  lines.push('');

  lines.push('## 2. T01-T08 覆盖表');
  lines.push('');
  lines.push('| 项 | 结果 | 问题等级 | 证据 | 关键观察 |');
  lines.push('| --- | --- | --- | --- | --- |');
  for (const [id, row] of Object.entries(state.coverage)) {
    const r = /** @type {{status:string;severity:string;screenshot:string;observation:string}} */ (row);
    lines.push(`| ${id} | ${r.status} | ${r.severity} | ${r.screenshot || '-'} | ${r.observation} |`);
  }
  lines.push('');

  lines.push('## 3. Tab 联动结果');
  lines.push('');
  for (const [entry, row] of Object.entries(state.tabLinkage)) {
    const r = /** @type {Record<string, unknown>} */ (row);
    lines.push(`### ${entry}`);
    lines.push(`- 结果：**${r.status || '-'}**`);
    lines.push(`- URL：${r.url || '-'}`);
    lines.push(`- 工作空间上下文：${r.context || '-'}`);
    lines.push(`- 工作空间不存在：${r.workspaceMissing ? '是' : '否'}`);
    lines.push(`- 上下文丢失：${r.contextLost ? '是' : '否'}`);
    lines.push(`- 卡片/按钮失败：${r.cardFailure || '无'}`);
    if (r.screenshot) lines.push(`- 截图：${r.screenshot}`);
    if (r.note) lines.push(`- 备注：${r.note}`);
    lines.push('');
  }

  lines.push('## 4. 简历生成结果');
  lines.push('');
  const rw = /** @type {Record<string, unknown>} */ (state.resumeWorkflow);
  lines.push(`- Workflow 成功：${rw.success ?? '-'}`);
  lines.push(`- 生成卡片完整：${rw.cardComplete ?? '-'}`);
  lines.push(`- PDF 路径：${rw.pdfPath ?? '-'}`);
  lines.push(`- Word 路径：${rw.wordPath ?? '-'}`);
  lines.push(`- PDF 文本摘要：${rw.pdfTextSummary ?? '-'}`);
  lines.push(`- Word 文本摘要：${rw.wordTextSummary ?? '-'}`);
  lines.push(`- Markdown 伪格式：${rw.markdownIssue ?? '-'}`);
  lines.push(`- 支持修改并重新下载：${rw.editAndRedownload ?? '-'}`);
  lines.push('');

  lines.push('## 5. Bug 清单');
  lines.push('');
  const order = ['P0', 'P1', 'P2', 'P3'];
  const bugs = /** @type {Array<Record<string, unknown>>} */ (state.bugs).sort(
    (a, b) => order.indexOf(String(a.severity)) - order.indexOf(String(b.severity))
  );
  if (bugs.length === 0) {
    lines.push('无记录 Bug。');
  } else {
    bugs.forEach((b, i) => {
      lines.push(`### Bug ${i + 1} [${b.severity}] ${b.title}`);
      lines.push(`- **复现步骤**：${b.steps}`);
      lines.push(`- **实际结果**：${b.actual}`);
      lines.push(`- **期望结果**：${b.expected}`);
      lines.push(`- **证据**：${b.evidence || '-'}`);
      lines.push(`- **影响范围**：${b.impact || '-'}`);
      lines.push(`- **建议修复方向**：${b.fix || '-'}`);
      lines.push('');
    });
  }

  lines.push('## 6. 最终结论');
  lines.push('');
  lines.push(`- T01-T08 是否形成完整产品闭环：**${rw.closureVerdict ?? '见覆盖表'}**`);
  lines.push(`- 是否符合用户习惯：**${rw.uxVerdict ?? '见 Tab 联动与截图'}**`);
  lines.push(`- 是否像 Agent 产品：**${rw.agentVerdict ?? '见工具/trace/workflow 项'}**`);
  lines.push(`- PDF/Word 简历是否达标：**${rw.downloadVerdict ?? '见第 4 节'}**`);
  lines.push(`- 是否可以进入 T09 Memory：**${rw.t09Verdict ?? '待定'}**`);

  fs.writeFileSync(REPORT_MD, lines.join('\n'), 'utf8');
  return REPORT_MD;
}

module.exports = {
  PROJECT_ROOT,
  ASSETS_DIR,
  REPORT_MD,
  ensureDirs,
  resetState,
  setEnv,
  recordCoverage,
  recordTabLinkage,
  recordResumeWorkflow,
  addBug,
  assetPath,
  writeReport,
  getState: () => state,
};
