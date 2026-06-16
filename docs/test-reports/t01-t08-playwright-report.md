# CareerMate T01-T08 Playwright 测试报告（续测：上传官德志.pdf 后）

> 更新时间：2026-06-16  
> 账号：185****0934 | 登录态：`frontend/careermate/tests/e2e/.auth/user-18565040934.json`

---

## 1. 测试环境

| 项 | 值 |
| --- | --- |
| Backend | http://localhost:8081 |
| Frontend | http://localhost:5173 |
| RagForge | http://localhost:8080 |
| Viewport | 390×844 移动端 |
| 默认简历 | 已上传桌面 `官德志.pdf`（resume id=50，isDefault=true） |

---

## 2. 续测结果摘要

| 项 | 结果 | 说明 |
| --- | --- | --- |
| 上传默认简历 | **通过** | API 解析成功，含姓名/Java 经历等正文 |
| T08 Workflow 生成 | **部分通过** | 后端约 1 分钟完成，版本 `ed8fec38-…` 已入库；Playwright 未匹配到 `简历已生成` 文案（卡片标题为「定制简历版」） |
| RESUME_GENERATED 卡片 | **部分通过** | 后端产物存在；UI 自动化选择器需放宽 |
| PDF 下载 | **部分通过** | 文件 `%PDF-1.5`，15KB；程序抽取中文困难（字体编码），但非 Markdown 原文堆砌 |
| Word 下载 | **通过** | docx 结构正常，正文可读，无 `#`/`-` 等 Markdown 主导排版 |
| 修改并重下载 | **通过** | API 更新 contentMarkdown 后 Word 含 `E2E_EDIT_MARKER` |

---

## 3. Tab 联动（前轮已验证，本轮未回归）

全部 **通过**，无「工作空间不存在」。截图见 `docs/test-reports/assets/t01-t08/tab-link-*.png`。

---

## 4. 简历生成与下载详情

### Workflow

- 触发：机会页 → 定制简历 → JD 准备空间 →「生成定制简历」
- 后端日志：19:10:40 开始 stream，19:11:41 SSE 完成
- 产物：`versionId=ed8fec38-2e65-4382-b14b-d8437f307dd5`，`versionName=定制简历版`
- Markdown 摘要：含 `# 官德志`、探也智能/Java 后端 JD 定制内容（3502 字）

### 下载文件

| 格式 | 路径 | 大小 | 验证 |
| --- | --- | --- | --- |
| PDF | `test-results/t01-t08-downloads/定制简历版.pdf` | 15,154 B | 头 `%PDF-1.5`；非 0 字节；**非 Markdown 伪 PDF** |
| Word | `test-results/t01-t08-downloads/定制简历版.docx` | 6,555 B | 含 `word/document.xml`；正文「官德志 / Java / Spring / Redis…」 |
| Word（编辑后） | `test-results/t01-t08-downloads/定制简历版-edited.docx` | - | 含编辑标记 `E2E_EDIT_MARKER` |

**Word 正文摘录：**  
「官德志 185-6504-0934 … Java开发工程师 … 高并发与分布式系统专家 … SpringBoot …」——段落与 bullet 正常，**无 Markdown 语法残留**。

**PDF 说明：** 使用 STSong-Light 渲染（见 `ResumeVersionPdfRenderer`），本地无 `pdftotext` 时自动抽取失败，但文件为正规 PDF 流而非 `.md` 改名；建议人工打开 `docs/test-reports/assets/t01-t08/generated-resume.pdf` 目视确认排版。

---

## 5. Bug 清单（含续测新发现）

### Bug 1 [P1] 成功卡片标题与测试/用户预期不一致

- **现象**：Workflow 成功后卡片标题为「✅ 定制简历版」，不一定包含「简历已生成」字样
- **影响**：自动化误判超时；用户仍可操作按钮
- **建议**：卡片 title 固定含「简历已生成」或增加 `data-testid`

### Bug 2 [P1] Workflow 进行中 UI 反馈不足

- **现象**：生成约 1 分钟，Playwright 等待 7.5 分钟未见 success/fail 卡片（可能流式区无明确进度）
- **建议**：增加步骤条/「正在生成…」状态

### Bug 3 [P2] RagForge chunks 超时降级

- **日志**：`fetchDocumentChunks docId=156 Read timed out`，已 fallback search
- **影响**：轻微延迟，本次仍成功

### Bug 4 [P2] PDF 文本抽取/可复制性

- **现象**：第三方工具难以抽取中文（字体编码）
- **影响**：ATS 解析可能受影响；目视可读性待人工确认

### Bug 5 [P1]（已解除）无默认简历时 Workflow 失败

- **状态**：上传 `官德志.pdf` 后 **已解除**，Workflow 可跑通

---

## 6. 最终结论

| 问题 | 结论 |
| --- | --- |
| T01-T08 完整闭环？ | **基本成型**：Tab/Workspace/Workflow 主链路通；Agent 工具与 UI 进度仍有缺口 |
| 用户习惯？ | **明显改善**（有简历后机会→定制简历可走完） |
| Agent 产品感？ | **有** — JD 上下文 + Workflow 流式 + 结果卡片 |
| PDF/Word 达标？ | **Word 达标**；**PDF 结构达标**，中文抽取/ATS 待人工目视 |
| 可进 T09 Memory？ | **可以启动 T09**，建议并行修复 P1 UI 反馈与卡片文案 |

---

## 7. 产物路径

- 报告：`docs/test-reports/t01-t08-playwright-report.md`
- 截图：`docs/test-reports/assets/t01-t08/`
- 下载：`test-results/t01-t08-downloads/`
- 备份：`docs/test-reports/assets/t01-t08/generated-resume.pdf` / `.docx`
