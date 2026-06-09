#!/usr/bin/env bash
# Deploy a GitHub Actions release on Server 3 (app layer).
# Frontend is deployed separately to Server 2 ingress by CI.
# Usage: deploy-from-github.sh <release-sha>
set -euo pipefail

if [[ "${#}" -ne 1 ]]; then
  echo "Usage: $0 <release-sha>"
  exit 1
fi

RELEASE_SHA="${1}"
RELEASE_DIR="/opt/careermate/releases/${RELEASE_SHA}"
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
    curl -fsS "${url}" >/dev/null
    echo "  health ok: ${url}"
  done
}

PREVIOUS_RELEASE=""
PREVIOUS_RELEASE="$(readlink -f "${CURRENT_LINK}" 2>/dev/null || true)"

on_error() {
  echo "ERROR: deployment failed for release ${RELEASE_SHA}" >&2
  if [[ -n "${PREVIOUS_RELEASE}" ]]; then
    echo "Previous release (for manual rollback): ${PREVIOUS_RELEASE}" >&2
    echo "Run: sudo bash /opt/careermate/scripts/rollback-careermate.sh '${PREVIOUS_RELEASE}'" >&2
  fi
}
trap on_error ERR

stop_legacy_systemd() {
  if command -v systemctl >/dev/null 2>&1 \
    && systemctl list-unit-files careermate-backend.service >/dev/null 2>&1; then
    if systemctl is-active --quiet careermate-backend; then
      echo "Stopping legacy systemd service: careermate-backend"
      systemctl stop careermate-backend
    fi
    if systemctl is-enabled --quiet careermate-backend 2>/dev/null; then
      echo "Disabling legacy systemd service: careermate-backend"
      systemctl disable careermate-backend >/dev/null 2>&1 || true
    fi
  fi
}

stop_legacy_docker() {
  # Single-replica container from pre-3-node compose; it holds :18080 and blocks backend-1.
  if docker ps -a --format '{{.Names}}' | grep -qx 'careermate-backend'; then
    echo "Removing legacy Docker container: careermate-backend"
    docker rm -f careermate-backend
  fi
}

echo "[1/7] Validate release directory: ${RELEASE_DIR}"
if [[ ! -d "${RELEASE_DIR}" ]]; then
  echo "Release directory not found: ${RELEASE_DIR}" >&2
  exit 1
fi

echo "[2/7] Validate backend artifact"
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
if [[ ! -f /opt/shared/env/common.env || ! -f /opt/shared/env/careermate.env ]]; then
  echo "Missing shared env files: /opt/shared/env/common.env and /opt/shared/env/careermate.env" >&2
  exit 1
fi

echo "[3/7] Previous release: ${PREVIOUS_RELEASE:-<none>}"

echo "[4/7] Switch current symlink"
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

echo "[5/7] Stop legacy systemd service and single-node container if present"
stop_legacy_systemd
stop_legacy_docker

echo "[6/7] Build image and restart Docker container"
docker build --build-arg JAR_FILE=app.jar -t "${IMAGE_NAME}" "${CURRENT_LINK}/backend"
docker compose -f "${COMPOSE_FILE}" up -d --force-recreate
docker compose -f "${COMPOSE_FILE}" ps

echo "[7/7] Wait for backend health (ports: ${HEALTH_PORTS[*]})"
wait_for_health

echo "Deployment succeeded (Server 3 backend only)"
echo "  release: ${RELEASE_DIR}"
echo "  current: $(readlink -f "${CURRENT_LINK}")"
echo "  containers: careermate-backend-1 careermate-backend-2 careermate-backend-3"
echo "  frontend: deployed separately to Server 2 /opt/rag-forge/frontend/dist/careerforge/"
if [[ -n "${PREVIOUS_RELEASE}" && "${PREVIOUS_RELEASE}" != "$(readlink -f "${CURRENT_LINK}")" ]]; then
  echo "  rollback: sudo bash /opt/careermate/scripts/rollback-careermate.sh '${PREVIOUS_RELEASE}'"
fi
