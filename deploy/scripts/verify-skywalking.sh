#!/usr/bin/env bash
# Verify SkyWalking trace headers and UI reachability.
set -euo pipefail

INGRESS_URL="${INGRESS_URL:-http://8.163.63.222}"
APP_HOST="${APP_HOST:-172.25.90.184}"

fail=0

check() {
  local name="$1"
  shift
  if "$@"; then
    echo "OK  ${name}"
  else
    echo "FAIL ${name}" >&2
    fail=1
  fi
}

echo "=== SkyWalking verification ==="

check "OAP health (Server 3)" curl -sf "http://${APP_HOST}:12800/healthcheck" >/dev/null
check "UI private (Server 3)" curl -sfI "http://${APP_HOST}:18088/skywalking/" | grep -qi '200\|302'
check "UI ingress" curl -sfI "${INGRESS_URL}/skywalking/" | grep -qi '200\|302'

echo ""
echo "--- CareerMate trace headers ---"
CM_HEADERS="$(curl -sI "${INGRESS_URL}/careermate-api/health")"
echo "${CM_HEADERS}" | grep -i 'x-trace-id\|x-request-id' || { echo "missing trace headers" >&2; fail=1; }

echo ""
echo "--- RAG trace headers ---"
RAG_HEADERS="$(curl -sI "${INGRESS_URL}/api/v1/health")"
echo "${RAG_HEADERS}" | grep -i 'x-trace-id\|x-request-id' || { echo "missing trace headers" >&2; fail=1; }

exit "${fail}"
