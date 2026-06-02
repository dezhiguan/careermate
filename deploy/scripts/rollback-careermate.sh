#!/usr/bin/env bash
# Roll back CareerMate to a previous release directory.
# Usage: rollback-careermate.sh /opt/careermate/releases/<sha>
set -euo pipefail

if [[ "${#}" -ne 1 ]]; then
  echo "Usage: $0 /opt/careermate/releases/<release-sha>"
  exit 1
fi

RELEASE_DIR="${1}"
CURRENT_LINK="/opt/careermate/current"
# Nginx container path: /usr/share/nginx/html/careermate/
# Host bind-mount (ragforge-nginx): /opt/rag-forge/frontend/dist/careermate/
FRONTEND_TARGET="/opt/rag-forge/frontend/dist/careermate"
HEALTH_URL="http://127.0.0.1:18080/api/health"

echo "[1/6] Validate release directory: ${RELEASE_DIR}"
if [[ ! -d "${RELEASE_DIR}" ]]; then
  echo "Release directory not found: ${RELEASE_DIR}" >&2
  exit 1
fi

if [[ ! -f "${RELEASE_DIR}/backend/app.jar" ]]; then
  echo "Missing ${RELEASE_DIR}/backend/app.jar" >&2
  exit 1
fi

if [[ ! -f "${RELEASE_DIR}/frontend/dist/index.html" ]]; then
  echo "Missing ${RELEASE_DIR}/frontend/dist/index.html" >&2
  exit 1
fi

echo "[2/6] Switch current symlink"
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

echo "[3/6] Sync frontend dist to Nginx-visible directory: ${FRONTEND_TARGET}"
mkdir -p "${FRONTEND_TARGET}"
rsync -a --delete "${RELEASE_DIR}/frontend/dist/" "${FRONTEND_TARGET}/"

echo "[4/6] Restart careermate-backend"
sudo systemctl restart careermate-backend

echo "[5/6] Wait for backend health"
for _ in $(seq 1 30); do
  if curl -fsS "${HEALTH_URL}" >/dev/null; then
    break
  fi
  sleep 2
done

echo "[6/6] Health check"
curl -fsS "${HEALTH_URL}"

echo "Rollback succeeded"
echo "  current: $(readlink -f "${CURRENT_LINK}")"
