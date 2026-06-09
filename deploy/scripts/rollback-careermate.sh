#!/usr/bin/env bash
# Roll back CareerMate backend on Server 3 to a previous release directory.
# Frontend rollback: rsync a previous dist to Server 2 /opt/rag-forge/frontend/dist/careerforge/
# Usage: rollback-careermate.sh /opt/careermate/releases/<sha>
set -euo pipefail

if [[ "${#}" -ne 1 ]]; then
  echo "Usage: $0 /opt/careermate/releases/<release-sha>"
  exit 1
fi

RELEASE_DIR="${1}"
CURRENT_LINK="/opt/careermate/current"
HEALTH_PORTS=(18080 18081 18082)
COMPOSE_FILE="/opt/careermate/docker-compose-backend.yml"
IMAGE_NAME="careermate-backend:latest"

wait_for_health() {
  local port
  for port in "${HEALTH_PORTS[@]}"; do
    local url="http://127.0.0.1:${port}/api/health"
    for _ in $(seq 1 30); do
      if curl -fsS "${url}" >/dev/null; then
        break
      fi
      sleep 2
    done
    curl -fsS "${url}"
    echo "  health ok: ${url}"
  done
}

echo "[1/4] Validate release directory: ${RELEASE_DIR}"
if [[ ! -d "${RELEASE_DIR}" ]]; then
  echo "Release directory not found: ${RELEASE_DIR}" >&2
  exit 1
fi

if [[ ! -f "${RELEASE_DIR}/backend/app.jar" ]]; then
  echo "Missing ${RELEASE_DIR}/backend/app.jar" >&2
  exit 1
fi
if [[ ! -f "${RELEASE_DIR}/backend/Dockerfile" ]]; then
  echo "Missing ${RELEASE_DIR}/backend/Dockerfile" >&2
  exit 1
fi
if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "Missing ${COMPOSE_FILE}" >&2
  exit 1
fi

echo "[2/4] Switch current symlink"
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

echo "[3/4] Build image and restart Docker container"
docker build --build-arg JAR_FILE=app.jar -t "${IMAGE_NAME}" "${CURRENT_LINK}/backend"
docker compose -f "${COMPOSE_FILE}" up -d --force-recreate

echo "[4/4] Wait for backend health (ports: ${HEALTH_PORTS[*]})"
wait_for_health

echo "Backend rollback succeeded (Server 3)"
echo "  current: $(readlink -f "${CURRENT_LINK}")"
echo "  frontend: roll back separately on Server 2 if needed"
