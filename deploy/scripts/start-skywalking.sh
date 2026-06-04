#!/usr/bin/env bash
# Start SkyWalking OAP + UI via Docker Compose (localhost bindings only).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deploy/skywalking/docker-compose.skywalking.yml"

docker compose -f "${COMPOSE_FILE}" up -d
docker compose -f "${COMPOSE_FILE}" ps

echo "OAP gRPC: 127.0.0.1:11800"
echo "UI local: http://127.0.0.1:18088/"
echo "After Nginx: http://<ingress>/skywalking/"
