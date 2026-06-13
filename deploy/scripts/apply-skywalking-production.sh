#!/usr/bin/env bash
# Apply SkyWalking on Server 3 (OAP/UI + Java Agent) and roll k8s backends.
# Run on Server 3 as root after syncing careermate + rag-forge repos.
#
# Usage:
#   sudo SKYWALKING_BIND_IP=172.25.90.184 bash deploy/scripts/apply-skywalking-production.sh
#   sudo SKIP_K8S_ROLLOUT=1 bash deploy/scripts/apply-skywalking-production.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CAREERMATE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RAGFORGE_ROOT="${RAGFORGE_ROOT:-/opt/rag-forge}"
export SKYWALKING_BIND_IP="${SKYWALKING_BIND_IP:-172.25.90.184}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

echo "[1/5] Install SkyWalking Java Agent (if missing)"
bash "${CAREERMATE_ROOT}/deploy/scripts/install-skywalking-agent.sh"

echo "[2/5] Start OAP + UI (bind ${SKYWALKING_BIND_IP})"
bash "${CAREERMATE_ROOT}/deploy/scripts/start-skywalking.sh"

echo "[3/5] Verify OAP/UI locally"
curl -sf "http://127.0.0.1:12800/healthcheck" >/dev/null
curl -sfI "http://127.0.0.1:18088/skywalking/" | head -1
curl -sfI "http://${SKYWALKING_BIND_IP}:18088/skywalking/" | head -1

if [[ "${SKIP_K8S_ROLLOUT:-0}" != "1" ]]; then
  echo "[4/5] Roll out k8s backends with SkyWalking agent"
  bash "${CAREERMATE_ROOT}/deploy/scripts/deploy-careermate-k8s.sh"
  if [[ -x "${RAGFORGE_ROOT}/deploy/scripts/deploy-ragforge-k8s.sh" ]]; then
    bash "${RAGFORGE_ROOT}/deploy/scripts/deploy-ragforge-k8s.sh"
  else
    echo "WARN: ${RAGFORGE_ROOT}/deploy/scripts/deploy-ragforge-k8s.sh not found; apply RAG k8s manually" >&2
  fi
else
  echo "[4/5] Skip k8s rollout (SKIP_K8S_ROLLOUT=1)"
fi

echo "[5/5] Post-check (Server 3 NodePort)"
curl -sfI "http://127.0.0.1:31080/api/health" | grep -i x-trace-id || true
curl -sfI "http://127.0.0.1:31090/api/v1/health" | grep -i x-trace-id || true

cat <<EOF

Done on Server 3.

Next on Server 2 (ingress ${SKYWALKING_BIND_IP:-172.25.90.184} is app layer):
  1) Sync rag-forge/nginx.conf (contains /skywalking/ location)
  2) docker exec ragforge-nginx nginx -t && docker exec ragforge-nginx nginx -s reload
  3) curl -I http://127.0.0.1/skywalking/
  4) curl -I http://8.163.63.222/skywalking/

Public UI: http://8.163.63.222/skywalking/
EOF
