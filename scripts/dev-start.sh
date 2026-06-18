#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

if [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

cd "${BACKEND_DIR}"

mvn clean package -DskipTests

SPRING_PROFILES_ACTIVE=dev \
SERVER_PORT=8082 \
ALIYUN_SMS_ENABLED=true \
ALIYUN_SMS_MOCK_ENABLED=true \
java -jar target/*.jar
