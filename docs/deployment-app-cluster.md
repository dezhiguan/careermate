# CareerMate App Replica Deployment

This document describes the app-layer replica deployment shape on Server 3.

## Topology

- Server 2 runs Nginx, frontend assets, TLS, SSH tunnels, and reverse proxy upstream.
- Server 3 runs three `careermate-backend` Docker containers on the same host.
- Server 1 continues to run PostgreSQL and other shared middleware.

This is single-host multi-replica deployment. It improves same-host request concurrency but is not machine-level high availability.

## Server 3 Compose

Three containers share one image and env files:

| Container | Host port | Log dir |
|-----------|-----------|---------|
| `careermate-backend-1` | `18080` | `/opt/careermate/logs/backend-1` |
| `careermate-backend-2` | `18081` | `/opt/careermate/logs/backend-2` |
| `careermate-backend-3` | `18082` | `/opt/careermate/logs/backend-3` |

```bash
docker compose -f /opt/careermate/docker-compose-backend.yml up -d --force-recreate
```

## Server 2 SSH Tunnels

Nginx upstream points to Server 2 local tunnel addresses (`172.19.40.32`). Add tunnels for all three app ports:

```text
172.19.40.32:18080 → 172.25.90.184:18080
172.19.40.32:18081 → 172.25.90.184:18081
172.19.40.32:18082 → 172.25.90.184:18082
```

Verify from Server 2:

```bash
curl -fsS http://172.19.40.32:18080/api/health
curl -fsS http://172.19.40.32:18081/api/health
curl -fsS http://172.19.40.32:18082/api/health
```

## Nginx Upstream

Canonical config lives in `rag-forge/nginx.conf`:

```nginx
upstream careermate_backend {
    ip_hash;
    server 172.19.40.32:18080;
    server 172.19.40.32:18081;
    server 172.19.40.32:18082;
    keepalive 32;
}
```

`ip_hash` keeps Agent SSE and in-memory task state on one replica per client IP. Plain REST + JWT endpoints can still run on any replica.

After updating `nginx.conf` on Server 2:

```bash
docker exec ragforge-nginx nginx -t
docker exec ragforge-nginx nginx -s reload
```

## Shared Env

All replicas read:

- `/opt/shared/env/common.env`
- `/opt/shared/env/careermate.env`

Keep the existing JVM setting unless Server 3 memory is confirmed:

```text
JAVA_OPTS=-Xms512m -Xmx1g
```

Three replicas with `-Xmx1g` need roughly 3 GB heap plus metaspace and OS headroom on Server 3.

## Health Checks

Server 3:

```bash
curl -fsS http://127.0.0.1:18080/api/health
curl -fsS http://127.0.0.1:18081/api/health
curl -fsS http://127.0.0.1:18082/api/health
```

Public ingress:

```bash
curl -fsS https://careerforge.cn/api/health
curl -fsS http://8.163.63.222/careermate-api/health
```

## Deploy / Rollback

CI and manual deploys use the same scripts:

```bash
sudo bash /opt/careermate/scripts/deploy-from-github.sh <release-sha>
sudo bash /opt/careermate/scripts/rollback-careermate.sh /opt/careermate/releases/<previous-sha>
```

Both scripts wait for health on `18080`, `18081`, and `18082`.
