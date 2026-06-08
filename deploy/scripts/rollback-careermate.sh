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
HEALTH_URL="http://127.0.0.1:18080/api/health"

echo "[1/4] Validate release directory: ${RELEASE_DIR}"
if [[ ! -d "${RELEASE_DIR}" ]]; then
  echo "Release directory not found: ${RELEASE_DIR}" >&2
  exit 1
fi

if [[ ! -f "${RELEASE_DIR}/backend/app.jar" ]]; then
  echo "Missing ${RELEASE_DIR}/backend/app.jar" >&2
  exit 1
fi

echo "[2/4] Switch current symlink"
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

echo "[3/4] Restart careermate-backend"
sudo systemctl restart careermate-backend

echo "[4/4] Wait for backend health"
for _ in $(seq 1 30); do
  if curl -fsS "${HEALTH_URL}" >/dev/null; then
    break
  fi
  sleep 2
done

curl -fsS "${HEALTH_URL}"

echo "Backend rollback succeeded (Server 3)"
echo "  current: $(readlink -f "${CURRENT_LINK}")"
echo "  frontend: roll back separately on Server 2 if needed"
