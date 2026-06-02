#!/usr/bin/env bash
# Deploy a GitHub Actions release on the ingress server.
# Usage: deploy-from-github.sh <release-sha>
set -euo pipefail

if [[ "${#}" -ne 1 ]]; then
  echo "Usage: $0 <release-sha>"
  exit 1
fi

RELEASE_SHA="${1}"
RELEASE_DIR="/opt/careermate/releases/${RELEASE_SHA}"
CURRENT_LINK="/opt/careermate/current"
# Nginx container path: /usr/share/nginx/html/careermate/
# Host bind-mount (ragforge-nginx): /opt/rag-forge/frontend/dist/careermate/
FRONTEND_TARGET="/opt/rag-forge/frontend/dist/careermate"
HEALTH_URL="http://127.0.0.1:18080/api/health"

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

echo "[1/9] Validate release directory: ${RELEASE_DIR}"
if [[ ! -d "${RELEASE_DIR}" ]]; then
  echo "Release directory not found: ${RELEASE_DIR}" >&2
  exit 1
fi

echo "[2/9] Validate backend artifact"
if [[ ! -f "${RELEASE_DIR}/backend/app.jar" ]]; then
  echo "Missing ${RELEASE_DIR}/backend/app.jar" >&2
  exit 1
fi

echo "[3/9] Validate frontend artifact"
if [[ ! -f "${RELEASE_DIR}/frontend/dist/index.html" ]]; then
  echo "Missing ${RELEASE_DIR}/frontend/dist/index.html" >&2
  exit 1
fi

echo "[4/9] Previous release: ${PREVIOUS_RELEASE:-<none>}"

echo "[5/9] Switch current symlink"
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

echo "[6/9] Sync frontend dist to Nginx-visible directory: ${FRONTEND_TARGET}"
mkdir -p "${FRONTEND_TARGET}"
rsync -a --delete "${RELEASE_DIR}/frontend/dist/" "${FRONTEND_TARGET}/"

echo "[7/9] Restart careermate-backend"
sudo systemctl restart careermate-backend

echo "[8/9] Wait for backend health"
for _ in $(seq 1 30); do
  if curl -fsS "${HEALTH_URL}" >/dev/null; then
    break
  fi
  sleep 2
done
curl -fsS "${HEALTH_URL}" >/dev/null

echo "[9/9] Deployment succeeded"
echo "  release: ${RELEASE_DIR}"
echo "  current: $(readlink -f "${CURRENT_LINK}")"
echo "  frontend: ${FRONTEND_TARGET}"
if [[ -n "${PREVIOUS_RELEASE}" && "${PREVIOUS_RELEASE}" != "$(readlink -f "${CURRENT_LINK}")" ]]; then
  echo "  rollback: sudo bash /opt/careermate/scripts/rollback-careermate.sh '${PREVIOUS_RELEASE}'"
fi
