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
- **RAGForge**：`RagForgeClient` + 传播头预留；**业务 RAG 集成未完成**

---

## 下一阶段建议

### P6 简历上传解析

- 文件上传、解析 pipeline
- 与现有文本简历模型衔接

### P7 RAGForge JD 知识库集成

- 启用 `RAGForgeClient` 业务调用
- JD 入库、检索增强 Agent / 岗位模块
- 与 SkyWalking / trace 头跨服务延续

### P8 JD 闭环能力

- 基于 JD 的推荐、匹配、简历优化、面试准备一体化

### P9 Agent 评测与 Prompt 管理

- Prompt 模板与版本
- Agent Evaluation 数据集与回归

### P10 开源展示增强

- 演示数据、一键部署、文档与截图
- 云端完整 Playwright 端到端回归固化

---

## 目标架构文档说明

仓库内 [`docs/design/CareerMate-architecture-v2.1.html`](design/CareerMate-architecture-v2.1.html) 与桌面上的 **CareerMate-架构设计文档-V2.1.html** 描述**目标架构**。

当前实现为**阶段性 MVP**：核心应用闭环已可演示，**RAGForge 深度集成、简历文件解析、Agent 评测、Prompt 管理**仍在 Roadmap。
