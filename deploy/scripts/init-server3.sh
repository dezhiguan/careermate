#!/usr/bin/env bash
# Server 3 (app layer) one-time bootstrap for CareerMate backend.
# Creates user, directories, permissions, and shared env templates.
# Does NOT start any service. Safe to re-run.
#
# Usage (from careermate repo root on Server 3):
#   sudo bash deploy/scripts/init-server3.sh
#   sudo CAREERMATE_DEPLOY_USER=<CAREERMATE_APP_USER> bash deploy/scripts/init-server3.sh
set -euo pipefail

BASE_DIR="/opt/careermate"
BACKEND_DIR="${BASE_DIR}/backend"
SHARED_ENV_DIR="/opt/shared/env"
COMMON_ENV="${SHARED_ENV_DIR}/common.env"
CAREERMATE_ENV="${SHARED_ENV_DIR}/careermate.env"
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
  "${SHARED_ENV_DIR}" \
  "${BASE_DIR}" \
  "${BACKEND_DIR}" \
  "${BASE_DIR}/releases" \
  "${BASE_DIR}/scripts" \
  "${BASE_DIR}/logs" \
  "${BASE_DIR}/logs/backend-1" \
  "${BASE_DIR}/logs/backend-2" \
  "${BASE_DIR}/logs/backend-3" \
  "${BASE_DIR}/deploy/scripts" \
  "${BASE_DIR}/deploy/skywalking" \
  "${BASE_DIR}/deploy/nginx"

echo "[3/5] Set ownership and permissions"
chown root:root /opt/shared "${SHARED_ENV_DIR}"
chmod 755 /opt/shared
chmod 700 "${SHARED_ENV_DIR}"

chown root:"${SERVICE_USER}" "${BASE_DIR}" "${BASE_DIR}/releases" "${BASE_DIR}/scripts" \
  "${BASE_DIR}/deploy" "${BASE_DIR}/deploy/scripts" "${BASE_DIR}/deploy/skywalking" \
  "${BASE_DIR}/deploy/nginx"
chmod 755 "${BASE_DIR}"
chmod 2775 "${BASE_DIR}/releases" "${BASE_DIR}/scripts" "${BASE_DIR}/deploy" \
  "${BASE_DIR}/deploy/scripts" "${BASE_DIR}/deploy/skywalking" "${BASE_DIR}/deploy/nginx"

chown "${SERVICE_USER}:${SERVICE_USER}" "${BACKEND_DIR}" "${BASE_DIR}/logs" \
  "${BASE_DIR}/logs/backend-1" "${BASE_DIR}/logs/backend-2" "${BASE_DIR}/logs/backend-3"
chmod 750 "${BACKEND_DIR}"
chmod 755 "${BASE_DIR}/logs" "${BASE_DIR}/logs/backend-1" "${BASE_DIR}/logs/backend-2" "${BASE_DIR}/logs/backend-3"

echo "[4/5] Ensure placeholder env file"
if [[ -f "${COMMON_ENV}" ]]; then
  echo "  ${COMMON_ENV} already exists, skipping"
else
  cat > "${COMMON_ENV}" <<'EOF'
TZ=Asia/Shanghai
DASHSCOPE_API_KEY=your_dashscope_api_key
LLM_API_KEY=your_dashscope_api_key
EOF
  chown root:root "${COMMON_ENV}"
  chmod 600 "${COMMON_ENV}"
  echo "  created ${COMMON_ENV} (mode 600)"
fi

if [[ -f "${CAREERMATE_ENV}" ]]; then
  echo "  ${CAREERMATE_ENV} already exists, skipping copy"
else
  cp "${ENV_EXAMPLE}" "${CAREERMATE_ENV}"
  sed -i.bak '/^LLM_API_KEY=/d' "${CAREERMATE_ENV}"
  rm -f "${CAREERMATE_ENV}.bak"
  chown root:root "${CAREERMATE_ENV}"
  chmod 600 "${CAREERMATE_ENV}"
  echo "  created ${CAREERMATE_ENV} from template (mode 600)"
  echo ""
  echo "  ACTION REQUIRED: edit shared env files and replace placeholder secrets before starting backend:"
  echo "    - ${COMMON_ENV}: DASHSCOPE_API_KEY / LLM_API_KEY"
  echo "    - ${CAREERMATE_ENV}: DB_PASSWORD / JWT_SECRET / service-specific values"
  echo "    - DB_PASSWORD"
  echo "    - JWT_SECRET"
  echo "    - other sensitive values as needed"
fi

echo "[5/5] Bootstrap complete (no services started)"
echo "  base dir: ${BASE_DIR}"
echo "  shared env dir: ${SHARED_ENV_DIR}"
if [[ -n "${DEPLOY_USER}" && "${DEPLOY_USER}" != "root" && "${DEPLOY_USER}" != "${SERVICE_USER}" ]]; then
  echo "  deploy user: ${DEPLOY_USER} (log out/in or restart SSH session for group membership)"
fi
echo "  next steps:"
echo "    1. Edit ${COMMON_ENV} and ${CAREERMATE_ENV} with real production secrets"
echo "    2. Deploy backend via CI or deploy-from-github.sh"
