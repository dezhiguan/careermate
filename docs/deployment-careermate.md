# CareerMate Deployment Guide

> **最终部署 Runbook（按真实执行顺序）**：见 RAGForge 仓库 `docs/deployment-migration-runbook.md`（章节 A–J：Server1 检查 → Server3/2 初始化 → 连通性 → 部署顺序 → Smoke test → 回滚 → CI Secrets）。

## 1. Deployment Topology (Three-Tier)

| Layer | Public IP | Private IP | Services |
|-------|-----------|------------|----------|
| Server 1 Data | 8.163.30.216 | 172.25.90.183 | PostgreSQL, ES, Redis, RocketMQ — **unchanged** |
| Server 2 Ingress | 8.163.63.222 | 172.19.40.32 | Nginx, RAGForge frontend, CareerMate frontend |
| Server 3 App | 8.138.191.228 | 172.25.90.184 | CareerMate backend (:18080), RAGForge backend (:8080) |

Request flow:

```text
User → Server 2 Nginx (8.163.63.222)
  /careermate/      → static files at /opt/rag-forge/frontend/dist/careermate/
  /careermate-api/  → http://172.25.90.184:18080/api/

Server 3 backend → Server 1 data (172.25.90.183:5432)
```

> **Note:** CareerMate backend no longer runs on the ingress server. The legacy single-server layout is deprecated.

## 2. Deployment Principles

- Do not override existing RAGForge routes during CareerMate rollout.
- Keep strict isolation between RAGForge and CareerMate:
  - path isolation
  - process isolation
  - config isolation
- Do not store real secrets in repository.
- Keep sensitive values only on Server 3 local files:
  - `/opt/careermate/backend/.env.app`
  - local systemd files on Server 3

## 3. Build Artifacts

- Backend JAR: `backend/target/careermate-backend-0.1.0.jar`
- Frontend dist: `frontend/careermate/dist`
- `frontend/careermate/dist/` is a build artifact and should not be committed
- `backend/target/` is a build artifact and should not be committed

Build commands:

```bash
cd backend
mvn -DskipTests package

cd ../frontend/careermate
VITE_API_BASE_URL=/careermate-api VITE_BASE_PATH=/careermate/ npm run build
```

## 3.1 Production Profile

- Production uses `SPRING_PROFILES_ACTIVE=prod`.
- `prod` profile loads `backend/src/main/resources/application-prod.yml`.
- `application-prod.yml` contains no real secrets.
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` must be injected from server-local:
  - `/opt/careermate/backend/.env.app` (on **Server 3**)

## 4. Server Directories

### Server 3 (app layer) — GitHub CI/CD backend

```text
/opt/careermate/
  current -> releases/<git-sha>/          # atomic switch per deploy
  releases/
    <git-sha>/
      backend/app.jar
  backend/
    .env.app                              # persistent secrets (not in releases)
  logs/
  scripts/
    deploy-from-github.sh
    rollback-careermate.sh
```

### Server 2 (ingress layer) — frontend static files

```text
/opt/rag-forge/frontend/dist/careermate/   # host bind-mount
  → container: /usr/share/nginx/html/careermate/
```

Do not change RAGForge paths outside the `careermate/` subdirectory.

## 5. Environment Variables

Use placeholder templates only:

- `deploy/env/careermate-backend.env.example`
- `deploy/env/careermate-frontend.env.example`

Production notes:

- local dev backend can run on `8080`
- production backend runs on **Server 3** port `18080`
- `DB_URL=jdbc:postgresql://172.25.90.183:5432/careermate_db`
- `RAGFORGE_URL=http://127.0.0.1:8080` when RAGForge integration is enabled on Server 3
- real `.env.app` must stay on Server 3 and must not be committed

### 5.1 SkyWalking 链路追踪（生产推荐，可浏览器查看 Trace）

Server 3 部署 OAP + UI（或与 RAGForge 共用），CareerMate 挂 Java Agent。完整步骤见 **`docs/skywalking-cloud-setup.md`**。

快速索引：

| 组件 | 说明 |
|------|------|
| Compose | `deploy/skywalking/docker-compose.skywalking.yml` |
| 启动脚本 | `deploy/scripts/start-skywalking.sh` |
| Agent 安装 | `deploy/scripts/install-skywalking-agent.sh` → `/opt/skywalking-agent` |
| Nginx | `deploy/nginx/skywalking.locations.example` → `http://8.163.63.222/skywalking/` |
| systemd | `deploy/systemd/careermate-backend.service.example`（Server 3，`JAVA_TOOL_OPTIONS` 含 `-Xms512m -Xmx2g` + javaagent） |

生产 `.env.app` 建议（无密钥）：

```bash
SKYWALKING_AGENT_SERVICE_NAME=careermate-backend
SKYWALKING_COLLECTOR_BACKEND_SERVICE=127.0.0.1:11800
JAVA_TOOL_OPTIONS=-Xms512m -Xmx2g -javaagent:/opt/skywalking-agent/skywalking-agent.jar -Dskywalking.agent.service_name=careermate-backend -Dskywalking.collector.backend_service=127.0.0.1:11800
TRACING_ENABLED=false
```

验收：UI 中可见 `careermate-backend`，Agent 对话后 Trace 列表有新记录；`curl -i http://127.0.0.1:18080/api/health` 含 `X-Trace-Id`。

RAGForge 共用 OAP：见 `docs/ragforge-skywalking-integration.md`。

### 5.2 分布式追踪（OTLP，可选 Collector）

在 `/opt/careermate/backend/.env.app` 增加（模板见 `.env.example`）：

```bash
TRACING_ENABLED=true
TRACING_SAMPLING_PROBABILITY=1.0
OTEL_SERVICE_NAME=careermate-backend
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces
```

说明：

- 使用 Spring Boot 3.2 Micrometer Tracing + OpenTelemetry OTLP；日志含 `traceId` / `spanId` / `requestId` / `userId` / `sessionId`。
- HTTP 响应头：`X-Request-Id`、`X-Trace-Id`；调用 RAGForge 时透传 `traceparent` / `tracestate`。
- **不强制**生产部署 OTLP Collector；未部署时仍可本地日志排查，仅无集中式 trace UI。
- 可选 Collector 示例：`deploy/otel/otel-collector-config.yaml`（Docker 侧车或独立容器）。

验证：

```bash
curl -i http://127.0.0.1:18080/api/health
# 预期响应头含 X-Request-Id、X-Trace-Id
```

RAGForge 侧对接见 `docs/ragforge-tracing-integration.md`。

### 5.3 LLM（阿里云百炼 Qwen）

推荐在 Server 3 `/opt/careermate/backend/.env.app` 配置（占位符模板见 `deploy/env/careermate-backend.env.example`）：

```bash
LLM_PROVIDER=qwen
LLM_MODEL=qwen-plus
LLM_ENDPOINT=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_API_KEY=your_dashscope_api_key
```

说明：

- 使用 DashScope **OpenAI 兼容** Chat Completions：`{endpoint}/chat/completions`，Header `Authorization: Bearer <API_KEY>`。
- 官方文档：[OpenAI 兼容接口](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope)
- **禁止**将真实 `LLM_API_KEY` 写入仓库、GitHub Actions、前端或 README。
- 生产 `SPRING_PROFILES_ACTIVE=prod` 时默认关闭 `/api/debug/llm`。
- 本地与 CI 可继续使用 `LLM_PROVIDER=mock`。

部署后验证（需临时开启 debug 或直接使用 Agent 对话台）：

```bash
curl -s -X POST "http://127.0.0.1:18080/api/debug/llm/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"请用一句话介绍 CareerMate"}'
```

预期：`code=0`，`data.provider=qwen`，`data.content` 为模型生成文本（非 mock 固定话术）。

### 5.4 线上仍是 Mock 回复？排查清单

若 Agent 仍返回「这是 Mock CareerMate 回复…」，说明进程内 **`LLM_PROVIDER` 实际为 `mock`**（或未传入，走 `application.yml` 默认值）。

在 **Server 3** 执行：

```bash
sudo grep -E '^LLM_' /opt/careermate/backend/.env.app
sudo grep EnvironmentFile /etc/systemd/system/careermate-backend.service
PID=$(pgrep -f 'careermate.*app.jar' | head -1)
sudo tr '\0' '\n' < /proc/$PID/environ | grep -E '^LLM_'
curl -s http://127.0.0.1:18080/api/health | python3 -m json.tool
```

常见原因：

| 现象 | 原因 | 处理 |
|------|------|------|
| `.env.app` 仍是 `LLM_PROVIDER=mock` | 未改或未保存 | 改为 `qwen` 并 **restart** |
| 改了文件但未重启 | 旧进程仍用旧环境 | `sudo systemctl restart careermate-backend` |
| 变量名写错 | 必须用 `LLM_PROVIDER` | 对照 `application.yml` 占位符 |
| systemd 未配置 `EnvironmentFile` | 进程读不到 `.env.app` | 安装 `deploy/systemd/careermate-backend.service.example` |

修改 `.env.app` 后务必：

```bash
sudo systemctl daemon-reload
sudo systemctl restart careermate-backend
```

## 6. Nginx Routing Plan

### Plan A (current)

Keep RAGForge routes unchanged and use:

- CareerMate frontend: `/careermate/`
- CareerMate API: `/careermate-api/` → `http://172.25.90.184:18080/api/`
- Nginx runs on **Server 2** only
- Merge location snippet: `deploy/nginx/careermate.locations.example`
- Always backup current Nginx config before modification
- Minimum required SSE proxy options:
  - `proxy_http_version 1.1`
  - `proxy_buffering off`
  - `proxy_read_timeout 300s`
  - `proxy_send_timeout 300s`
- `/careermate-api/` must proxy to Server 3 private IP (`172.25.90.184:18080`), not `127.0.0.1`

### Plan B (future migration)

- CareerMate frontend: `/`
- CareerMate API: `/api/`
- Move RAGForge to `/ragforge/` and `/ragforge-api/`

Current recommendation: **Plan A only**.

## 7. Database Connectivity

Use private network PostgreSQL on Server 1:

- `DB_URL=jdbc:postgresql://172.25.90.183:5432/careermate_db`

Initialization template:

- `deploy/sql/init-careermate-db.sql.example`

## 8. Deployment Steps

完整顺序见 Runbook 章节 E：

1. Server 1 — 仅检查（PostgreSQL / ES / Redis / RocketMQ）
2. Server 3 — RAGForge backend
3. Server 3 — CareerMate backend
4. Server 2 — RAGForge frontend + Nginx
5. Server 2 — CareerMate frontend
6. 公网 smoke test

### 8.1 One-time server bootstrap (manual)

**Server 1 (data):** 保持不动；确认中间件已启动，安全组仅允许 `172.25.90.184` 访问数据端口（见 Runbook 章节 A）。

**Server 3 (app):**

1. Run initialization script (directories, user, permissions, env template only — does **not** start backend):
   ```bash
   sudo bash deploy/scripts/init-server3.sh
   ```
   If GitHub Actions deploys with a non-root `CAREERMATE_APP_USER`, pass the same user during bootstrap so it can write releases:
   ```bash
   sudo CAREERMATE_DEPLOY_USER=<CAREERMATE_APP_USER> bash deploy/scripts/init-server3.sh
   ```
2. Edit `/opt/careermate/backend/.env.app` with real secrets (`DB_PASSWORD`, `JWT_SECRET`, `LLM_API_KEY`, etc.; never commit).
3. Install systemd unit from `deploy/systemd/careermate-backend.service.example` as `/etc/systemd/system/careermate-backend.service`.
4. `systemctl daemon-reload && systemctl enable careermate-backend`
5. Configure GitHub Secrets (section 13).

**Server 2 (ingress):** 见 Runbook 章节 C。

1. `mkdir -p /opt/rag-forge/frontend/dist/careermate/`
2. 确认 Nginx 反代：`/api/` → `172.25.90.184:8080`；`/careermate-api/` → `172.25.90.184:18080/api/`；`/careermate/` → 静态目录
3. `docker compose -f docker-compose-ingress.yml up -d`
4. 连通性：`nc -vz -w 3 172.25.90.184 8080` 和 `18080`

**Server 3 RAGForge 敏感配置：**

- `/opt/rag-forge/docker-compose.override.yml`（DashScope Key、DB 密码等，**不入库**）

### 8.2 Ongoing deploy via GitHub Actions (recommended)

1. Push to `main` (or run workflow manually).
2. GitHub Actions builds backend JAR and frontend `dist`.
3. **Server 3:** backend JAR uploaded to `/opt/careermate/releases/${GITHUB_SHA}/backend/app.jar`; `deploy-from-github.sh` switches `current` and restarts systemd.
4. **Server 2:** frontend `dist` rsynced to `/opt/rag-forge/frontend/dist/careermate/`.
5. Workflow smoke tests pass, or the job fails without deleting previous releases.

### 8.3 Manual deploy (fallback)

**Backend (Server 3):**

```bash
# Upload app.jar to /opt/careermate/releases/<sha>/backend/app.jar
sudo bash /opt/careermate/scripts/deploy-from-github.sh <release-sha>
```

**Frontend (Server 2):**

```bash
rsync -avz --delete frontend/careermate/dist/ \
  root@8.163.63.222:/opt/rag-forge/frontend/dist/careermate/
```

## 9. Verification

```bash
# Server 3
curl -fsS http://127.0.0.1:18080/api/health

# Server 2 internal
curl -fsS http://172.25.90.184:18080/api/health

# Public ingress
curl -fsS http://8.163.63.222/careermate-api/health
```

Functional checks:

- login/enter flow works
- AgentChat SSE stream works (`plan/token/message/done`)

## 10. Rollback

- Releases under `/opt/careermate/releases/` are never deleted by CI/CD.
- If a deploy fails, GitHub Actions stops; `current` may still point at the failed release—roll back manually.

### Backend rollback (Server 3 only)

```bash
ls -1 /opt/careermate/releases/
sudo bash /opt/careermate/scripts/rollback-careermate.sh /opt/careermate/releases/<previous-sha>
```

### Frontend rollback (Server 2)

Rsync a previous `dist/` snapshot back to `/opt/rag-forge/frontend/dist/careermate/`, or restore from a local backup.

Verify:

```bash
curl -fsS http://127.0.0.1:18080/api/health
curl -fsS http://8.163.63.222/careermate-api/health
```

## 11. Risk Notes

- Server 2 `80` is occupied by `ragforge-nginx` — only static files and proxy, no backend containers.
- Server 3 hosts both RAGForge (:8080) and CareerMate (:18080) backends.
- Data server disk usage should be monitored.
- Do not reuse `/api/` for CareerMate; use `/careermate-api/` to avoid route conflicts.

## 12. Template Files

- Nginx template: `deploy/nginx/careermate.conf.example`
- Nginx location snippet: `deploy/nginx/careermate.locations.example`
- Backend env template: `deploy/env/careermate-backend.env.example`
- Frontend env template: `deploy/env/careermate-frontend.env.example`
- DB init SQL template: `deploy/sql/init-careermate-db.sql.example`
- Server 3 init script: `deploy/scripts/init-server3.sh`
- GitHub deploy script: `deploy/scripts/deploy-from-github.sh` (Server 3)
- Rollback script: `deploy/scripts/rollback-careermate.sh` (Server 3)
- systemd template: `deploy/systemd/careermate-backend.service.example` (Server 3)
- GitHub Actions workflow: `.github/workflows/careermate-deploy.yml`

## 13. GitHub CI/CD

### 13.1 Workflow

File: `.github/workflows/careermate-deploy.yml`

Triggers:

- `push` to `main`
- `workflow_dispatch` (manual)

Jobs:

| Job | Purpose |
|-----|---------|
| `backend-build` | `mvn -B -DskipTests package` |
| `frontend-build` | `npm ci` + production build |
| `deploy-app` | Upload backend to **Server 3**, run `deploy-from-github.sh` |
| `deploy-ingress` | Upload frontend to **Server 2** |
| `smoke-test` | Public health + frontend entry |
| `postdeploy-playwright` | Cloud E2E smoke |

### 13.2 GitHub Secrets

Configure in repository **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `CAREERMATE_APP_HOST` | Server 3 host (e.g. `8.138.191.228`) |
| `CAREERMATE_APP_USER` | SSH user for Server 3 |
| `CAREERMATE_APP_SSH_KEY` | Private key for Server 3 |
| `CAREERMATE_APP_PORT` | SSH port (optional; default `22`) |
| `CAREERMATE_INGRESS_HOST` | Server 2 host (e.g. `8.163.63.222`) |
| `CAREERMATE_INGRESS_USER` | SSH user for Server 2 |
| `CAREERMATE_INGRESS_SSH_KEY` | Private key for Server 2 |
| `CAREERMATE_INGRESS_PORT` | SSH port (optional; default `22`) |

**Legacy secrets** (deprecated, single-server deploy):

| Secret | Status |
|--------|--------|
| `CAREERMATE_DEPLOY_HOST` | Replaced by `CAREERMATE_APP_HOST` + `CAREERMATE_INGRESS_HOST` |
| `CAREERMATE_DEPLOY_USER` | Replaced by `CAREERMATE_APP_USER` + `CAREERMATE_INGRESS_USER` |
| `CAREERMATE_DEPLOY_SSH_KEY` | Replaced by dual keys above |
| `CAREERMATE_DEPLOY_PORT` | Replaced by `CAREERMATE_APP_PORT` + `CAREERMATE_INGRESS_PORT` |

Do **not** put database passwords, `JWT_SECRET`, or LLM API keys in the repository or in these secrets.

### 13.3 Server prerequisites

**Server 3:**

| Item | Location / command |
|------|-------------------|
| Backend env | `/opt/careermate/backend/.env.app` |
| systemd unit | `/etc/systemd/system/careermate-backend.service` |
| Backend port | `18080` |
| Deploy scripts | `/opt/careermate/scripts/` |
| SSH access | Deploy user can `sudo systemctl restart careermate-backend` |

**Server 2:**

| Item | Location |
|------|----------|
| Nginx routes | `/careermate/`, `/careermate-api/` |
| Frontend static | `/opt/rag-forge/frontend/dist/careermate/` |

### 13.4 Release layout (Server 3)

```text
/opt/careermate/releases/<git-sha>/
  backend/app.jar
/opt/careermate/current -> /opt/careermate/releases/<git-sha>
```

Frontend is deployed directly to Server 2, not stored in Server 3 releases.

### 13.5 Prohibited

- Do not commit `.env.app` or production secrets.
- Do not store DB passwords / JWT / LLM keys in repo files.
- Do not change RAGForge deploy paths or port `8080`.
- Do not modify production PostgreSQL / Redis / Elasticsearch from CI.
- Do not delete old releases automatically in this phase.

## 14. 本地 E2E 验收（Playwright）

在 `frontend/careermate` 目录执行 E2E（本机 Google Chrome headed）。云端完整用户场景：`npm run test:e2e:cloud:full`（要求 `SECURITY_MODE=jwt` 且 `JWT_SECRET`≥32 字节）。

## 15. Final Deployment Runbook & RAGForge CI Secrets

**主 Runbook**：RAGForge 仓库 `docs/deployment-migration-runbook.md`

包含：Server1 检查、Server3/2 初始化、连通性探测、部署顺序、Smoke test、回滚、Secrets 清单、`/data/files` 迁移。

**RAGForge GitHub Secrets**（在 `rag-forge` 仓库配置，勿写真实值）：

| Secret | 说明 |
|--------|------|
| `RAGFORGE_INGRESS_HOST` | Server 2 SSH 目标 |
| `RAGFORGE_APP_HOST` | Server 3 SSH 目标 |
| `RAGFORGE_INGRESS_SSH_KEY` | Server 2 私钥 |
| `RAGFORGE_APP_SSH_KEY` | Server 3 私钥 |
| `RAGFORGE_INGRESS_KNOWN_HOSTS` | `ssh-keyscan 8.163.63.222` |
| `RAGFORGE_APP_KNOWN_HOSTS` | `ssh-keyscan 8.138.191.228` |
| `RAGFORGE_INGRESS_DIR` | 可选，默认 `/opt/rag-forge` |
| `RAGFORGE_APP_DIR` | 可选，默认 `/opt/rag-forge` |
