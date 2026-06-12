# CareerMate on single-node k3s (Server 3)

Scope: **CareerMate backend + frontend only**.

Not in scope:

- 3-node cluster / Rancher
- PostgreSQL, Redis, Elasticsearch, RocketMQ (stay on Server 1)
- RAGForge
- Business code changes

## Topology

```text
User -> Server 2 Nginx (172.19.40.32)
         /careermate/     -> Server 3 k3s NodePort 31000 (frontend Pod)
         /careermate-api/ -> Server 3 k3s NodePort 31080 (backend Pod)
Backend Pod -> Server 1 data layer (172.25.90.183)
```

## Server 3 — one-time + deploy

```bash
cd /path/to/careermate

# 1) Install k3s
sudo bash deploy/scripts/install-k3s-server3.sh

# 2) Verify
sudo k3s kubectl get nodes -o wide
sudo k3s kubectl get pods -A

# 3) Ensure env files exist (from init-server3.sh)
#    /opt/shared/env/common.env
#    /opt/shared/env/careermate.env

# 4) Build images + deploy
sudo bash deploy/scripts/deploy-careermate-k8s.sh

# 5) Check pods/services
sudo k3s kubectl -n careermate get pods -o wide
sudo k3s kubectl -n careermate get svc
bash deploy/scripts/verify-careermate-k8s.sh
```

Re-deploy after code changes:

```bash
sudo bash deploy/scripts/deploy-careermate-k8s.sh
# or skip rebuild if images already built:
sudo SKIP_IMAGE_BUILD=1 bash deploy/scripts/deploy-careermate-k8s.sh
```

## Server 2 — Nginx

Merge `deploy/nginx/careermate-k8s.locations.example` into the ingress server block, then:

```bash
sudo nginx -t && sudo nginx -s reload
```

## Rollback

1. Keep k3s running; restore Server 2 Nginx to previous upstream/static paths.
2. Start legacy Docker backends on Server 3:

```bash
docker start careermate-backend-1 careermate-backend-2 careermate-backend-3
```

3. After k8s is verified stable, stop legacy containers (do not delete):

```bash
sudo bash deploy/scripts/stop-legacy-careermate-containers.sh
```

## Manifests

| File | Purpose |
|------|---------|
| `namespace.yaml` | `careermate` namespace |
| `backend-deployment.yaml` | 2 replicas, env from Secret, logs + SkyWalking hostPath |
| `backend-service.yaml` | NodePort **31080** |
| `frontend-deployment.yaml` | nginx static Pod |
| `frontend-service.yaml` | NodePort **31000** |

Backend Secret is created from `/opt/shared/env/common.env` + `careermate.env` — never commit real secrets.

## Images

| Image | Build |
|-------|-------|
| `careermate/backend:latest` | `backend/Dockerfile` |
| `careermate/frontend:latest` | `frontend/careermate/Dockerfile` (VITE base `/careermate/`, API `/careermate-api`) |

Images are imported into k3s via `docker save | k3s ctr images import`.
