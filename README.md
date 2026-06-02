# CareerMate

CareerMate 是一个面向职业发展的智能助手平台，提供简历优化、面试辅导、职业规划等能力。本仓库为项目 monorepo，包含后端服务与前端应用。

## Documentation

- [AI Context](docs/AI_CONTEXT.md)
- [Database Design](docs/database-design.md)
- [Architecture Design V2.1](docs/design/CareerMate-architecture-v2.1.html)
- [Prototype Design](docs/design/CareerMate-prototype-design.html)

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.2.x、Maven |
| 持久层 | MyBatis-Plus 3.5.x、PostgreSQL 15、Flyway |
| 前端 | Vue 3 + Vite（后续迁入） |
| 容器化 | Docker、Docker Compose |

## 当前阶段说明

**阶段五：LLM 抽象层（基础能力）**

当前已完成：

- Spring Boot 后端基础骨架
- 统一响应与异常处理
- 健康检查接口
- Vue 前端页面导入
- Flyway migration 基础
- 用户核心表 `users` / `user_profiles` / `security_audit_logs`
- 对应实体类与 MyBatis-Plus Mapper
- Spring Security 接入
- single-user / jwt 双模式
- 认证接口：`POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`
- JWT 生成与校验
- `CurrentUserContext` 用户上下文注入
- 注册 / 登录审计日志写入 `security_audit_logs`
- 前端统一请求层 `src/api/http.js`（fetch）
- 前端认证状态管理 `src/stores/authStore.js`
- 登录页 `#/login` 与登录/注册交互
- 路由守卫（未认证自动跳转登录页）
- single-user 模式可直接通过 `/api/auth/me` 进入应用
- LLM 抽象接口 `LlmClient` 与 provider 路由配置
- `mock` / `deepseek` / `openai-compatible` 三种 provider 选择
- 开发验证接口：`POST /api/debug/llm/chat`（需认证）

本阶段前端已优先对接已完成后端接口：

- Auth 当前用户展示
- Agent 对话台 SSE mock stream（会话落库 + 消息/Trace 持久化）

当前仍未对接（继续保留前端 mock 数据）：

- 简历上传/解析页面
- 岗位匹配页面
- 面试训练页面
- 求职看板页面

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
LLM_API_KEY=your-dashscope-api-key
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

## LLM Debug 接口（仅开发验证）

> Debug API 仅用于本地验证，生产环境建议关闭。

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

## 后续阶段计划

1. ~~**数据库迁移**：创建用户核心表，开启 Flyway migration~~（阶段二已完成）
2. ~~**认证授权**：接入 Spring Security，实现登录注册~~（阶段三基础能力已完成）
3. ~~**前端迁入**：将 Vue 3 前端迁移至 `frontend/` 目录~~（已完成）
4. ~~**前端 Auth 接入**：登录页、Auth Store、路由守卫~~（阶段四基础能力已完成）
5. ~~**LLM 抽象层**：实现 provider 抽象与 debug 验证接口~~（阶段五基础能力已完成）
6. **Agent Runtime**：实现智能体运行时与 Tool Registry
7. **LLM / RAG 集成**：对接大语言模型与 RAGForge 知识库
7. **业务表扩展**：简历、岗位、面试等剩余业务表
