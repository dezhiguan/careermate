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

wait_http_ok() {
  local name="$1"
  local url="$2"
  local attempts="${3:-60}"
  for i in $(seq 1 "${attempts}"); do
    if curl -sfI "${url}" 2>/dev/null | grep -qi '200\|302'; then
      echo "[skywalking] ${name} ready (${url})"
      return 0
    fi
    sleep 3
  done
  echo "[skywalking] ERROR: ${name} not ready: ${url}" >&2
  docker ps --filter name=skywalking --format 'table {{.Names}}\t{{.Status}}' || true
  return 1
}

if [[ ! -f /opt/skywalking-agent/skywalking-agent.jar ]]; then
  echo "[skywalking] installing Java agent"
  bash "${SCRIPT_DIR}/install-skywalking-agent.sh"
else
  echo "[skywalking] Java agent already installed"
fi

bash "${SCRIPT_DIR}/ensure-skywalking-optional-plugins.sh"

if [[ "${SKIP_PULL:-0}" != "1" ]]; then
  pull_image apache/skywalking-banyandb:0.8.0
  pull_image apache/skywalking-oap-server:10.2.0
  pull_image apache/skywalking-ui:10.2.0
else
  echo "[skywalking] skip image pull (SKIP_PULL=1)"
  for image in apache/skywalking-banyandb:0.8.0 apache/skywalking-oap-server:10.2.0 apache/skywalking-ui:10.2.0; do
    docker image inspect "${image}" >/dev/null || { echo "missing image: ${image}" >&2; exit 1; }
  done
fi

bash "${SCRIPT_DIR}/start-skywalking.sh"

echo "[skywalking] waiting for OAP healthcheck..."
for _ in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:12800/healthcheck" >/dev/null 2>&1; then
    echo "[skywalking] OAP healthcheck OK"
    break
  fi
  sleep 3
done
curl -sf "http://127.0.0.1:12800/healthcheck" >/dev/null

wait_http_ok "UI localhost" "http://127.0.0.1:18088/skywalking/" 40
wait_http_ok "UI private IP" "http://${SKYWALKING_BIND_IP}:18088/skywalking/" 20

echo "[skywalking] stack ready (UI private: http://${SKYWALKING_BIND_IP}:18088/skywalking/)"
