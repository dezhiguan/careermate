# CareerMate

CareerMate 是一个面向职业发展的智能助手平台，提供简历优化、面试辅导、职业规划等能力。本仓库为项目 monorepo，包含后端服务与前端应用。

## Documentation

- [AI Context](docs/AI_CONTEXT.md)
- [Database Design](docs/database-design.md)
- [CareerMate Deployment Guide](docs/deployment-careermate.md)
- [Architecture Design V2.1](docs/design/CareerMate-architecture-v2.1.html)
- [Prototype Design](docs/design/CareerMate-prototype-design.html)

部署端口说明：

- 本地开发默认使用 `8080`
- 生产部署建议使用 `18080`（避免与现有 RAGForge `8080` 冲突）

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.2.x、Maven |
| 持久层 | MyBatis-Plus 3.5.x、PostgreSQL 15、Flyway |
| 前端 | Vue 3 + Vite（后续迁入） |
| 容器化 | Docker、Docker Compose |

## 当前阶段说明

**阶段：已完成功能收口 + 真实 Qwen LLM 切换**

### 已收口并可用（本仓库当前范围）

| 模块 | 说明 |
|------|------|
| Agent 对话 | SSE 流式、工具调用、错误兜底（done/error + 前端 45s UI 超时） |
| 会话恢复 | 最近会话列表、切换会话、刷新后恢复消息 |
| 多轮上下文 | 会话历史注入 LLM prompt |
| 求职画像 | `career_profiles` 跨会话记忆 |
| 求职任务 | Agent 工具创建/查询/完成 + Dashboard 同步 |
| 简历文本管理 | 简历 CRUD、默认简历、上下文注入 |
| 岗位匹配 | JD 分析、匹配结果、Agent 工具 |
| 面试训练 | 会话创建、答题、Agent 工具 |
| Dashboard | 统计、建议、下一步任务 |
| Agent 工具卡片 | 业务工具展示名与页面跳转 |

### LLM Provider

- `LlmClient` 抽象 + `mock` / `deepseek` / `qwen` / `openai-compatible`
- **线上推荐**：`LLM_PROVIDER=qwen`，`LLM_MODEL=qwen-plus`，DashScope OpenAI 兼容 endpoint
- **本地/CI 默认**：`mock`（无需外部 API Key）
- 真实 Key **仅**配置在服务器本地 `.env.app`，**禁止**写入仓库、`.env.example`、README、GitHub Actions

### 下一阶段（Roadmap，本阶段不实现）

- **RAGForge JD 知识库集成**（岗位 JD RAG、共享知识检索）

### 基础设施（已完成）

- Spring Boot 后端、Flyway、Spring Security（single-user / jwt）
- 认证 API、前端 Auth、`src/api/http.js`
- 开发验证：`POST /api/debug/llm/chat`（**生产 profile 默认关闭**）

### 数据库初始化

本地 PostgreSQL 启动后，后端启动时 Flyway 会自动执行 migration。

Migration 文件位于：

```
backend/src/main/resources/db/migration/
```

首次启动将执行 `V1__init_user_core_tables.sql` 与 `V2__init_agent_runtime_tables.sql`，创建用户核心表、Agent 基础表及 `flyway_schema_history`。

## 本地启动后端

### 前置条件

- JDK 17
- Maven 3.8+
- PostgreSQL 15（本地安装，或通过 Docker Compose 仅启动 postgres）

### 启动 PostgreSQL（可选，使用 Docker）

```bash
docker compose up -d postgres
```

### 配置环境变量

```bash
cp .env.example .env
# 按需修改 .env 中的数据库连接信息
```

### 认证模式配置

```bash
SECURITY_MODE=single-user|jwt
JWT_SECRET=change-me-in-dev-only-change-me-in-dev-only
JWT_EXPIRATION_MS=86400000
SINGLE_USER_ID=1
SINGLE_USER_NAME=local-user
```

### LLM 配置

```bash
LLM_PROVIDER=mock
LLM_MODEL=mock-chat
LLM_API_KEY=
LLM_ENDPOINT=
LLM_TIMEOUT_MS=60000
LLM_MAX_TOKENS=4096
LLM_TEMPERATURE=0.7
```

- `mock`：本地开发默认，无需外部模型服务。
- `deepseek`：走 OpenAI-compatible 协议，默认 endpoint 为 `https://api.deepseek.com/v1`，默认 model 为 `deepseek-chat`。
- `qwen`：走 DashScope OpenAI-compatible 协议，默认 endpoint 为 `https://dashscope.aliyuncs.com/compatible-mode/v1`，默认 model 为 `qwen-plus`。
- `openai-compatible`：可对接兼容 `/chat/completions` 的模型网关，需显式配置 `LLM_MODEL` 与 `LLM_ENDPOINT`。

Qwen Plus:

```bash
LLM_PROVIDER=qwen
LLM_MODEL=qwen-plus
LLM_API_KEY=your_dashscope_api_key
LLM_ENDPOINT=https://dashscope.aliyuncs.com/compatible-mode/v1
```

DeepSeek:

```bash
LLM_PROVIDER=deepseek
LLM_MODEL=deepseek-chat
LLM_API_KEY=your-deepseek-api-key
LLM_ENDPOINT=https://api.deepseek.com/v1
```

Mock:

```bash
LLM_PROVIDER=mock
LLM_MODEL=mock-chat
```

### 编译并启动

```bash
cd backend
mvn spring-boot:run
```

或先打包再运行：

```bash
cd backend
mvn -DskipTests package
java -jar target/careermate-backend-0.1.0.jar
```

## Docker Compose 启动

在项目根目录执行：

```bash
docker compose up --build
```

这将启动 PostgreSQL 与后端服务。

## 健康检查

| 接口 | 说明 |
|------|------|
| `GET http://localhost:8080/api/health` | 应用健康检查（统一响应体） |
| `GET http://localhost:8080/actuator/health` | Spring Actuator 健康端点 |

## 认证接口

| 接口 | 说明 |
|------|------|
| `POST /api/auth/register` | 用户注册并返回 JWT |
| `POST /api/auth/login` | 用户登录并返回 JWT |
| `GET /api/auth/me` | 获取当前用户信息（single-user 或 JWT） |

## 线上 Qwen 验证（部署机执行）

在服务器 `/opt/careermate/backend/.env.app` 配置 `LLM_PROVIDER=qwen` 与 `LLM_API_KEY` 后：

```bash
curl -s -X POST "http://127.0.0.1:18080/api/debug/llm/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"请用一句话介绍 CareerMate"}'
```

> 生产环境默认关闭 `/api/debug/llm`（`careermate.debug.llm-api-enabled=false`）。若需临时验证，可短时设置 `CAREERMATE_DEBUG_LLM_API_ENABLED=true` 并重启后端，**勿**在响应或日志中输出 API Key。

也可直接通过 Agent 对话台发送：`请用一句话介绍 CareerMate`。

## LLM Debug 接口（仅开发验证）

> Debug API 仅用于本地验证；`SPRING_PROFILES_ACTIVE=prod` 时默认不注册该 Controller。

| 接口 | 说明 |
|------|------|
| `POST /api/debug/llm/chat` | 调用当前 `LlmClient` 做一次非流式 chat |

请求示例：

```json
{
  "message": "帮我分析简历"
}
```

预期（`mock` provider）：

- `code = 0`
- `data.provider = mock`
- `data.content` 有文本内容
- `data.latencyMs` 有值

## Agent Session API（mock stream + 基础持久化）

> 当前为 SSE 基础设施 + mock 流式对话 + 会话/消息/Trace 落库，**不是**完整 Agent Runtime。

| 接口 | 说明 |
|------|------|
| `POST /api/agent/sessions` | 创建 Agent 会话（落库 `agent_sessions` + `agent_task_states`） |
| `POST /api/agent/sessions/{sessionId}/messages/stream` | SSE 流式发送消息（持久化 user/agent 消息与 PLAN/MESSAGE/DONE/ERROR Trace） |
| `GET /api/agent/sessions/{sessionId}` | 查询会话详情与消息列表（按 `user_id` 隔离） |
| `GET /api/agent/sessions/{sessionId}/trace` | 查询会话 Trace 列表（按 `user_id` 隔离） |

示例：创建 session（需要认证）

```bash
curl -X POST http://localhost:8080/api/agent/sessions \\
  -H "Authorization: Bearer <token>"
```

示例：发起 SSE 流式请求（需要认证，single-user 可直接用；jwt 需带 token）

```bash
curl -N -X POST "http://localhost:8080/api/agent/sessions/<sessionId>/messages/stream" \\
  -H "Content-Type: application/json" \\
  -H "Authorization: Bearer <token>" \\
  -d '{\"message\":\"帮我分析简历\"}'
```

示例：查询会话与 Trace

```bash
curl http://localhost:8080/api/agent/sessions/<sessionId> \\
  -H "Authorization: Bearer <token>"

curl http://localhost:8080/api/agent/sessions/<sessionId>/trace \\
  -H "Authorization: Bearer <token>"
```

## 前端认证接入

- 前端目录：`frontend/careermate`
- API 基础地址环境变量：`VITE_API_BASE_URL`（默认 `http://localhost:8080`）
- 登录页面路由：`#/login`
- `single-user` 模式：前端可通过 `/api/auth/me` 自动恢复 `local-user`
- `jwt` 模式：需登录/注册获取 token，后续请求自动携带 `Authorization: Bearer <token>`
- 遇到 401：前端自动清理本地登录态并跳转 `#/login`

示例响应（`/api/health`）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "service": "careermate-backend",
    "version": "0.1.0"
  },
  "traceId": null,
  "timestamp": 1710000000000
}
```

## 目录结构

```
careermate/
├── README.md
├── docs/
│   ├── AI_CONTEXT.md
│   ├── database-design.md
│   └── design/
│       ├── CareerMate-architecture-v2.1.html
│       └── CareerMate-prototype-design.html
├── docker-compose.yml
├── .env.example
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/careermate/
│       │   ├── CareerMateApplication.java
│       │   ├── common/          # 公共组件（响应体、异常）
│       │   ├── config/          # 配置类
│       │   ├── health/          # 健康检查
│       │   ├── mapper/          # MyBatis-Plus Mapper
│       │   └── model/entity/    # 数据库实体
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           └── db/migration/    # Flyway migration 脚本
└── frontend/careermate/         # Vue 3 + Vite 前端
```

## 当前可体验流程（阶段 A）

在当前版本中，建议按下面流程体验：

1. 打开 `#/login`，使用 single-user 直接进入，或在 jwt 模式登录/注册。
2. 进入 `#/`（Agent 对话台）后，页面会自动创建会话（`POST /api/agent/sessions`）。
3. 输入消息并发送，页面通过 SSE 接收 `plan/token/message/done` 事件。
4. 右侧面板会展示：
   - 当前用户（`GET /api/auth/me` 认证上下文）
   - sessionId
   - stream 状态
   - event count
   - totalLatencyMs
5. 点击「刷新 Trace」可拉取后端持久化 trace（`GET /api/agent/sessions/{sessionId}/trace`）。

> 说明：当前是 mock stream + 基础持久化体验，不是完整 Agent Runtime。

## 新阶段节奏（可体验优先）

1. **阶段 A（进行中）**：补齐已完成接口的页面对接与交互完善。
2. **阶段 B**：最小可用简历模块（`resumes` + 上传/列表/详情/删除 + ResumeStudio 对接）。
3. **阶段 C**：最小可用岗位匹配模块（`job_posts` / `job_matches` + 关键词匹配）。
4. **阶段 D**：最小可用看板模块（`/api/dashboard` 汇总）。
5. **阶段 E**：GitHub CI/CD + 部署文档。
6. **阶段 F**：移动端 Web 响应式适配。
7. **阶段 G**：再深化 Agent Runtime / Memory / Prompt / Eval。
