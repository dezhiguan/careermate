#!/usr/bin/env bash
# Ensure SkyWalking BanyanDB + OAP + UI are running on Server 3.
# Idempotent: safe to run on every deploy.
#
# Usage:
#   sudo SKYWALKING_BIND_IP=172.25.90.184 bash deploy/scripts/ensure-skywalking-stack.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export SKYWALKING_BIND_IP="${SKYWALKING_BIND_IP:-172.25.90.184}"

pull_image() {
  local image="$1"
  if docker image inspect "${image}" >/dev/null 2>&1; then
    echo "[skywalking] image present: ${image}"
    return 0
  fi
  echo "[skywalking] pulling ${image}..."
  if docker pull "${image}"; then
    return 0
  fi
  local mirror="docker.m.daocloud.io/${image}"
  echo "[skywalking] retry via mirror: ${mirror}"
  docker pull "${mirror}"
  docker tag "${mirror}" "${image}"
}

if [[ ! -f /opt/skywalking-agent/skywalking-agent.jar ]]; then
  echo "[skywalking] installing Java agent"
  bash "${SCRIPT_DIR}/install-skywalking-agent.sh"
else
  echo "[skywalking] Java agent already installed"
fi

pull_image apache/skywalking-banyandb:0.8.0
pull_image apache/skywalking-oap-server:10.2.0
pull_image apache/skywalking-ui:10.2.0

bash "${SCRIPT_DIR}/start-skywalking.sh"

echo "[skywalking] waiting for OAP..."
for _ in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:12800/healthcheck" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -sf "http://127.0.0.1:12800/healthcheck" >/dev/null

echo "[skywalking] waiting for UI..."
for _ in $(seq 1 30); do
  if curl -sfI "http://127.0.0.1:18088/skywalking/" 2>/dev/null | grep -qi '200\|302'; then
    break
  fi
  sleep 2
done
curl -sfI "http://127.0.0.1:18088/skywalking/" | head -1
curl -sfI "http://${SKYWALKING_BIND_IP}:18088/skywalking/" | head -1

echo "[skywalking] stack ready (UI private: http://${SKYWALKING_BIND_IP}:18088/skywalking/)"
