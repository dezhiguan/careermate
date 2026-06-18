#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

if [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

if [[ -f "${REPO_ROOT}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${REPO_ROOT}/.env"
  set +a
fi

PORT="${SERVER_PORT:-8082}"

if lsof -ti ":${PORT}" >/dev/null 2>&1; then
  echo "[dev-start] killing existing process on :${PORT}"
  lsof -ti ":${PORT}" | xargs kill -9 || true
  sleep 1
fi

cd "${BACKEND_DIR}"
mvn clean package -DskipTests

APP_JAR="$(ls -1 target/careermate-backend-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"
if [[ -z "${APP_JAR}" ]]; then
  echo "[dev-start] no fat jar in target/; mvn package may have failed" >&2
  exit 1
fi

SPRING_PROFILES_ACTIVE=dev \
SERVER_PORT="${PORT}" \
RAGFORGE_TIMEOUT_MS=15000 \
ALIYUN_SMS_ENABLED=true \
ALIYUN_SMS_MOCK_ENABLED=true \
java -jar "${APP_JAR}"
