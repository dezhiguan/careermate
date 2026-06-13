#!/usr/bin/env bash
# Deploy CareerMate to single-node k3s on Server 3.
# Does NOT migrate PostgreSQL/Redis/ES/RocketMQ or RAGForge.
#
# Usage (from careermate repo root on Server 3):
#   sudo bash deploy/scripts/deploy-careermate-k8s.sh
#   sudo SKIP_IMAGE_BUILD=1 bash deploy/scripts/deploy-careermate-k8s.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
K8S_DIR="${REPO_ROOT}/deploy/k8s/careermate"
NAMESPACE="${CAREERMATE_K8S_NAMESPACE:-careermate}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

if [[ ! -d "${K8S_DIR}" ]]; then
  echo "ERROR: missing ${K8S_DIR}" >&2
  exit 1
fi

echo "[1/6] Ensure k3s is installed"
bash "${SCRIPT_DIR}/install-k3s-server3.sh"

echo "[1.5/6] Ensure SkyWalking OAP + UI"
export SKYWALKING_BIND_IP="${SKYWALKING_BIND_IP:-172.25.90.184}"
bash "${SCRIPT_DIR}/ensure-skywalking-stack.sh"

IMAGES_REBUILT=0
if [[ "${SKIP_IMAGE_BUILD:-0}" != "1" ]]; then
  echo "[2/6] Build and import images"
  bash "${SCRIPT_DIR}/build-careermate-k8s-images.sh"
  IMAGES_REBUILT=1
else
  echo "[2/6] Skip image build (SKIP_IMAGE_BUILD=1)"
fi

echo "[3/6] Create backend secret from /opt/shared/env"
bash "${SCRIPT_DIR}/create-careermate-k8s-secret.sh"

echo "[4/6] Apply manifests"
for manifest in namespace.yaml backend-deployment.yaml backend-service.yaml frontend-deployment.yaml frontend-service.yaml; do
  k3s kubectl apply -f "${K8S_DIR}/${manifest}"
done

# Rebuilt images keep :latest tag; without a pod restart, k3s keeps running the old container layers.
if [[ "${IMAGES_REBUILT}" -eq 1 ]]; then
  echo "[4.5/6] Restart deployments to pick up rebuilt :latest images"
  k3s kubectl -n "${NAMESPACE}" rollout restart deployment/careermate-backend
  k3s kubectl -n "${NAMESPACE}" rollout restart deployment/careermate-frontend
fi

echo "[5/6] Wait for rollouts"
k3s kubectl -n "${NAMESPACE}" rollout status deployment/careermate-backend --timeout=300s
k3s kubectl -n "${NAMESPACE}" rollout status deployment/careermate-frontend --timeout=180s

echo "[6/6] Current status"
k3s kubectl -n "${NAMESPACE}" get pods -o wide
k3s kubectl -n "${NAMESPACE}" get svc

echo ""
echo "NodePort endpoints on Server 3 (172.25.90.184):"
echo "  frontend: http://172.25.90.184:31000/"
echo "  backend:  http://172.25.90.184:31080/api/health"
echo ""
echo "Next: update Server 2 Nginx using deploy/nginx/careermate-k8s.locations.example"
echo "Verify, then stop legacy containers:"
echo "  docker stop careermate-backend-1 careermate-backend-2 careermate-backend-3"
