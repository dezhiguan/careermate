# CareerMate Deployment Guide

## 1. Deployment Topology

- Ingress server (public entry):
  - Nginx
  - RAGForge frontend/backend (already running)
  - CareerMate frontend/backend (to be added)
- Data server (private data services):
  - PostgreSQL / PgVector
  - Elasticsearch
  - Redis
- Cross-server access should use private network.

## 2. Deployment Principles

- Do not override existing RAGForge services during CareerMate rollout.
- Keep strict isolation between RAGForge and CareerMate:
  - path isolation
  - process isolation
  - config isolation
- Do not store real secrets in repository.
- Keep sensitive values only on server local files:
  - `/opt/careermate/backend/.env.app`
  - local Nginx/systemd files on server

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
  - `/opt/careermate/backend/.env.app`

## 4. Server Directories

Recommended ingress server layout (GitHub CI/CD):

```text
/opt/careermate/
  current -> releases/<git-sha>/          # atomic switch per deploy
  releases/
    <git-sha>/
      backend/app.jar
      frontend/dist/
  backend/
    .env.app                              # persistent secrets (not in releases)
  logs/
  scripts/
    deploy-from-github.sh
    rollback-careermate.sh
```

Nginx static files (Plan A, `ragforge-nginx` container):

- Container path: `/usr/share/nginx/html/careermate/`
- Host bind-mount (sync target for deploy scripts): `/opt/rag-forge/frontend/dist/careermate/`
- Do not change RAGForge paths outside the `careermate/` subdirectory.

## 5. Environment Variables

Use placeholder templates only:

- `deploy/env/careermate-backend.env.example`
- `deploy/env/careermate-frontend.env.example`

Production notes:

- local dev backend can run on `8080`
- production backend should run on `18080` to avoid RAGForge `8080` conflict
- real `.env.app` must stay on server and must not be committed

### 5.1 SkyWalking 链路追踪（生产推荐，可浏览器查看 Trace）

入口服务器部署 OAP + UI，CareerMate 挂 Java Agent。完整步骤见 **`docs/skywalking-cloud-setup.md`**。

快速索引：

| 组件 | 说明 |
|------|------|
| Compose | `deploy/skywalking/docker-compose.skywalking.yml` |
| 启动脚本 | `deploy/scripts/start-skywalking.sh` |
| Agent 安装 | `deploy/scripts/install-skywalking-agent.sh` → `/opt/skywalking-agent` |
| Nginx | `deploy/nginx/skywalking.locations.example` → `http://8.163.63.222/skywalking/` |
| systemd | `deploy/systemd/careermate-backend.service.example`（`JAVA_TOOL_OPTIONS` + javaagent） |

生产 `.env.app` 建议（无密钥）：

```bash
SKYWALKING_AGENT_SERVICE_NAME=careermate-backend
SKYWALKING_COLLECTOR_BACKEND_SERVICE=127.0.0.1:11800
JAVA_TOOL_OPTIONS=-javaagent:/opt/skywalking-agent/skywalking-agent.jar -Dskywalking.agent.service_name=careermate-backend -Dskywalking.collector.backend_service=127.0.0.1:11800
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

推荐在服务器 `/opt/careermate/backend/.env.app` 配置（占位符模板见 `deploy/env/careermate-backend.env.example`）：

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
# 可选：临时 CAREERMATE_DEBUG_LLM_API_ENABLED=true 后
curl -s -X POST "http://127.0.0.1:18080/api/debug/llm/chat" \
  -H "Content-Type: application/json" \
  -d '{"message":"请用一句话介绍 CareerMate"}'
```

预期：`code=0`，`data.provider=qwen`，`data.content` 为模型生成文本（非 mock 固定话术）。

### 5.4 线上仍是 Mock 回复？排查清单

若 Agent 仍返回「这是 Mock CareerMate 回复…」，说明进程内 **`LLM_PROVIDER` 实际为 `mock`**（或未传入，走 `application.yml` 默认值）。

在服务器执行：

```bash
# 1) 环境文件是否存在、是否含 qwen
sudo grep -E '^LLM_' /opt/careermate/backend/.env.app

# 2) systemd 是否加载该文件
sudo grep EnvironmentFile /etc/systemd/system/careermate-backend.service

# 3) 运行中 Java 进程是否带上 LLM 变量（应看到 LLM_PROVIDER=qwen）
PID=$(pgrep -f 'careermate.*app.jar' | head -1)
sudo tr '\0' '\n' < /proc/$PID/environ | grep -E '^LLM_'

# 4) 健康检查（部署含 llmProvider 字段的版本后）
curl -s http://127.0.0.1:18080/api/health | python3 -m json.tool
# 期望: "llmProvider": "qwen", "llmApiKeyConfigured": "true"
```

常见原因：

| 现象 | 原因 | 处理 |
|------|------|------|
| `.env.app` 仍是 `LLM_PROVIDER=mock` | 未改或未保存 | 改为 `qwen` 并 **restart** |
| 改了文件但未重启 | 旧进程仍用旧环境 | `sudo systemctl restart careermate-backend` |
| 变量名写错 | 必须用 `LLM_PROVIDER`，不是 `CAREERMATE_LLM_PROVIDER` | 对照 `application.yml` 占位符 |
| 大小写错误 | 代码只识别小写 `qwen` | 勿写 `Qwen` / `QWEN` |
| systemd 未配置 `EnvironmentFile` | 进程读不到 `.env.app` | 安装 `deploy/systemd/careermate-backend.service.example` |
| 占位符 Key 未替换 | `your_dashscope_api_key` 会导致 Qwen 认证失败（但不会回到 mock） | 填入真实百炼 Key |

修改 `.env.app` 后务必：

```bash
sudo systemctl daemon-reload
sudo systemctl restart careermate-backend
sudo journalctl -u careermate-backend -n 50 --no-pager | grep 'LLM client init'
# 期望日志: provider=qwen, apiKeyConfigured=true
```

## 6. Nginx Routing Plan

### Plan A (recommended now)

Keep RAGForge routes unchanged and use:

- CareerMate frontend: `/careermate/`
- CareerMate API: `/careermate-api/`
- ingress server already has RAGForge Nginx/server blocks
- for real rollout, prefer merging location snippet:
  - `deploy/nginx/careermate.locations.example`
- do not blindly replace existing config with a full standalone server block
- always backup current Nginx config before modification
- if your Nginx version reports error on `proxy_set_header Connection "";`, remove that line
- minimum required SSE proxy options for CareerMate:
  - `proxy_http_version 1.1`
  - `proxy_buffering off`
  - `proxy_read_timeout 300s`
  - `proxy_send_timeout 300s`
- if `nginx -t` fails, rollback to backup immediately and do not run reload
- if Nginx runs in Docker:
  - frontend alias must point to container-visible path (for example `/usr/share/nginx/html/careermate/`)
  - copy CareerMate dist into the host directory mounted to `/usr/share/nginx/html`, then verify:
    - `docker exec <nginx-container> test -f /usr/share/nginx/html/careermate/index.html`
  - `/careermate-api/` must not proxy to `127.0.0.1` (container loopback)
  - proxy target should be host private IP (`172.19.40.32:18080`) or Docker gateway IP

### Plan B (future migration)

- CareerMate frontend: `/`
- CareerMate API: `/api/`
- Move RAGForge to `/ragforge/` and `/ragforge-api/`

Current recommendation: **Plan A only**.

## 7. Database Connectivity

Use private network PostgreSQL:

- `DB_URL=jdbc:postgresql://172.25.90.183:5432/careermate_db`

Read-only inspection result:

- `careermate_db` currently does not exist
- `careermate` role currently does not exist

Initialization template:

- `deploy/sql/init-careermate-db.sql.example`

## 8. Deployment Steps

### 8.1 One-time server bootstrap (manual)

1. On data server, initialize database and role (template SQL, manual confirm).
2. On ingress server, create `/opt/careermate/backend/.env.app` with real secrets (never commit).
3. Install systemd unit from `deploy/systemd/careermate-backend.service.example` as `/etc/systemd/system/careermate-backend.service`, then `systemctl daemon-reload && systemctl enable careermate-backend`.
4. Add Nginx locations for `/careermate/` and `/careermate-api/` (see `deploy/nginx/careermate.locations.example`).
5. Ensure `ragforge-nginx` can read `/usr/share/nginx/html/careermate/index.html` (host: `/opt/rag-forge/frontend/dist/careermate/`).
6. Configure GitHub Secrets (section 13).

### 8.2 Ongoing deploy via GitHub Actions (recommended)

1. Push to `main` (or run workflow manually).
2. GitHub Actions builds backend JAR and frontend `dist`.
3. Artifacts upload to `/opt/careermate/releases/${GITHUB_SHA}/`.
4. `deploy-from-github.sh` switches `/opt/careermate/current`, syncs frontend, verifies systemd `ExecStart` points to `/opt/careermate/current/backend/app.jar` (auto-fixes legacy `careermate-backend.jar` paths), then restarts backend.
5. Workflow health checks pass, or the job fails without deleting previous releases.

### 8.3 Manual deploy (fallback)

1. Build backend and frontend locally (section 3).
2. Upload JAR as `app.jar` and `dist/` into a new release directory.
3. Run `sudo bash /opt/careermate/scripts/deploy-from-github.sh <release-sha>`.

## 9. Verification

- `GET /careermate-api/health`
- login/enter flow works
- AgentChat SSE stream works (`plan/token/message/done`)

## 10. Rollback

- Releases under `/opt/careermate/releases/` are never deleted by CI/CD.
- If a deploy fails, GitHub Actions stops; `current` may still point at the failed release—roll back manually.
- Backup Nginx config before any routing change.

Manual rollback on ingress server:

```bash
# List releases
ls -1 /opt/careermate/releases/

# Roll back to a previous SHA directory
sudo bash /opt/careermate/scripts/rollback-careermate.sh /opt/careermate/releases/<previous-sha>
```

Verify:

```bash
curl -fsS http://127.0.0.1:18080/api/health
curl -fsS http://8.163.63.222/careermate-api/health
```

## 11. Inspection-based Risk Notes

- Ingress server already has:
  - `80` occupied by `ragforge-nginx`
  - `8080` occupied by `ragforge-backend`
- Data server disk usage is around `77%`; capacity monitoring is required.
- Data services are reachable over private network from ingress server (`5432/6379/9200`).
- Do not reuse `/api/` now; use `/careermate-api/` to avoid route conflicts.

## 12. Template Files

- Nginx template: `deploy/nginx/careermate.conf.example`
- Nginx location snippet template (preferred for existing ingress): `deploy/nginx/careermate.locations.example`
- Backend env template: `deploy/env/careermate-backend.env.example`
- Frontend env template: `deploy/env/careermate-frontend.env.example`
- DB init SQL template: `deploy/sql/init-careermate-db.sql.example`
- Entry deploy script template: `deploy/scripts/deploy-careermate-entry.sh.example`
- GitHub deploy script: `deploy/scripts/deploy-from-github.sh`
- Rollback script: `deploy/scripts/rollback-careermate.sh`
- systemd template: `deploy/systemd/careermate-backend.service.example`
- GitHub Actions workflow: `.github/workflows/careermate-deploy.yml`

## 13. GitHub CI/CD

### 13.1 Workflow

File: `.github/workflows/careermate-deploy.yml`

Triggers:

- `push` to `main`
- `workflow_dispatch` (manual)

Jobs (each job is a separate node; `needs` controls order):

| Job | Depends on | Purpose |
|-----|------------|---------|
| `backend-test` | — | `mvn -B test` in `backend/` (GitHub Actions 启动 PostgreSQL 16 服务容器供 Flyway + 集成测试) |
| `backend-build` | `backend-test` | `mvn -B -DskipTests package`; artifact `careermate-backend-jar` |
| `frontend-build` | — | `npm ci` + production build; artifact `careermate-frontend-dist` |
| `package-release` | `backend-build`, `frontend-build` | Assemble `release/backend/app.jar` + `release/frontend/dist/`; artifact `careermate-release` |
| `deploy-production` | `package-release` | SSH upload + `deploy-from-github.sh`; **environment: `production`** (optional manual approval) |
| `smoke-test` | `deploy-production` | `curl` API health + frontend entry |

On failure: workflow stops at the failed job; server releases are not deleted; roll back manually (section 10).

**Production approval:** Repository → Settings → Environments → `production` → enable **Required reviewers**. Deploy pauses at `deploy-production` until approved.

### 13.2 GitHub Secrets

Configure in repository **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `CAREERMATE_DEPLOY_HOST` | Ingress server host (e.g. `8.163.63.222`) |
| `CAREERMATE_DEPLOY_USER` | SSH user (e.g. `root` or deploy account) |
| `CAREERMATE_DEPLOY_SSH_KEY` | Private key (PEM) for SSH |
| `CAREERMATE_DEPLOY_PORT` | SSH port (optional; default `22`) |

Do **not** put database passwords, `JWT_SECRET`, or LLM API keys in the repository or in these secrets unless strictly required for deploy (they are not—use server-local `.env.app`).

### 13.3 Server prerequisites (before first CI deploy)

| Item | Location / command |
|------|-------------------|
| Backend env | `/opt/careermate/backend/.env.app` |
| systemd unit | `/etc/systemd/system/careermate-backend.service` from template |
| Backend port | `18080` (do not use `8080`—RAGForge) |
| Nginx routes | `/careermate/`, `/careermate-api/` |
| Frontend static | Host `/opt/rag-forge/frontend/dist/careermate/` → container `/usr/share/nginx/html/careermate/` |
| Deploy scripts dir | `/opt/careermate/scripts/` (workflow uploads scripts each run) |
| SSH access | Deploy user can `sudo systemctl restart careermate-backend` |

systemd must use release layout:

- `WorkingDirectory=/opt/careermate/current/backend`
- `ExecStart=/usr/bin/java -jar /opt/careermate/current/backend/app.jar`
- `EnvironmentFile=/opt/careermate/backend/.env.app`

### 13.4 Release layout

Each deploy creates:

```text
/opt/careermate/releases/<git-sha>/
  backend/app.jar
  frontend/dist/
/opt/careermate/current -> /opt/careermate/releases/<git-sha>
```

### 13.5 Prohibited

- Do not commit `.env.app` or production secrets.
- Do not store DB passwords / JWT / LLM keys in repo files.
- Do not change RAGForge deploy paths or port `8080`.
- Do not modify production PostgreSQL / Redis / Elasticsearch from CI.
- Do not delete old releases automatically in this phase.
- No Docker image registry, Kubernetes, or blue/green in this phase.

## 14. 本地 E2E 验收（Playwright）

在 `frontend/careermate` 目录执行 E2E（本机 Google Chrome headed）。云端完整用户场景（注册→功能→退出登录，不 skip）：`npm run test:e2e:cloud:full`（要求 `SECURITY_MODE=jwt` 且 `JWT_SECRET`≥32 字节）。云端须为 jwt 模式，账号前缀 `e2e_cloud_`。
