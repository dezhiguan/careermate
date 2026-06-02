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

Recommended ingress server layout:

```text
/opt/careermate/
  backend/
    careermate-backend.jar
    .env.app
  frontend/
    dist/
  logs/
  releases/
  scripts/
```

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

1. On data server, initialize database and role (template SQL, manual confirm).
2. Build backend and frontend locally.
3. Upload JAR and `dist` to ingress server release directory.
4. Create `/opt/careermate/backend/.env.app` on server with real secrets.
5. Install systemd service from template and start backend on `18080`.
6. Add Nginx config for `/careermate/` and `/careermate-api/`.
7. Validate Nginx syntax and reload (only during execution phase).
8. Run verification checks.

## 9. Verification

- `GET /careermate-api/health`
- login/enter flow works
- AgentChat SSE stream works (`plan/token/message/done`)

## 10. Rollback

- Keep previous backend JAR under `/opt/careermate/releases`
- backup Nginx config before change
- rollback by restoring old JAR/service and old Nginx config

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
- systemd template: `deploy/systemd/careermate-backend.service.example`
