# CareerMate

CareerMate 是一个面向求职场景的 AI Agent 工作台。它把简历管理、岗位机会、岗位匹配、面试训练、市场洞察、任务看板和对话式 Agent 串到同一个工作流里，并已接入统一认证、用户隔离、RAGForge 检索/知识库能力和 SkyWalking 观测部署模板。

本仓库是 monorepo：

```text
careermate/
├── backend/                 # Spring Boot API
├── frontend/careermate/     # Vue 3 Web App
├── docs/                    # 架构、认证、测试、部署、数据库
├── deploy/                  # Nginx、systemd、k8s、env、脚本模板
├── scripts/                 # 本地开发脚本
└── docker-compose.yml       # 本地 PostgreSQL + 后端示例
```

## 当前状态

当前代码已覆盖 P0-P8 主线能力，适合推到 GitHub 做演示、接力开发和部署验收。

- 统一认证：账号密码注册/登录、手机号验证码登录、密码重置、JWT Bearer 鉴权、Auth Gateway token exchange、session/password 事件吊销。
- 权限与隔离：所有 `/api/**` 业务接口默认要求认证，业务数据按 `CurrentUserContext.userId` 隔离，Agent 工具声明读写权限和风险等级。
- Agent 工作台：SSE 流式对话、会话恢复、Trace、工具卡片、任务/简历/岗位/面试联动。
- AI 能力：mock、Qwen、DeepSeek、OpenAI-compatible Provider；岗位匹配、面试评分、面试题生成已 LLM 化并支持降级。
- RAGForge 集成：JD KB 搜索、Interview KB 辅助、Personal KB 简历同步、跨服务 trace header 透传、token exchange。
- Multi-Agent 与 ReAct：Supervisor + 专家 Agent，非流式 ReAct 推理结果注入最终回复。
- 前端应用：登录/注册/短信/重置密码、机会、面试、小职对话、市场、我的、简历管理。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21、Spring Boot 3.5、Spring Security、Maven、MyBatis-Plus、Flyway |
| 数据库/缓存 | PostgreSQL 15、Redis（生产短信限流与认证事件吊销推荐启用） |
| 前端 | Vue 3、Vite、Vue Router、Vitest、Playwright |
| LLM | mock / qwen / deepseek / openai-compatible |
| RAG | RAGForge REST 集成 |
| 观测 | MDC traceId/requestId、Micrometer OTLP、SkyWalking Java Agent |

## 文档索引

| 文档 | 说明 |
|------|------|
| [docs/SECURITY_AUTH.md](docs/SECURITY_AUTH.md) | 认证、权限、JWT、Auth Gateway、事件吊销 |
| [docs/AI_CONTEXT.md](docs/AI_CONTEXT.md) | 给后续 AI/开发者快速恢复上下文 |
| [docs/ARCHITECTURE_SUMMARY.md](docs/ARCHITECTURE_SUMMARY.md) | 当前架构摘要 |
| [docs/ROADMAP.md](docs/ROADMAP.md) | 阶段完成情况与下一阶段 |
| [docs/database-design.md](docs/database-design.md) | 当前 Flyway 表结构概要 |
| [docs/TESTING.md](docs/TESTING.md) | 后端、前端、Playwright 测试说明 |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | 部署索引与环境变量 |
| [docs/deployment-careermate.md](docs/deployment-careermate.md) | 云端部署细节 |
| [docs/skywalking-cloud-setup.md](docs/skywalking-cloud-setup.md) | SkyWalking 部署与接入 |
| [docs/design/CareerMate-architecture-v2.1.html](docs/design/CareerMate-architecture-v2.1.html) | 目标架构设计，不等于全部当前实现 |

## 本地启动

### 1. 基础依赖

- JDK 21+
- Maven 3.8+
- Node.js 18+
- PostgreSQL 15
- Redis 可选；生产建议开启，开发默认可用内存存储
- Auth Gateway：当前认证主链路依赖 `AUTH_GATEWAY_BASE_URL` 指向可用认证服务

### 2. 准备配置

```bash
cp .env.example .env
```

重点检查：

```properties
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://localhost:5432/careermate_db
DB_USERNAME=careermate
DB_PASSWORD=change_me

AUTH_GATEWAY_BASE_URL=http://localhost:8090
AUTH_GATEWAY_ISSUER=https://auth.careermate.cn
AUTH_GATEWAY_AUDIENCE=careermate-api

LLM_PROVIDER=mock
LLM_MODEL=mock-chat
RAGFORGE_ENABLED=false
```

真实密钥只放本地 `.env` 或服务器 `/opt/careermate/backend/.env.app`，不要提交到仓库。

### 3. 数据库

```bash
docker compose up -d postgres
# 或手动创建：
createdb careermate_db
```

Flyway 会在后端启动时自动应用 `backend/src/main/resources/db/migration/`。

### 4. 后端

推荐脚本：

```bash
scripts/dev-start.sh
```

脚本会切换 JDK 21、加载 `.env`、构建后端，并用 `dev` profile 启动。也可以手动启动：

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```bash
curl -i http://localhost:8080/api/health
```

### 5. 前端

```bash
cd frontend/careermate
npm install
npm run dev
```

默认访问：

```text
http://localhost:5173
```

本地 dev 默认通过 Vite `/api` 代理访问后端；生产构建可设置：

```bash
VITE_API_BASE_URL=/careermate-api VITE_BASE_PATH=/careermate/ npm run build
```

## 主要 API

除健康检查、登录注册、短信、密码重置和认证事件 webhook 外，所有 `/api/**` 接口都需要：

```http
Authorization: Bearer <access_token>
```

| 模块 | 路径 |
|------|------|
| 健康检查 | `GET /api/health` |
| 认证 | `POST /api/auth/register`、`POST /api/auth/login`、`GET/PUT /api/auth/me` |
| 手机号登录 | `POST /api/auth/sms/send`、`POST /api/auth/mobile/login` |
| 密码重置 | `POST /api/auth/password-reset/sms/send`、`POST /api/auth/password-reset/confirm` |
| Auth 事件 | `POST /api/v1/events/session-revoked`、`POST /api/v1/events/password-changed` |
| Agent | `/api/agent/sessions`、`/messages/stream`、`/trace` |
| Workspace | `/api/workspace/**` |
| 简历 | `/api/resumes/**`、`/api/resume-version/**` |
| 岗位匹配 | `/api/job-matches/**`、`/api/opportunity/**` |
| 面试 | `/api/interview/**`、`/api/interview-sessions/**` |
| 市场洞察 | `/api/market/**` |
| 看板/任务/画像 | `/api/dashboard/**`、`/api/tasks/**`、`/api/profile/**` |
| MCP | `POST /api/mcp`，默认关闭，需 `CAREERMATE_MCP_ENABLED=true` |

## 测试

```bash
# 后端
cd backend
mvn test

# 前端构建
cd frontend/careermate
npm run build

# 前端单测
npm run test:unit

# Playwright
npm run test:e2e
```

认证相关 E2E：

```bash
cd frontend/careermate
npm run test:e2e:auth:local
```

更多命令见 [docs/TESTING.md](docs/TESTING.md)。

## 部署概要

生产推荐三层结构：

| 层级 | 组件 |
|------|------|
| 数据层 | PostgreSQL、Redis、ES、RocketMQ |
| 入口层 | Nginx、CareerMate 前端、RAGForge 前端 |
| 应用层 | CareerMate backend、RAGForge backend、SkyWalking Agent |

CareerMate 约定：

| 项 | 值 |
|----|-----|
| 后端端口 | `18080` |
| 前端路径 | `/careermate/` |
| API 路径 | `/careermate-api/` |
| 生产密钥 | `/opt/careermate/backend/.env.app` 或共享 env |
| SkyWalking UI | `/skywalking/` |

Agent 对话使用 SSE，Nginx / 网关必须关闭 buffering：

```nginx
location /api/agent/ {
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 600s;
    gzip off;
    chunked_transfer_encoding on;
}
```

部署细节见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) 和 [docs/deployment-careermate.md](docs/deployment-careermate.md)。

## 安全约定

- 不提交真实 `LLM_API_KEY`、`AUTH_EVENT_HMAC_SECRET`、`JWT_SECRET`、数据库密码、短信密钥、RSA 私钥。
- 后端只接受 Auth Gateway 签发且 issuer/audience 匹配的 JWT。
- 前端只把 access token 放在 localStorage；refresh token 由后端写入 HttpOnly cookie。
- 业务层使用当前登录用户上下文，不信任前端传入的 userId。
- 日志不记录完整简历、JD、prompt、模型回复和密钥。

## License

当前按团队仓库策略管理；开源许可证以后续仓库治理为准。
