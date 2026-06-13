#!/usr/bin/env bash
# Start SkyWalking OAP + UI via Docker Compose.
# On Server 3 (k8s app layer), bind private IP so ingress Nginx and k8s pods can reach OAP/UI:
#   SKYWALKING_BIND_IP=172.25.90.184 bash deploy/scripts/start-skywalking.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deploy/skywalking/docker-compose.skywalking.yml"
export SKYWALKING_BIND_IP="${SKYWALKING_BIND_IP:-127.0.0.1}"

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    echo "ERROR: docker compose / docker-compose not found" >&2
    exit 1
  fi
}

compose -f "${COMPOSE_FILE}" up -d
compose -f "${COMPOSE_FILE}" ps

echo "OAP gRPC (localhost): 127.0.0.1:11800"
echo "OAP gRPC (bind IP):     ${SKYWALKING_BIND_IP}:11800"
echo "UI local:               http://127.0.0.1:18088/skywalking/"
echo "UI private:             http://${SKYWALKING_BIND_IP}:18088/skywalking/"
echo "Ingress (after Nginx):  http://<ingress>/skywalking/"
