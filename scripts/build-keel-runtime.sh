#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"
(cd agent/vendor && shasum -a 256 -c SHA256SUMS)
image=${CAREERMATE_AGENT_IMAGE:-careermate-agent:$(git rev-parse --short=12 HEAD)}
docker build -t "$image" agent
