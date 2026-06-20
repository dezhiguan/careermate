# CareerMate 部署说明（索引）

详细运维步骤见 **[deployment-careermate.md](deployment-careermate.md)**。跨 RAGForge / CareerMate 的最终部署 Runbook 仍以 RAGForge 仓库 `docs/deployment-migration-runbook.md` 为准。SkyWalking 见 **[skywalking-cloud-setup.md](skywalking-cloud-setup.md)**。认证配置见 **[SECURITY_AUTH.md](SECURITY_AUTH.md)**。

## 三层架构（生产）

| 层级 | 公网 IP | 内网 IP | 组件 |
|------|---------|---------|------|
| Server 1 数据层 | 8.163.30.216 | 172.25.90.183 | PostgreSQL、ES、Redis、RocketMQ |
| Server 2 入口层 | 8.163.63.222 | 172.19.40.32 | Nginx、RAGForge 前端、CareerMate 前端 |
| Server 3 应用层 | 8.138.191.228 | 172.25.90.184 | CareerMate backend、RAGForge backend |

请求链路：

```text
用户 → Server 2 Nginx
  /careermate/      → CareerMate 静态资源
  /careermate-api/  → Server 3 :18080/api/
```

## 本地部署

### 依赖

- JDK 21、Maven 3.8+
- PostgreSQL 15
- Node 18+（前端）

### 配置

```bash
cp .env.example .env
# 编辑 DB_URL、AUTH_GATEWAY_*、LLM_*、RAGFORGE_* 等（勿提交真实 Key）
```

Spring 实际读取：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`（文档中偶称 DATABASE_*，含义相同）。

当前认证主链路依赖 Auth Gateway，本地后端启动前请保证 `AUTH_GATEWAY_BASE_URL` 可访问；如只验证静态页面或前端构建，可不启动认证服务。

### 启动

```bash
docker compose up -d postgres   # 可选

cd backend && mvn spring-boot:run

cd frontend/careermate && npm run dev
```

- 后端：`http://localhost:8080`
- 前端：`http://localhost:5173`

## Server 3 首次初始化

在应用层服务器上，从 careermate 仓库根目录执行（仅创建用户、目录、权限和 `.env.app` 占位模板，**不启动后端**）：

```bash
sudo bash deploy/scripts/init-server3.sh
```

如果 GitHub Actions 的 `CAREERMATE_APP_USER` 不是 `root`，初始化时指定同一个部署用户，脚本会把它加入 `careermate` 组以便写入 release 和脚本目录：

```bash
sudo CAREERMATE_DEPLOY_USER=<CAREERMATE_APP_USER> bash deploy/scripts/init-server3.sh
```

然后手工编辑 `/opt/careermate/backend/.env.app`，填入 `DB_PASSWORD`、`JWT_SECRET`、`LLM_API_KEY` 等真实值，再安装 systemd unit 并部署。

## 云端部署（概要）

| 项 | 约定 |
|----|------|
| 入口 | `8.163.63.222`（Server 2） |
| 后端 | `8.138.191.228`（Server 3），端口 `18080` |
| 前端路径 | `/careermate/` |
| API 路径 | `/careermate-api/` → 反代到 `172.25.90.184:18080/api/` |
| SkyWalking UI | `/skywalking/` → `127.0.0.1:18088`（勿公网裸露 8088） |
| 密钥 | `/opt/careermate/backend/.env.app` 或 `/opt/shared/env/common.env`，**不入库** |

构建：

```bash
cd backend && mvn -DskipTests package
cd frontend/careermate && VITE_API_BASE_URL=/careermate-api VITE_BASE_PATH=/careermate/ npm run build
```

Nginx 片段：`deploy/nginx/careermate.locations.example`、`deploy/nginx/skywalking.locations.example`

## 环境变量（占位符）

| 变量 | 说明 |
|------|------|
| `SERVER_PORT` | 本地 `8080`，生产 `18080` |
| `DB_URL` | `jdbc:postgresql://172.25.90.183:5432/careermate_db` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 |
| `AUTH_GATEWAY_BASE_URL` | Auth Gateway 内网或本地地址 |
| `AUTH_GATEWAY_ISSUER` / `AUTH_GATEWAY_AUDIENCE` | JWT 校验 issuer/audience |
| `AUTH_GATEWAY_CLIENT_ID` | CareerMate 后端 OAuth client |
| `AUTH_GATEWAY_CLIENT_ASSERTION_PRIVATE_KEY` / `AUTH_GATEWAY_CLIENT_ASSERTION_KID` | client assertion 签名配置 |
| `AUTH_GATEWAY_REFRESH_COOKIE_*` | refresh cookie 名称、路径、domain、secure |
| `AUTH_EVENT_HMAC_SECRET` | Auth Gateway 事件 webhook HMAC 密钥 |
| `LLM_PROVIDER` | `mock` \| `qwen` \| `deepseek` \| `openai-compatible` |
| `LLM_MODEL` | 如 `qwen-plus`、`mock-chat` |
| `LLM_ENDPOINT` | Qwen: DashScope 兼容 endpoint |
| `LLM_API_KEY` | **仅服务器本地**，勿写入仓库 |
| `RAGFORGE_ENABLED` | 是否启用 RAGForge 调用 |
| `RAGFORGE_URL` | Server 3 上启用集成时用 `http://127.0.0.1:8080` |
| `RAGFORGE_JD_KB_ID` / `RAGFORGE_INTERVIEW_KB_ID` / `RAGFORGE_PERSONAL_KB_ID` | JD、面试、个人简历知识库 |
| `RAGFORGE_REQUESTED_AUDIENCE` / `RAGFORGE_REQUESTED_SCOPES` | token exchange 目标 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis，生产用于短信限流和 auth 事件吊销 |
| `TRACING_ENABLED` | Micrometer OTLP；生产用 SkyWalking 时建议 `false` |
| `SKYWALKING_AGENT_SERVICE_NAME` | 默认 `careermate-backend` |
| `SKYWALKING_COLLECTOR_BACKEND_SERVICE` | 如 `127.0.0.1:11800` 或 `skywalking-oap:11800` |
| `JAVA_TOOL_OPTIONS` | `-Xms512m -Xmx2g -javaagent:.../skywalking-agent.jar` 等 |
| `ALIYUN_SMS_*` | 手机号登录/密码重置短信配置 |
| `CAREERMATE_MCP_ENABLED` | MCP endpoint 开关，默认 `false` |

模板：`deploy/env/careermate-backend.env.example`、根目录 `.env.example`

## 验证

```bash
# Server 3 本机
curl -fsS http://127.0.0.1:18080/api/health

# 公网入口
curl -fsS http://8.163.63.222/careermate-api/health
curl -I http://8.163.63.222/skywalking/    # 需已部署 OAP/UI + Nginx
```

登录链路验证需使用真实 Auth Gateway：

```bash
curl -sS http://8.163.63.222/careermate-api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"<account>","password":"<password>"}'
```

业务接口验证需带返回的 access token：

```bash
curl -sS http://8.163.63.222/careermate-api/auth/me \
  -H "Authorization: Bearer <access_token>"
```

## 相关文档

- [deployment-careermate.md](deployment-careermate.md) — 目录、CI/CD、排障
- RAGForge 三层部署：`rag-forge/docs/deployment-three-tier.md`
- 统一迁移 Runbook：`rag-forge/docs/deployment-migration-runbook.md`
- [skywalking-cloud-setup.md](skywalking-cloud-setup.md)
- [ragforge-skywalking-integration.md](ragforge-skywalking-integration.md)
