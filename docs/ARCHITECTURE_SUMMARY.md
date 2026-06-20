# CareerMate 架构摘要

本文描述当前代码架构。`docs/design/CareerMate-architecture-v2.1.html` 是目标架构设计，不能直接等同于当前实现。

## 1. 总体架构

```text
Browser (Vue 3 + Vite)
    |
    | HTTPS / Nginx
    v
CareerMate Backend (Spring Boot, stateless API)
    |
    |-- PostgreSQL: 业务数据、会话、Trace、审计
    |-- Redis: 短信限流、验证码、auth event 幂等与吊销
    |-- Auth Gateway: 登录、短信、密码重置、JWKS、token exchange
    |-- LLM Provider: mock / qwen / deepseek / openai-compatible
    |-- RAGForge: JD KB、Interview KB、Personal KB
    |-- SkyWalking / OTLP: 链路观测
```

后端是单体 Spring Boot 服务，不使用 WebFlux。Spring AI 依赖作为旁路能力保留，主聊天链路仍走项目内 `LlmClient` 抽象。

## 2. 前端分层

| 层 | 说明 |
|----|------|
| Router | Hash 路由，统一登录守卫 |
| Stores | `authStore`、`homeStore` 管理会话和首页 bootstrap |
| API | `src/api/http.js` 统一注入 Bearer token、处理 trace header 和 401 |
| Views | 登录、机会、面试、小职、市场、我的、简历管理 |
| Components | App shell、聊天卡片、Agent 工具卡片 |

生产构建通过 `VITE_API_BASE_URL=/careermate-api`、`VITE_BASE_PATH=/careermate/` 适配入口层 Nginx。

## 3. 认证与安全

CareerMate API 使用 stateless security：

- 匿名只放行健康检查、登录注册、短信、密码重置和 auth event webhook。
- 其它 `/api/**` 要求 Bearer token。
- JWT 由 Auth Gateway 签发，CareerMate 通过 JWKS 校验签名。
- JWT 校验 issuer、audience、expiration。
- `users.auth_user_id` 映射 Auth Gateway 用户，`users.id` 用于本地业务表。
- `CurrentUserContext` 是后端业务获取当前用户的唯一入口。
- Auth event webhook 用 HMAC + Redis 做 token/session/password 事件吊销。

详见 `docs/SECURITY_AUTH.md`。

## 4. Agent 架构

```text
SSE 请求
  -> Controller
  -> AgentSessionService / WorkspaceService
  -> AgentMemory / Context Provider
  -> Intent Recognizer / Tool Router
  -> AgentToolExecutionService
  -> ReActEngine / AgentSupervisor
  -> LlmClient stream
  -> SseEmitter
  -> Trace 持久化
```

关键能力：

- SSE 流式输出，支持 token/message/done/error/heartbeat。
- 会话、消息、Trace 持久化，支持刷新恢复。
- 多轮上下文、求职画像、默认简历、最近岗位匹配注入 prompt。
- LLM 意图识别失败时降级到规则工具路由。
- Supervisor 并行调度 Resume/JobMatch/Interview/Market 等专家能力。
- ReAct 非流式推理循环最多 3 轮，结果注入最终回复，不向用户展示 Chain-of-Thought。

## 5. RAGForge 边界

RAGForge 负责知识库、文档和检索；CareerMate 负责求职工作台和业务数据。

当前集成：

- `RagForgeClient`：搜索、文本上传、文档删除。
- 简历保存/更新/删除同步 Personal KB。
- JD KB 搜索用于岗位匹配页和上下文增强。
- Interview KB 用于面试题生成和回答评估辅助。
- Bearer token 通过 Auth Gateway token exchange 换取 RAGForge audience token。
- trace header 跨服务透传。

关闭 `RAGFORGE_ENABLED` 或缺少 KB ID 时，业务应降级而不是失败。

## 6. 数据分层

| 类型 | 表 |
|------|----|
| 用户安全 | `users`、`user_profiles`、`security_audit_logs` |
| Agent Runtime | `agent_sessions`、`agent_messages`、`agent_tool_calls`、`agent_task_states` |
| Agent 工作流 | `agent_artifacts`、`agent_pending_actions` |
| 求职业务 | `resumes`、`resume_versions`、`job_posts`、`job_matches`、`interview_sessions`、`interview_questions`、`career_profiles`、`career_tasks` |

所有用户私有业务表都必须带 `user_id` 或通过父表归属校验间接隔离。

## 7. 观测

- `TracingMdcFilter` 写入 `requestId` / `traceId`。
- 响应头输出 `X-Request-Id` / `X-Trace-Id`。
- 日志 MDC 包含 `userId`、`sessionId` 等上下文。
- LLM/RAG/Agent Trace 记录摘要，不写完整 prompt、简历、JD、密钥或 token。
- 生产推荐 SkyWalking Java Agent；Micrometer OTLP 可选。

## 8. 部署视图

生产推荐三层：

| 层 | 组件 |
|----|------|
| 数据层 | PostgreSQL、Redis、ES、RocketMQ |
| 入口层 | Nginx、CareerMate 静态资源、RAGForge 静态资源 |
| 应用层 | CareerMate backend、RAGForge backend、SkyWalking Agent |

CareerMate 后端生产端口 `18080`，入口路径 `/careermate/`，API 路径 `/careermate-api/`。
