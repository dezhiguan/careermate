#!/usr/bin/env bash
# Build CareerMate backend/frontend images on Server 3 and import into k3s containerd.
#
# Usage (from careermate repo root on Server 3):
#   bash deploy/scripts/build-careermate-k8s-images.sh
#   SKIP_BACKEND_BUILD=1 bash deploy/scripts/build-careermate-k8s-images.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

BACKEND_IMAGE="${CAREERMATE_BACKEND_IMAGE:-careermate/backend:latest}"
FRONTEND_IMAGE="${CAREERMATE_FRONTEND_IMAGE:-careermate/frontend:latest}"
JAR_FILE="${CAREERMATE_BACKEND_JAR:-${REPO_ROOT}/backend/target/careermate-backend-0.1.0.jar}"

cd "${REPO_ROOT}"

import_image() {
  local image="$1"
  if command -v k3s >/dev/null 2>&1; then
    docker save "${image}" | sudo k3s ctr images import -
  else
    echo "WARN: k3s not found; skip ctr import for ${image}" >&2
  fi
}

if [[ "${SKIP_BACKEND_BUILD:-0}" != "1" ]]; then
  if command -v mvn >/dev/null 2>&1; then
    echo "[backend] mvn package"
    (cd backend && mvn -DskipTests package)
  elif [[ -f "${JAR_FILE}" ]]; then
    echo "[backend] mvn not found; using existing JAR: ${JAR_FILE}"
  else
    echo "ERROR: mvn not found and backend JAR missing: ${JAR_FILE}" >&2
    echo "Set SKIP_BACKEND_BUILD=1 after placing the JAR, or install Maven on the build host." >&2
    exit 1
  fi
fi

if [[ ! -f "${JAR_FILE}" ]]; then
  echo "ERROR: backend JAR not found: ${JAR_FILE}" >&2
  exit 1
fi

echo "[backend] docker build -> ${BACKEND_IMAGE}"
docker build -f backend/Dockerfile -t "${BACKEND_IMAGE}" backend/
import_image "${BACKEND_IMAGE}"

echo "[frontend] docker build -> ${FRONTEND_IMAGE}"
docker build -f frontend/careermate/Dockerfile \
  --build-arg VITE_API_BASE_URL=/careermate-api \
  --build-arg VITE_BASE_PATH=/careermate/ \
  -t "${FRONTEND_IMAGE}" .
import_image "${FRONTEND_IMAGE}"

echo ""
echo "Images ready:"
docker images | awk 'NR==1 || /careermate\/(backend|frontend)/'
