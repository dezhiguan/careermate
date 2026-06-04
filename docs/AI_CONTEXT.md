# CareerMate AI Context

## 1. Project Identity

- Project name: CareerMate
- Repository name: careermate
- Chinese name: CareerMate 求职智能体
- Positioning: AI Job Search Agent built with Java, Spring Boot, Vue and RAGForge
- Goal: 一个可开源、可部署、可演示的 Java AI Agent 应用，不是 demo，不是普通聊天壳子。

## 2. Current Project Path

```text
/Users/amy/CursorProject/careermate
```

## 3. Current Status

记录当前状态：

- 后端基础骨架已创建。
- 后端端口使用 **8080**。
- 统一响应体 `ApiResponse` 与全局异常处理已就绪。
- 健康检查接口 `GET /api/health` 可用。
- Flyway 已开启；用户核心表 migration（V1）已落地。
- 已创建表：`users`、`user_profiles`、`security_audit_logs`。
- 已创建对应 Entity 与 MyBatis-Plus Mapper（无业务接口）。
- 认证与用户隔离（阶段三）已开始并完成基础能力：
  - Spring Security 已接入。
  - 支持 `single-user` / `jwt` 双模式。
  - `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me` 已提供。
  - JWT 生成与校验已接入。
  - `CurrentUser` / `CurrentUserContext` 已接入。
  - 注册、登录会写入 `security_audit_logs`。
- 默认安全模式为 `single-user`（通过 `SECURITY_MODE` 可切换为 `jwt`）。
- 前端 Vue 页面已导入，并且可以启动（路径：`frontend/careermate/`）。
- 前端 Auth 接入（阶段四）已完成基础能力：
  - 新增统一请求层 `src/api/http.js`（fetch + 自动 Authorization）。
  - 新增认证 API `src/api/auth.js`。
  - 新增轻量认证状态 `src/stores/authStore.js`（localStorage 持久化）。
  - 新增登录页 `src/views/LoginView.vue`（含登录/注册与单用户入口）。
  - 新增路由守卫，未认证访问受保护路由会跳转 `/login`。
  - 401 会自动清理登录态并跳转登录页。
- 当前已有 5 个前端页面：
  - Agent 对话台
  - 简历工作室
  - 岗位匹配
  - 面试特训
  - 求职看板
- 前端主要业务页已对接后端 API（Agent / 简历 / 岗位匹配 / 面试 / 看板）。
- LLM 抽象层（已完成）：
  - `LlmClient` + `mock` / `deepseek` / `qwen` / `openai-compatible`。
  - 本地默认 `mock`；线上通过环境变量切换 `qwen` + `qwen-plus` + DashScope OpenAI 兼容 endpoint。
  - `QwenLlmClient` 复用 `OpenAiCompatibleLlmClient`（`/chat/completions` + Bearer Token）。
  - 调用失败返回明确摘要，SSE 走 `error` + `done` 兜底，不泄露 API Key。
  - `POST /api/debug/llm/chat` 仅 dev 默认开启；`prod` profile 关闭（`careermate.debug.llm-api-enabled=false`）。
- 已收口模块：Agent 对话、会话恢复、多轮上下文、求职画像、求职任务工具、简历、岗位匹配、面试训练、Dashboard、工具卡片。
- **下一阶段（仅 Roadmap）**：RAGForge JD 知识库集成（本仓库当前不实现 RAG 代码）。
- SSE 基础设施（阶段六）已完成基础能力：
  - 基于 Spring MVC `SseEmitter` 的 SSE 连接与事件发送基础设施。
  - 独立 `agent-executor` 线程池，避免阻塞 Tomcat 请求线程执行长任务。
  - SSE 事件统一结构 `SseEvent` + `SseEventType`（本阶段使用 plan/token/message/done/error/heartbeat）。
  - 支持同一 session 同时仅一个流式任务运行（冲突返回 429）。
  - 支持基础取消：连接关闭/超时/错误会取消运行中任务并清理资源。
  - 提供 mock 流式对话接口 `POST /api/agent/sessions/{sessionId}/messages/stream`，通过 `LlmClient.streamChat` 输出 token。
- 前端阶段七（已完成当前范围）：
  - `AgentChat` 已对接后端 SSE mock stream（plan/token/message/done/error/heartbeat）。
  - 页面可创建 session 并展示当前 sessionId、流式状态、trace 与 latency。
  - 其他业务页（简历/岗位/面试/看板）仍使用 mock 数据，待后端业务接口就绪后再对接。
- Agent Session / Message / Trace 持久化（阶段八，已完成当前范围）：
  - Flyway `V2__init_agent_runtime_tables.sql` 已落地：`agent_sessions` / `agent_messages` / `agent_tool_calls` / `agent_task_states`。
  - `AgentSessionService` 负责创建会话、追加消息、记录 Trace、标记完成/错误；所有查询按 `user_id` 隔离。
  - `POST /api/agent/sessions` 创建会话并落库；`POST .../messages/stream` 在 mock 流式过程中持久化 user/agent 消息与 PLAN/MESSAGE/DONE/ERROR Trace。
  - `GET /api/agent/sessions/{sessionId}` 查询会话详情与消息列表；`GET /api/agent/sessions/{sessionId}/trace` 查询 Trace 列表。
  - 前端 `AgentChat` 支持「刷新 Trace」从服务端拉取持久化记录；历史消息列表恢复留待下一阶段。
  - **当前 Agent 仍是 mock stream + LLM 抽象，不是完整 Agent Runtime**；未实现 Tool Registry / RAGForge 对接。
- 项目文档目录 `docs/` 已建立，含架构设计、原型设计与本文件。

## 4. Core Architecture Decisions

写清楚以下决策：

- 后端使用 Java 17 + Spring Boot 3.2。
- 前端使用 Vue 3 + Vite。
- 数据库使用 PostgreSQL 15 + JSONB。
- 数据库 migration 使用 Flyway。
- 认证使用 Spring Security + JWT，后续实现 single-user / jwt 双模式。
- LLM 不直接绑定 DeepSeek，使用 LlmClient 抽象。
- LLM Provider 支持 `mock / deepseek / qwen / openai-compatible`，并通过统一 `LlmClient` 屏蔽供应商差异。
- `qwen` 使用 DashScope OpenAI-compatible 协议，默认 endpoint 为 `https://dashscope.aliyuncs.com/compatible-mode/v1`。
- 不使用 Spring AI / LangChain4j 作为核心 Agent Runtime。
- 可以预留 Spring AI Adapter / LangChain4j Adapter，但不进入核心链路。
- RAG 能力由 RAGForge 提供，CareerMate 通过 REST API 调用。
- CareerMate 不直接操作 pgvector / Elasticsearch。
- 用户私有简历默认不进入共享 RAGForge 知识库。
- Agent 使用单主控 Agent + 分层工具系统 + 工具可并行执行。
- 不展示 Chain-of-Thought，只展示 Agent Trace。
- Redis 不是必选依赖，只作为多实例、缓存、限流、SSE 协同的可选增强。

## 5. Frontend Design Memory

记录当前前端风格：

- 轻量 SaaS 工具风格。
- 白色 / 浅灰背景。
- navy 深色重点区域。
- purple 主色。
- green / amber / red 表示状态。
- 信息密度偏高，适合求职 Agent 工作台。
- 当前底部导航偏移动端风格，后续可逐步调整为 PC Web 优先的响应式布局。

前端后续必须修正的口径：

- “Agent 思考”需要改成“Agent Trace / 执行轨迹”。
- 不展示 Chain-of-Thought 原文。
- 简历页面不要展示 Chunk 数。
- 不要写 RAGForge 解析简历。
- Agent 跳转要表现为 UI Action 推荐跳转，不是 LLM 任意控制路由。

## 6. Planned Development Phases

记录后续阶段：

1. 项目基础骨架 ✅
2. 数据库 Migration + 用户核心表 ✅
3. 认证与用户隔离 ✅（基础能力）
4. 前端 Auth 接入 ✅（基础能力）
5. LLM 抽象层 ✅（基础能力）
6. SSE 基础设施 ✅（基础能力）
7. Agent Session + Message + Trace 基础
8. Tool Registry + ToolExecutor
9. Memory 基础能力
10. 简历上传、解析与画像
11. RAGForge Client + Knowledge Tools
12. 岗位匹配 + 技能差距 + 看板
13. Agent Runtime 闭环
14. 面试训练
15. Prompt 模板与版本管理
16. Agent Evaluation 评测体系
17. Metrics / Cost / Observability
18. MCP Adapter NoOp 实现
19. 前端 UI 完整打磨
20. Docker Compose 联合部署 + README + 演示数据

## 7. Current Next Task

当前下一步建议：

**RAGForge JD 知识库集成**（岗位 JD 检索增强，通过 REST 调用 RAGForge，不直接操作 pgvector/ES）。

本阶段已完成：**真实 Qwen 切换 + 已完成功能收口**（保持 mock 供本地/CI）。

## 8. Cursor Working Rules

写入以下规则：

- 每次只实现当前阶段任务，不要提前实现后续阶段。
- 不要一次性实现完整 Agent 系统。
- 不要随意重构前端 UI。
- 不要引入未经确认的框架。
- 不要引入 Spring AI 作为核心框架。
- 不要引入 LangChain4j 作为核心框架。
- 不要引入 WebFlux。
- 不要引入 Redis 作为必选依赖。
- 不要把 userId 暴露给前端或 LLM 参数。
- 不要把简历原文写入日志。
- 不要展示 Chain-of-Thought。
- 所有数据库变更必须走 Flyway migration。
- 所有用户私有数据必须带 user_id。
- 所有后续业务逻辑必须从 `CurrentUserContext` 获取 `userId`，禁止前端传入。
- 所有接口必须使用统一响应结构。
- 前端后续业务 API 统一通过 `src/api/http.js` 调用。
- 本地验证启动的后端 / 前端进程，结束后应释放端口（8080、5173 等），避免占用。

## 9. Important Documents

列出：

- [docs/design/CareerMate-architecture-v2.1.html](design/CareerMate-architecture-v2.1.html)
- [docs/design/CareerMate-prototype-design.html](design/CareerMate-prototype-design.html)
- [docs/AI_CONTEXT.md](AI_CONTEXT.md)

桌面原始文档（勿删，仓库内为副本）：

- `/Users/amy/Desktop/rag最终版本/CareerMate-架构设计文档-V2.1.html`
- `/Users/amy/Desktop/rag最终版本/agent-service-design.html`
