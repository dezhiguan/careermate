#!/usr/bin/env bash
# Download Apache SkyWalking Java Agent to /opt/skywalking-agent (no secrets).
set -euo pipefail

AGENT_VERSION="${SKYWALKING_AGENT_VERSION:-9.3.0}"
INSTALL_DIR="${SKYWALKING_AGENT_HOME:-/opt/skywalking-agent}"
ARCHIVE="apache-skywalking-java-agent-${AGENT_VERSION}.tgz"
BASE_URL="https://archive.apache.org/dist/skywalking/java-agent/${AGENT_VERSION}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root or with sudo to install under ${INSTALL_DIR}" >&2
  exit 1
fi

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

echo "Downloading SkyWalking Java Agent ${AGENT_VERSION}..."
curl -fsSL "${BASE_URL}/${ARCHIVE}" -o "${tmpdir}/${ARCHIVE}"
tar -xzf "${tmpdir}/${ARCHIVE}" -C "${tmpdir}"

src_dir="${tmpdir}/skywalking-agent"
if [[ ! -f "${src_dir}/skywalking-agent.jar" ]]; then
  echo "skywalking-agent.jar not found in archive" >&2
  exit 1
fi

mkdir -p "$(dirname "${INSTALL_DIR}")"
rm -rf "${INSTALL_DIR}.bak"
[[ -d "${INSTALL_DIR}" ]] && mv "${INSTALL_DIR}" "${INSTALL_DIR}.bak"
mv "${src_dir}" "${INSTALL_DIR}"
chown -R root:root "${INSTALL_DIR}"

# Spring 6 / RestTemplate 6.x plugin (required for sw8 on CareerMate → RAGForge RestTemplate calls).
optional_plugin="optional-plugins/apm-resttemplate-6.x-plugin-${AGENT_VERSION}.jar"
if [[ -f "${INSTALL_DIR}/${optional_plugin}" ]]; then
  cp -f "${INSTALL_DIR}/${optional_plugin}" "${INSTALL_DIR}/plugins/"
  echo "Enabled optional plugin: apm-resttemplate-6.x-plugin"
else
  echo "WARN: ${optional_plugin} not found; copy manually for RestTemplate sw8 propagation" >&2
fi

echo "Installed: ${INSTALL_DIR}/skywalking-agent.jar"
echo "Set JAVA_TOOL_OPTIONS or -javaagent in systemd (see deploy/systemd/careermate-backend.service.example)"
