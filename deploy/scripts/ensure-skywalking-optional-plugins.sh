#!/usr/bin/env bash
# Copy required SkyWalking optional plugins into plugins/ (idempotent).
set -euo pipefail

AGENT_VERSION="${SKYWALKING_AGENT_VERSION:-9.3.0}"
INSTALL_DIR="${SKYWALKING_AGENT_HOME:-/opt/skywalking-agent}"

if [[ ! -d "${INSTALL_DIR}" ]]; then
  echo "[skywalking] agent dir missing: ${INSTALL_DIR}" >&2
  exit 1
fi

mkdir -p "${INSTALL_DIR}/plugins"

enable_optional_plugin() {
  local jar_name="$1"
  local src="${INSTALL_DIR}/optional-plugins/${jar_name}-${AGENT_VERSION}.jar"
  if [[ ! -f "${src}" ]]; then
    echo "[skywalking] WARN: optional plugin not found: ${src}" >&2
    return 0
  fi
  cp -f "${src}" "${INSTALL_DIR}/plugins/"
  echo "[skywalking] enabled optional plugin: ${jar_name}"
}

enable_optional_plugin "apm-resttemplate-6.x-plugin"
