#!/usr/bin/env bash
# Server 3 (app layer) one-time bootstrap for CareerMate backend.
# Creates user, directories, permissions, and a placeholder .env.app template.
# Does NOT start any service. Safe to re-run.
#
# Usage (from careermate repo root on Server 3):
#   sudo bash deploy/scripts/init-server3.sh
#   sudo CAREERMATE_DEPLOY_USER=<CAREERMATE_APP_USER> bash deploy/scripts/init-server3.sh
set -euo pipefail

BASE_DIR="/opt/careermate"
BACKEND_DIR="${BASE_DIR}/backend"
ENV_APP="${BACKEND_DIR}/.env.app"
SERVICE_USER="${CAREERMATE_SERVICE_USER:-careermate}"
DEPLOY_USER="${CAREERMATE_DEPLOY_USER:-${SUDO_USER:-}}"

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

if [[ -n "${DEPLOY_USER}" && "${DEPLOY_USER}" != "root" && "${DEPLOY_USER}" != "${SERVICE_USER}" ]]; then
  if id "${DEPLOY_USER}" &>/dev/null; then
    usermod -aG "${SERVICE_USER}" "${DEPLOY_USER}"
    echo "  added deploy user ${DEPLOY_USER} to group ${SERVICE_USER}"
  else
    echo "  WARN: deploy user ${DEPLOY_USER} does not exist; skip group assignment" >&2
  fi
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
chown root:"${SERVICE_USER}" "${BASE_DIR}" "${BASE_DIR}/releases" "${BASE_DIR}/scripts" \
  "${BASE_DIR}/deploy" "${BASE_DIR}/deploy/scripts" "${BASE_DIR}/deploy/skywalking" \
  "${BASE_DIR}/deploy/nginx"
chmod 755 "${BASE_DIR}"
chmod 2775 "${BASE_DIR}/releases" "${BASE_DIR}/scripts" "${BASE_DIR}/deploy" \
  "${BASE_DIR}/deploy/scripts" "${BASE_DIR}/deploy/skywalking" "${BASE_DIR}/deploy/nginx"

chown "${SERVICE_USER}:${SERVICE_USER}" "${BACKEND_DIR}" "${BASE_DIR}/logs"
chmod 750 "${BACKEND_DIR}"
chmod 755 "${BASE_DIR}/logs"

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
if [[ -n "${DEPLOY_USER}" && "${DEPLOY_USER}" != "root" && "${DEPLOY_USER}" != "${SERVICE_USER}" ]]; then
  echo "  deploy user: ${DEPLOY_USER} (log out/in or restart SSH session for group membership)"
fi
echo "  next steps:"
echo "    1. Edit ${ENV_APP} with real production secrets"
echo "    2. Install systemd unit: deploy/systemd/careermate-backend.service.example"
echo "       -> /etc/systemd/system/careermate-backend.service"
echo "    3. systemctl daemon-reload && systemctl enable careermate-backend"
echo "    4. Deploy backend via CI or deploy-from-github.sh"
