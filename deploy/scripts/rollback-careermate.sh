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

wait_for_port_health() {
  local port="$1"
  local max_attempts="${HEALTH_MAX_ATTEMPTS:-60}"
  local sleep_secs="${HEALTH_SLEEP_SECS:-3}"
  local url="http://127.0.0.1:${port}/api/health"

  for attempt in $(seq 1 "${max_attempts}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      echo "  health ok: ${url}"
      return 0
    fi
    echo "  attempt ${attempt}/${max_attempts}: ${url} not ready"
    sleep "${sleep_secs}"
  done

  echo "ERROR: health check timed out: ${url}" >&2
  return 1
}

wait_for_health() {
  local port url
  local max_attempts="${HEALTH_MAX_ATTEMPTS:-60}"
  local sleep_secs="${HEALTH_SLEEP_SECS:-3}"

  echo "  waiting up to $((max_attempts * sleep_secs))s for ports: ${HEALTH_PORTS[*]}"

  for attempt in $(seq 1 "${max_attempts}"); do
    local all_ok=true
    for port in "${HEALTH_PORTS[@]}"; do
      url="http://127.0.0.1:${port}/api/health"
      if ! curl -fsS "${url}" >/dev/null 2>&1; then
        all_ok=false
        break
      fi
    done
    if [[ "${all_ok}" == true ]]; then
      for port in "${HEALTH_PORTS[@]}"; do
        curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null
        echo "  health ok: http://127.0.0.1:${port}/api/health"
      done
      return 0
    fi
    echo "  attempt ${attempt}/${max_attempts}: backends still starting..."
    sleep "${sleep_secs}"
  done

  echo "ERROR: health check timed out for ports: ${HEALTH_PORTS[*]}" >&2
  return 1
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

if docker ps -a --format '{{.Names}}' | grep -qx 'careermate-backend'; then
  echo "Removing legacy Docker container: careermate-backend"
  docker rm -f careermate-backend
fi

echo "[3/4] Build image and rolling-restart Docker containers"
docker build --build-arg JAR_FILE=app.jar -t "${IMAGE_NAME}" "${CURRENT_LINK}/backend"
services=(
  "careermate-backend-1:18080"
  "careermate-backend-2:18081"
  "careermate-backend-3:18082"
)
for item in "${services[@]}"; do
  service="${item%%:*}"
  port="${item##*:}"

  echo "  recreating ${service} on port ${port}..."
  docker compose -f "${COMPOSE_FILE}" up -d --force-recreate --no-deps "${service}"
  wait_for_port_health "${port}"
done

echo "[4/4] Wait for backend health (ports: ${HEALTH_PORTS[*]})"
wait_for_health

echo "Backend rollback succeeded (Server 3)"
echo "  current: $(readlink -f "${CURRENT_LINK}")"
echo "  frontend: roll back separately on Server 2 if needed"
