#!/usr/bin/env bash
# Stop legacy Docker backend containers after k8s migration is verified.
# Containers are stopped, NOT removed, for rollback.
#
# Usage (on Server 3):
#   sudo bash deploy/scripts/stop-legacy-careermate-containers.sh
set -euo pipefail

CONTAINERS=(careermate-backend-1 careermate-backend-2 careermate-backend-3)

for name in "${CONTAINERS[@]}"; do
  if docker ps --format '{{.Names}}' | grep -qx "${name}"; then
    echo "Stopping ${name}..."
    docker stop "${name}"
  else
    echo "Skip ${name} (not running)"
  fi
done

echo "Legacy backend containers stopped. Rollback: docker start ${CONTAINERS[*]}"
