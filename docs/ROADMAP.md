# CareerMate Roadmap

本文件描述**阶段性 MVP** 的完成范围与下一阶段计划。未完成项请勿当作已上线能力。

## 已完成阶段

### P0 基础工程与认证

- Spring Boot 3.2 + PostgreSQL + Flyway
- 统一 `ApiResponse`、全局异常处理
- Spring Security：`single-user` / `jwt`
- `POST /api/auth/register`、`/login`、`GET /api/auth/me`
- 前端登录、路由守卫、`http.js` 鉴权

### P1 Agent 对话台

- SSE 流式对话（plan / token / message / done / error / heartbeat）
- 会话 / 消息 / Trace 持久化
- 会话历史列表与**刷新后恢复**
- **多轮上下文**注入 prompt
- SSE 防卡死（超时、done/error 兜底、前端流式状态恢复）
- 规则路由 + 工具调用框架

### P2 简历 / 岗位匹配 / 面试 / Dashboard

- 文本简历 CRUD、默认简历、Agent 上下文注入
- 岗位匹配创建与查询、最近匹配上下文
- 面试训练会话与答题
- Dashboard 概览与建议

### P3 求职画像与任务

- `career_profiles` 跨会话画像记忆
- `career_tasks` 求职任务清单（Dashboard + Agent）

### P4 Agent 工具调用

- 规则 `AgentToolRouter` + `AgentToolExecutionService`
- 工具：简历、岗位匹配、面试、看板、任务等（见 `docs/AI_CONTEXT.md`）
- 前端**工具卡片**与页面跳转
- Mock LLM 工具话术与 Trace 记录

### P5 Qwen / 工程化观测收口

- **Qwen**：`LlmClient` + DashScope OpenAI 兼容；生产通过 `.env.app` 切换（**非**仓库内配 Key）
- **日志追踪**：MDC `traceId` / `requestId` / `userId` / `sessionId`；`X-Request-Id` / `X-Trace-Id`
- **LLM 耗时日志**：`llm.chat`（无 prompt / Key 泄露）
- **SkyWalking**：OAP/UI compose、Java Agent 与 Nginx `/skywalking/` **部署文档与配置模板**（云端 UI 需按文档在服务器启用）
- **RAGForge**：`RagForgeClient` + 传播头预留（P6 起业务 RAG 已集成，见下方）

### P6 RAGForge 深度集成

- RagForgeClient（搜索、文本上传、删除文档）
- 简历保存/更新/删除时异步同步 Personal KB，rag_doc_id 版本追踪
- POST /api/v1/documents/text 文本直传（RAGForge 侧新增）
- JobMatchAnalyzer 调用 JD KB 做 RAG 上下文增强

### P7 LLM 升级 + 简历文件上传

- JobMatchAnalyzer / InterviewAnswerEvaluator / InterviewQuestionGenerator 全部 LLM 化 + Structured Output
- 简历文件上传解析（Tika，PDF/Word/Markdown）
- 岗位匹配页 JD 库浏览（搜索 + 一键填入）

### P8 Agent 架构升级

- AgentLlmIntentRecognizer：LLM 语义意图识别，降级到 regex
- AgentSupervisor + 3 个专家 Agent（Resume/JobMatch/Interview）并行编排
- ReActEngine：非流式推理循环（最多 3 轮），结果注入 system prompt

---

## 下一阶段建议（P9+）

- 联合 docker-compose 一键部署（RAGForge + CareerMate + 中间件）
- 薪资谈判官（Salary KB + 谈判脚本，新模块）
- Agent 评测与 Prompt 管理
- 云端完整 Playwright 端到端回归固化

---

## 目标架构文档说明

仓库内 [`docs/design/CareerMate-architecture-v2.1.html`](design/CareerMate-architecture-v2.1.html) 与桌面上的 **CareerMate-架构设计文档-V2.1.html** 描述**目标架构**。

当前实现为**阶段性 MVP（P0–P8）**：核心应用闭环 + RAGForge 深度集成已可演示，**联合部署、Agent 评测、Prompt 管理**仍在 Roadmap。
