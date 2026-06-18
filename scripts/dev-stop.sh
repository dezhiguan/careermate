#!/usr/bin/env bash
set -euo pipefail

PORTS=(8080 8081 8082)

for p in "${PORTS[@]}"; do
  pid="$(lsof -ti ":${p}" 2>/dev/null || true)"
  if [[ -n "${pid}" ]]; then
    for x in ${pid}; do
      if ps -p "${x}" -o command= | grep -q careermate; then
        echo "[dev-stop] killing ${x} on :${p}"
        kill -9 "${x}"
      fi
    done
  fi
done
