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
