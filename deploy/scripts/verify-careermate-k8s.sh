#!/usr/bin/env bash
# Smoke checks after CareerMate k8s deployment.
#
# Usage:
#   bash deploy/scripts/verify-careermate-k8s.sh
#   CAREERMATE_APP_HOST=172.25.90.184 bash deploy/scripts/verify-careermate-k8s.sh
set -euo pipefail

APP_HOST="${CAREERMATE_APP_HOST:-172.25.90.184}"
FRONTEND_PORT="${CAREERMATE_FRONTEND_NODEPORT:-31000}"
BACKEND_PORT="${CAREERMATE_BACKEND_NODEPORT:-31080}"

echo "== k3s pods =="
if command -v k3s >/dev/null 2>&1; then
  sudo k3s kubectl -n careermate get pods -o wide
  sudo k3s kubectl -n careermate get svc
else
  echo "WARN: k3s not installed on this host" >&2
fi

echo ""
echo "== backend health =="
curl -fsS "http://${APP_HOST}:${BACKEND_PORT}/api/health" | head -c 200
echo ""

echo ""
echo "== frontend root =="
curl -fsSI "http://${APP_HOST}:${FRONTEND_PORT}/" | head -n 5

echo ""
echo "Manual checks on Server 2 ingress:"
echo "  - open https://careerforge.cn/#/login"
echo "  - login, AI chat, resume PDF/Word download"
echo "  - confirm backend connects to PostgreSQL/Redis/ES/RocketMQ on Server 1"
