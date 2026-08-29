#!/usr/bin/env bash
# Install single-node k3s on Server 3 (app layer).
# Does NOT install Rancher or multi-node cluster.
#
# Usage (on Server 3):
#   sudo bash deploy/scripts/install-k3s-server3.sh
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

if command -v k3s >/dev/null 2>&1; then
  echo "k3s already installed: $(k3s --version | head -n 1)"
else
  echo "[1/2] Installing k3s (single-node, Traefik disabled)..."
  if curl -fsSL --connect-timeout 15 https://get.k3s.io -o /tmp/k3s-install.sh; then
    sh /tmp/k3s-install.sh -s - \
      --write-kubeconfig-mode 644 \
      --disable traefik
  else
    echo "  get.k3s.io unreachable, using rancher CN mirror..."
    curl -fsSL https://rancher-mirror.rancher.cn/k3s/k3s-install.sh -o /tmp/k3s-install.sh
    INSTALL_K3S_MIRROR=cn sh /tmp/k3s-install.sh -s - \
      --write-kubeconfig-mode 644 \
      --disable traefik
  fi
fi

REGISTRY_FILE="/etc/rancher/k3s/registries.yaml"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_REGISTRY="${SCRIPT_DIR}/../k3s/registries.yaml"
if [[ -f "${SOURCE_REGISTRY}" ]]; then
  mkdir -p /etc/rancher/k3s
  if [[ -f "${REGISTRY_FILE}" ]] && cmp -s "${SOURCE_REGISTRY}" "${REGISTRY_FILE}"; then
    echo "registries.yaml unchanged; skip k3s restart"
  else
    cp "${SOURCE_REGISTRY}" "${REGISTRY_FILE}"
    if command -v k3s >/dev/null 2>&1; then
      systemctl restart k3s || true
      sleep 10
    fi
  fi
fi

# Import pause sandbox image when containerd cannot reach docker.io.
# docker 已于 2026-08 从本机下线（k3s 自带 containerd，本来就零依赖），但二进制还在，
# 光靠 `command -v docker` 会误判成可用，随后 docker pull 必然失败 + set -e 直接把整次部署打挂。
# 这里改为：只有守护进程真的连得上才借 docker，否则直接用 containerd 拉；且整段 best-effort——
# pause 镜像多半早已在本地，拉不到也不该阻断部署。
if command -v k3s >/dev/null 2>&1; then
  if ! k3s ctr -n k8s.io images ls | grep -q 'rancher/mirrored-pause:3.6'; then
    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
      docker pull rancher/mirrored-pause:3.6 \
        && docker save rancher/mirrored-pause:3.6 | k3s ctr -n k8s.io images import - \
        || echo "  WARN: pause image import via docker failed; continuing"
    else
      k3s ctr -n k8s.io images pull docker.io/rancher/mirrored-pause:3.6 \
        || echo "  WARN: pause image pull via containerd failed; continuing"
    fi
  fi
fi

echo "[2/2] Verify cluster"
k3s kubectl get nodes -o wide
k3s kubectl get pods -A

echo ""
echo "k3s ready. Kubeconfig: /etc/rancher/k3s/k3s.yaml"
