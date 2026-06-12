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
if [[ -f "${SCRIPT_DIR}/../k3s/registries.yaml" ]]; then
  mkdir -p /etc/rancher/k3s
  cp "${SCRIPT_DIR}/../k3s/registries.yaml" "${REGISTRY_FILE}"
  systemctl restart k3s || true
  sleep 10
fi

# Import pause sandbox image via Docker when containerd cannot reach docker.io.
if command -v k3s >/dev/null 2>&1 && command -v docker >/dev/null 2>&1; then
  if ! k3s ctr -n k8s.io images ls | grep -q 'rancher/mirrored-pause:3.6'; then
    docker pull rancher/mirrored-pause:3.6
    docker save rancher/mirrored-pause:3.6 | k3s ctr -n k8s.io images import -
  fi
fi

echo "[2/2] Verify cluster"
k3s kubectl get nodes -o wide
k3s kubectl get pods -A

echo ""
echo "k3s ready. Kubeconfig: /etc/rancher/k3s/k3s.yaml"
