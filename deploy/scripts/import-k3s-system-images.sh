#!/usr/bin/env bash
# Import k3s kube-system images when docker.io is unreachable.
# Uses Rancher CN airgap bundle matching the installed k3s version.
#
# Usage (on Server 3):
#   sudo bash deploy/scripts/import-k3s-system-images.sh
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

if ! command -v k3s >/dev/null 2>&1; then
  echo "ERROR: k3s is not installed" >&2
  exit 1
fi

K3S_VERSION="$(k3s --version | head -n1 | awk '{print $3}')"
TARBALL="/tmp/k3s-airgap-images-amd64.tar.gz"
URL="https://rancher-mirror.rancher.cn/k3s/${K3S_VERSION}/k3s-airgap-images-amd64.tar.gz"

echo "Importing k3s system images for ${K3S_VERSION}..."
curl -fL --connect-timeout 30 --retry 3 -o "${TARBALL}" "${URL}"
k3s ctr -n k8s.io images import "${TARBALL}"

if command -v docker >/dev/null 2>&1; then
  if ! k3s ctr -n k8s.io images ls | grep -q 'rancher/mirrored-pause:3.6'; then
    docker pull rancher/mirrored-pause:3.6
    docker save rancher/mirrored-pause:3.6 | k3s ctr -n k8s.io images import -
  fi
fi

k3s kubectl delete pod -n kube-system --all --force --grace-period=0 2>/dev/null || true
sleep 20
k3s kubectl get pods -A
