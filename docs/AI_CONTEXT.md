# CareerMate AI Context

供后续在 Cursor / 其他 AI 工具中**快速恢复项目上下文**。描述以仓库**当前真实实现**为准。

## 1. 项目身份

- **名称**：CareerMate（求职智能体工作台）
- **路径**：`/Users/amy/CursorProject/careermate`
- **定位**：基于 **Java + Spring Boot + Vue** 的 AI 求职 Agent 应用，可部署、可演示的阶段性 MVP
- **目标架构文档**：`docs/design/CareerMate-architecture-v2.1.html`（桌面 `CareerMate-架构设计文档-V2.1.html` 为目标版，**不等于**当前已全部实现）

## 2. 当前阶段完成到哪里

**P0–P5 已收口**（见 `docs/ROADMAP.md`、`docs/PHASE_CLOSEOUT.md`）：

- 应用闭环：Agent、简历、匹配、面试、看板、画像、任务、工具卡片
- LLM：mock / qwen / deepseek / openai-compatible
- 观测：日志 traceId、Micrometer OTLP（可选）、SkyWalking **部署模板与文档**
- **未做**：RAGForge 业务集成、简历文件解析、Prompt/Eval/MCP

## 3. 已完成模块清单

| 模块 | 后端 | 前端 |
|------|------|------|
| 认证 | Security single-user/jwt | LoginView、authStore、http.js |
| Agent SSE | AgentStreamController、SseEmitterService | AgentChat.vue |
| 会话恢复 | listRecentSessions、消息列表 | 侧栏最近会话 |
| 多轮上下文 | AgentConversationContextProvider | — |
| 求职画像 | career_profiles、自动更新 | — |
| 求职任务 | career_tasks、Agent 工具 | Dashboard、任务卡片 |
| 简历 | resumes 文本 | ResumeStudio |
| 岗位匹配 | job_posts、job_matches | JobMatching |
| 面试 | interview 表 | InterviewPrep |
| Dashboard | dashboard API | CareerDashboard |
| Agent 工具 | AgentToolRouter + 各 Tool 实现 | agentToolDisplay.js |
| LLM | LlmClient、TracingLlmClient | — |
| 追踪 | TracingMdcFilter、TraceIdResolver | — |

## 4. 架构边界

- **单体** Spring Boot；**不用** WebFlux、**不用** Spring AI / LangChain4j 作核心
- **不**直接操作 pgvector / Elasticsearch（交给 RAGForge）
- **不**把 userId 交给前端或 LLM 参数；从 `CurrentUserContext` 取
- Redis **非**必选
- RAGForge：**外部** REST；本阶段 `RAGFORGE_ENABLED=false` 为默认

## 5. CareerMate 与 RAGForge

- RAGForge：已有/将有的 **JD 与共享知识** RAG 平台
- CareerMate：通过 `RagForgeClient`（`com.careermate.observability.ragforge`）+ `TraceHeaderPropagator` 调用
- **当前**：仅配置、传播头、Span 命名与文档；**无**生产 JD 检索业务
- 联动文档：`docs/ragforge-tracing-integration.md`、`docs/ragforge-skywalking-integration.md`

## 6. 简历数据原则

- 用户简历存 **PostgreSQL** `resumes`（文本字段），按 `user_id` 隔离
- 默认简历注入 Agent system prompt（长度摘要进 Trace，**不**打全文日志）
- **不上传**文件、**不**解析 PDF/Word 本阶段
- 用户私有简历 **默认不**写入 RAGForge 共享库（目标架构原则，P7 再定具体策略）

## 7. JD 知识库规划（未完成）

- JD 存入 RAGForge 知识库（`RAGFORGE_JD_KB_ID` 等配置预留）
- Agent / 岗位模块通过检索增强匹配与问答
- **下一阶段 P7**，本仓库勿写“已集成 RAGForge”

## 8. 当前 Agent 工具清单

| toolName | 用途 |
|----------|------|
| `get_default_resume` | 默认简历上下文 |
| `get_latest_job_match` | 最近岗位匹配 |
| `create_job_match` | 创建匹配（需 JD 文本等） |
| `create_interview_session` | 创建面试练习 |
| `get_dashboard_overview` | 看板概览 |
| `get_career_tasks` | 任务列表 |
| `create_career_task` | 创建任务 |
| `mark_career_task_done` | 完成任务 |

路由：`AgentToolRouter`（规则匹配，非 LLM tool_calls）。

Trace 中 LLM 摘要：`toolName=llm_chat`（`LlmChatTraceRecorder`）。

## 9. 重要 API（前缀 `/api`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/auth/register`、`/login` | 注册登录 |
| GET | `/auth/me` | 当前用户 |
| POST | `/agent/sessions` | 创建会话 |
| POST | `/agent/sessions/{id}/messages/stream` | SSE 对话 |
| GET | `/agent/sessions/{id}` | 会话+消息 |
| GET | `/agent/sessions/{id}/trace` | Trace 列表 |
| GET | `/agent/sessions/recent` | 最近会话（列表） |
| CRUD | `/resumes`、`/job-matches`、`/interviews`、`/tasks`、`/dashboard` 等 | 各业务模块 |
| POST | `/debug/llm/chat` | **仅 dev**；prod 默认关闭 |

## 10. 当前数据库表（Flyway）

- V1：`users`、`user_profiles`、`security_audit_logs`
- V2：`agent_sessions`、`agent_messages`、`agent_tool_calls`、`agent_task_states`
- V3：`resumes`
- V4：`job_posts`、`job_matches` 等
- V5：简历默认唯一索引
- V6：面试相关
- V7：`career_profiles`
- V8：`career_tasks`

详见 `docs/database-design.md` 与 `backend/src/main/resources/db/migration/`。

## 11. 部署约定

- 生产 profile：`prod`，密钥在 `/opt/careermate/backend/.env.app`
- 端口：本地 `8080`，生产 **`18080`**
- Nginx：`/careermate/`、`/careermate-api/`、`/skywalking/`
- 详见 `docs/DEPLOYMENT.md`、`docs/deployment-careermate.md`

## 12. 遗留问题 / 下一阶段建议

1. **RAGForge JD 集成**（最高业务优先级之一）
2. 简历文件上传解析（P6）
3. SkyWalking **云端** UI 与 Agent 同机部署验收
4. Playwright 云端全量回归进 CI
5. Agent 评测与 Prompt 版本管理
6. 任务工具 E2E 与 mock 话术对齐（偶发失败）

## 13. Cursor 工作规则（摘要）

- 不提前实现 Roadmap 外功能；不大改 UI 风格
- Flyway 管理 schema；业务带 `user_id`
- 日志禁止：API Key、完整 prompt、完整简历/JD、完整模型回复
- 本地进程结束后释放 8080/5173 端口

## 14. 文档索引

- [ROADMAP.md](ROADMAP.md)
- [ARCHITECTURE_SUMMARY.md](ARCHITECTURE_SUMMARY.md)
- [TESTING.md](TESTING.md)
- [DEPLOYMENT.md](DEPLOYMENT.md)
- [PHASE_CLOSEOUT.md](PHASE_CLOSEOUT.md)
- [skywalking-cloud-setup.md](skywalking-cloud-setup.md)
