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
SYSTEMD_UNIT="/etc/systemd/system/careermate-backend.service"
EXPECTED_JAR="/opt/careermate/current/backend/app.jar"
EXPECTED_WORKDIR="/opt/careermate/current/backend"

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

ensure_systemd_uses_current_jar() {
  if [[ ! -f "${SYSTEMD_UNIT}" ]]; then
    echo "Missing systemd unit: ${SYSTEMD_UNIT}" >&2
    echo "Install from deploy/systemd/careermate-backend.service.example" >&2
    exit 1
  fi

  if grep -qF "${EXPECTED_JAR}" "${SYSTEMD_UNIT}" \
    && grep -qF "WorkingDirectory=${EXPECTED_WORKDIR}" "${SYSTEMD_UNIT}"; then
    echo "systemd unit OK (ExecStart -> ${EXPECTED_JAR})"
    return 0
  fi

  echo "WARN: ${SYSTEMD_UNIT} must run ${EXPECTED_JAR}; updating unit file" >&2
  sed -i "s|^WorkingDirectory=.*|WorkingDirectory=${EXPECTED_WORKDIR}|" "${SYSTEMD_UNIT}"
  sed -i "s|^ExecStart=.*|ExecStart=/usr/bin/java -jar ${EXPECTED_JAR}|" "${SYSTEMD_UNIT}"
  systemctl daemon-reload

  if ! grep -qF "${EXPECTED_JAR}" "${SYSTEMD_UNIT}"; then
    echo "Failed to fix systemd ExecStart in ${SYSTEMD_UNIT}" >&2
    grep -E '^(ExecStart|WorkingDirectory)=' "${SYSTEMD_UNIT}" >&2 || true
    exit 1
  fi
  echo "systemd unit fixed and reloaded"
}

echo "[1/10] Validate release directory: ${RELEASE_DIR}"
if [[ ! -d "${RELEASE_DIR}" ]]; then
  echo "Release directory not found: ${RELEASE_DIR}" >&2
  exit 1
fi

echo "[2/10] Validate backend artifact"
if [[ ! -f "${RELEASE_DIR}/backend/app.jar" ]]; then
  echo "Missing ${RELEASE_DIR}/backend/app.jar" >&2
  exit 1
fi

echo "[3/10] Validate frontend artifact"
if [[ ! -f "${RELEASE_DIR}/frontend/dist/index.html" ]]; then
  echo "Missing ${RELEASE_DIR}/frontend/dist/index.html" >&2
  exit 1
fi

echo "[4/10] Previous release: ${PREVIOUS_RELEASE:-<none>}"

echo "[5/10] Switch current symlink"
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"

echo "[6/10] Sync frontend dist to Nginx-visible directory: ${FRONTEND_TARGET}"
mkdir -p "${FRONTEND_TARGET}"
rsync -a --delete "${RELEASE_DIR}/frontend/dist/" "${FRONTEND_TARGET}/"

echo "[7/10] Ensure systemd runs current release JAR"
ensure_systemd_uses_current_jar

echo "[8/10] Restart careermate-backend"
sudo systemctl restart careermate-backend

echo "[9/10] Wait for backend health"
for _ in $(seq 1 30); do
  if curl -fsS "${HEALTH_URL}" >/dev/null; then
    break
  fi
  sleep 2
done
curl -fsS "${HEALTH_URL}" >/dev/null

echo "[10/10] Deployment succeeded"
echo "  release: ${RELEASE_DIR}"
echo "  current: $(readlink -f "${CURRENT_LINK}")"
echo "  frontend: ${FRONTEND_TARGET}"
if [[ -n "${PREVIOUS_RELEASE}" && "${PREVIOUS_RELEASE}" != "$(readlink -f "${CURRENT_LINK}")" ]]; then
  echo "  rollback: sudo bash /opt/careermate/scripts/rollback-careermate.sh '${PREVIOUS_RELEASE}'"
fi
