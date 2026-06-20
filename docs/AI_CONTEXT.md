# CareerMate AI Context

供后续在 Cursor / Codex / 其他 AI 工具中快速恢复项目上下文。本文只描述仓库当前真实实现，不把目标架构当作已上线能力。

## 1. 项目身份

- 名称：CareerMate
- 本地路径：`/Users/amy/CursorProject/careermate`
- GitHub：`git@github.com:dezhiguan/careermate.git`
- 定位：AI 求职 Agent 工作台，覆盖认证、简历、岗位、面试、任务、市场、Agent 对话与 RAG 检索
- 结构：monorepo，`backend/` + `frontend/careermate/`

## 2. 当前完成范围

当前代码已完成 P0-P8：

- P0：Spring Boot / Vue / PostgreSQL / Flyway / 统一响应 / 全局异常 / 认证基础
- P1：Agent SSE、会话、消息、Trace、会话恢复、多轮上下文
- P2：简历、岗位匹配、面试训练、Dashboard
- P3：求职画像、求职任务
- P4：Agent 工具调用、工具卡片
- P5：Qwen、日志追踪、SkyWalking 模板
- P6：RAGForge 深度集成、Personal KB 简历同步、JD KB 检索
- P7：LLM 化岗位匹配/面试评分/面试题生成，Tika 文件解析
- P8：LLM 意图识别、Supervisor + 专家 Agent、ReAct

P9+ 未完成：联合一键部署、薪资谈判官、Agent Eval、Prompt 管理平台、云端完整 E2E 固化。

## 3. 关键模块

| 模块 | 后端入口 | 前端入口 |
|------|----------|----------|
| 认证 | `auth/**`、`security/**` | `LoginView.vue`、`authStore.js`、`api/http.js` |
| Agent 对话 | `agent/controller`、`agent/service`、`agent/runtime` | `AgentChat.vue` |
| Workspace | `workspace/**` | `AgentChat.vue` |
| 简历 | `resume/**`、`resume/version/**` | `ResumeManage.vue` |
| 岗位机会 | `opportunity/**` | `OpportunityView.vue` |
| 岗位匹配 | `jobmatch/**` | `JobMatching.vue` |
| 面试 | `interview/**` | `InterviewPrep.vue` |
| 市场洞察 | `market/**` | `MarketView.vue` |
| 看板/任务 | `dashboard/**`、`task/**` | 机会页/工具卡片 |
| RAGForge | `ragforge/**`、`knowledge/**` | 岗位匹配、市场、Agent |
| MCP | `mcp/**` | 外部 JSON-RPC 客户端 |

## 4. 认证与权限

- Auth Gateway 是主认证服务，CareerMate 调用它完成密码登录、手机号登录、密码重置和 token exchange。
- 后端通过 JWKS 验证 JWT 签名，校验 issuer、audience、expiration。
- 匿名只放行健康检查、登录注册、短信、密码重置和 auth event webhook。
- 其它 `/api/**` 需要 `Authorization: Bearer <access_token>`。
- 当前登录用户存在 `CurrentUserContext`，业务表按 `user_id` 隔离。
- `users.auth_user_id` 关联 Auth Gateway 用户 ID，`users.id` 是 CareerMate 业务用户 ID。
- Auth event webhook 用 HMAC + Redis 做 session/password 事件吊销。

详细说明见 `docs/SECURITY_AUTH.md`。

## 5. RAGForge 集成现状

CareerMate 通过 `RagForgeClient` 调用 RAGForge：

- 搜索 JD KB：岗位匹配页 `jd-kb-search` 和 Agent/知识检索链路使用。
- 搜索 Interview KB：面试题生成和回答评估可引用知识库上下文。
- Personal KB：简历保存/更新异步上传文本，删除简历时联动删除文档，`resumes.rag_doc_id` 记录版本。
- Token exchange：当前 Bearer token 交换为 RAGForge audience token。
- 观测：跨服务 trace header 透传。

默认 `RAGFORGE_ENABLED=false`，未启用或 KB ID 未配置时业务降级，不阻塞主流程。

## 6. Agent 当前实现

主要链路：

```text
用户消息
  -> AgentStreamController / WorkspaceController
  -> 会话与消息持久化
  -> 上下文装配：历史消息、画像、默认简历、最近岗位匹配
  -> LLM 意图识别 / 规则降级
  -> 可选工具调用 / ReAct / Supervisor 专家
  -> LLM 流式回复
  -> SSE token/message/done/error
  -> Trace/工具调用记录
```

当前工具示例：

- `get_default_resume`
- `get_latest_job_match`
- `create_job_match`
- `create_interview_session`
- `get_dashboard_overview`
- `get_career_tasks`
- `create_career_task`
- `mark_career_task_done`
- `search_knowledge_base`
- `generate_resume_from_jd`

工具定义包含权限和风险等级：`READ_USER_DATA`、`WRITE_USER_DATA`、`CALL_EXTERNAL_SERVICE`、`LONG_RUNNING_TASK`；`LOW`、`MEDIUM`、`HIGH`。

## 7. 前端路由

Hash 路由：

- `#/login`
- `#/chat`
- `#/chat/:wsId`
- `#/opportunity`
- `#/interview`
- `#/market`
- `#/mine`
- `#/mine/resume`

路由守卫会先 `authStore.init()`，未认证跳转登录，已认证会拉取 `homeStore.fetchBootstrap()`。

## 8. 数据库

Flyway migration 当前到 V27，核心表包括：

- 用户与安全：`users`、`user_profiles`、`security_audit_logs`
- Agent：`agent_sessions`、`agent_messages`、`agent_tool_calls`、`agent_task_states`、`agent_artifacts`、`agent_pending_actions`
- 业务：`resumes`、`resume_versions`、`job_posts`、`job_matches`、`interview_sessions`、`interview_questions`、`career_profiles`、`career_tasks`

数据库概要见 `docs/database-design.md`，DDL 以 `backend/src/main/resources/db/migration/` 为准。

## 9. 运行配置

- 后端默认 profile：`dev`
- `application.yml` 默认端口：`8081`
- `.env.example` 本地示例端口：`8080`
- `scripts/dev-start.sh` 未设置 `SERVER_PORT` 时使用：`8082`
- 生产端口：`18080`
- 前端 dev：`http://localhost:5173`
- 生产前端路径：`/careermate/`
- 生产 API 路径：`/careermate-api/`

## 10. 开发规则

- 不改已应用 Flyway migration；新增结构用新 migration。
- 不信任前端传 userId，统一从 `CurrentUserContext` 获取。
- 日志不记录密钥、token、验证码、完整 prompt、完整简历/JD、完整模型回复。
- `LLM_PROVIDER=mock` 是本地默认安全选择；真实 Key 只放 `.env` 或服务器 env。
- `RAGFORGE_ENABLED=false` 时必须优雅降级。
- `CAREERMATE_MCP_ENABLED=false` 是默认值，打开前要确认认证和资源边界。

## 11. 文档入口

- `README.md`
- `docs/SECURITY_AUTH.md`
- `docs/ARCHITECTURE_SUMMARY.md`
- `docs/ROADMAP.md`
- `docs/TESTING.md`
- `docs/DEPLOYMENT.md`
- `docs/database-design.md`
