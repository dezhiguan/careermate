# CareerMate Roadmap

本文件描述当前已完成范围与下一阶段计划。未完成项不要写成已上线能力。

## 已完成阶段

### P0 基础工程与统一认证

- Spring Boot 3.5、Java 21、PostgreSQL、Flyway、MyBatis-Plus。
- Vue 3、Vite、Vue Router。
- 统一 `ApiResponse`、全局异常、健康检查。
- Spring Security stateless API。
- Auth Gateway 接入：密码登录、手机号登录、密码重置、JWKS JWT 校验。
- 前端登录/注册/短信登录/密码重置、路由守卫、401 自动退出。
- 用户基础表、Profile 表、安全审计表。

### P1 Agent 对话台

- SSE 流式对话：token / message / done / error / heartbeat。
- 会话、消息、Trace 持久化。
- 会话历史列表与刷新后恢复。
- 多轮上下文注入 prompt。
- SSE 防卡死：超时、done/error 兜底、前端流式状态恢复。
- 规则工具路由基础框架。

### P2 简历 / 岗位匹配 / 面试 / Dashboard

- 文本简历 CRUD、默认简历、Agent 上下文注入。
- 岗位匹配创建、查询、最近匹配上下文。
- 面试训练会话、答题、完成。
- Dashboard 概览、技能差距和建议。

### P3 求职画像与任务

- `career_profiles` 跨会话画像记忆。
- `career_tasks` 求职任务清单。
- Dashboard、Agent 工具和前端卡片联动。

### P4 Agent 工具调用

- `AgentToolRegistry`、`AgentToolRouter`、`AgentToolExecutionService`。
- 工具覆盖简历、岗位匹配、面试、看板、任务、知识检索。
- 工具定义包含 permission 与 riskLevel。
- 前端工具卡片与页面跳转。
- Mock LLM 工具话术与 Trace 记录。

### P5 Qwen / 工程化观测

- `LlmClient` 抽象，支持 mock / qwen / deepseek / openai-compatible。
- DashScope OpenAI 兼容接入，生产密钥只放服务器 env。
- MDC `traceId` / `requestId` / `userId` / `sessionId`。
- 响应头 `X-Request-Id` / `X-Trace-Id`。
- SkyWalking OAP/UI compose、Java Agent、Nginx `/skywalking/` 模板。

### P6 RAGForge 深度集成

- `RagForgeClient` 支持搜索、文本上传、删除文档。
- 简历保存/更新/删除异步同步 Personal KB。
- `rag_doc_id` 记录 RAGForge 文档版本。
- JD KB 搜索与岗位匹配增强。
- trace header 跨服务透传。
- Auth Gateway token exchange 调用 RAGForge。

### P7 LLM 升级 + 简历文件上传

- JobMatchAnalyzer LLM 化 + Structured Output，失败降级规则匹配。
- InterviewAnswerEvaluator LLM 化，结合 Interview KB。
- InterviewQuestionGenerator LLM 化，基于简历、岗位、技能缺口生成个性化题目。
- 简历文件上传解析：PDF / Word / Markdown，Apache Tika，10MB 限制。
- 岗位匹配页 JD 库浏览：搜索 + 一键填入分析。

### P8 Agent 架构升级

- `AgentLlmIntentRecognizer`：LLM 语义意图识别，失败降级到 regex/router。
- `AgentSupervisor`：专家 Agent 并行编排。
- 专家 Agent：Resume / JobMatch / Interview / Market / Critic。
- `ReActEngine`：最多 3 轮 Thought -> Action -> Observation，结果注入 system prompt。
- MCP JSON-RPC endpoint 作为可选能力，默认关闭。

## 下一阶段建议

### P9 联合部署与运维固化

- RAGForge + CareerMate + 中间件联合 docker-compose 或 k8s 一键部署。
- Auth Gateway / CareerMate / RAGForge 三服务统一 runbook。
- 云端完整 Playwright 回归纳入 CI。
- 生产 Redis、SkyWalking、Nginx SSE 配置验收脚本化。

### P10 薪资谈判官

- Salary KB。
- 薪资谈判脚本生成。
- 市场洞察与用户画像联动。

### P11 Agent Eval 与 Prompt 管理

- Prompt 版本后台化。
- Agent Eval case 管理、回归报告和质量门禁。
- 工具调用审批策略与高风险写入体验完善。

## 目标架构说明

`docs/design/CareerMate-architecture-v2.1.html` 描述目标架构。当前实现是阶段性 MVP + RAGForge/Auth Gateway 集成，不包含完整评测平台、Prompt 管理平台和联合一键部署。
