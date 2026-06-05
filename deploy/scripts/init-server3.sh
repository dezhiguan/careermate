#!/usr/bin/env bash
# Server 3 (app layer) one-time bootstrap for CareerMate backend.
# Creates user, directories, permissions, and a placeholder .env.app template.
# Does NOT start any service. Safe to re-run.
#
# Usage (from careermate repo root on Server 3):
#   sudo bash deploy/scripts/init-server3.sh
set -euo pipefail

BASE_DIR="/opt/careermate"
BACKEND_DIR="${BASE_DIR}/backend"
ENV_APP="${BACKEND_DIR}/.env.app"
SERVICE_USER="careermate"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_EXAMPLE="${SCRIPT_DIR}/../env/careermate-backend.env.example"

if [[ ! -f "${ENV_EXAMPLE}" ]]; then
  echo "ERROR: env template not found: ${ENV_EXAMPLE}" >&2
  echo "Run this script from the careermate repository (deploy/scripts/init-server3.sh)." >&2
  exit 1
fi

echo "[1/5] Ensure service user: ${SERVICE_USER}"
if id "${SERVICE_USER}" &>/dev/null; then
  echo "  user already exists, skipping"
else
  useradd --system --home-dir "${BASE_DIR}" --shell /usr/sbin/nologin "${SERVICE_USER}"
  echo "  created system user ${SERVICE_USER}"
fi

echo "[2/5] Create directory layout"
mkdir -p \
  "${BASE_DIR}" \
  "${BACKEND_DIR}" \
  "${BASE_DIR}/releases" \
  "${BASE_DIR}/scripts" \
  "${BASE_DIR}/logs" \
  "${BASE_DIR}/deploy/scripts" \
  "${BASE_DIR}/deploy/skywalking" \
  "${BASE_DIR}/deploy/nginx"

echo "[3/5] Set ownership and permissions"
chown -R "${SERVICE_USER}:${SERVICE_USER}" "${BASE_DIR}"
chmod 755 "${BASE_DIR}" "${BACKEND_DIR}" "${BASE_DIR}/releases" "${BASE_DIR}/scripts" \
  "${BASE_DIR}/logs" "${BASE_DIR}/deploy" "${BASE_DIR}/deploy/scripts" \
  "${BASE_DIR}/deploy/skywalking" "${BASE_DIR}/deploy/nginx"

echo "[4/5] Ensure placeholder env file"
if [[ -f "${ENV_APP}" ]]; then
  echo "  ${ENV_APP} already exists, skipping copy"
else
  cp "${ENV_EXAMPLE}" "${ENV_APP}"
  chown "${SERVICE_USER}:${SERVICE_USER}" "${ENV_APP}"
  chmod 600 "${ENV_APP}"
  echo "  created ${ENV_APP} from template (mode 600)"
  echo ""
  echo "  ACTION REQUIRED: edit ${ENV_APP} and replace placeholder secrets before starting backend:"
  echo "    - DB_PASSWORD"
  echo "    - JWT_SECRET"
  echo "    - LLM_API_KEY"
  echo "    - other sensitive values as needed"
fi

echo "[5/5] Bootstrap complete (no services started)"
echo "  base dir: ${BASE_DIR}"
echo "  next steps:"
echo "    1. Edit ${ENV_APP} with real production secrets"
echo "    2. Install systemd unit: deploy/systemd/careermate-backend.service.example"
echo "       -> /etc/systemd/system/careermate-backend.service"
echo "    3. systemctl daemon-reload && systemctl enable careermate-backend"
echo "    4. Deploy backend via CI or deploy-from-github.sh"
