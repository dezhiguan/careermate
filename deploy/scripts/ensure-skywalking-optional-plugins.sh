#!/usr/bin/env bash
# Copy required SkyWalking optional plugins into plugins/ (idempotent).
set -euo pipefail

INSTALL_DIR="${SKYWALKING_AGENT_HOME:-/opt/skywalking-agent}"

if [[ ! -d "${INSTALL_DIR}" ]]; then
  echo "[skywalking] agent dir missing: ${INSTALL_DIR}" >&2
  exit 1
fi

mkdir -p "${INSTALL_DIR}/plugins"

enable_optional_plugin() {
  local prefix="$1"
  local src
  src="$(ls "${INSTALL_DIR}/optional-plugins/${prefix}"-*.jar 2>/dev/null | head -1 || true)"
  if [[ -z "${src}" || ! -f "${src}" ]]; then
    echo "[skywalking] WARN: optional plugin not found: ${prefix}-*.jar" >&2
    return 0
  fi
  cp -f "${src}" "${INSTALL_DIR}/plugins/"
  echo "[skywalking] enabled optional plugin: $(basename "${src}")"
}

enable_optional_plugin "apm-resttemplate-6.x-plugin"
