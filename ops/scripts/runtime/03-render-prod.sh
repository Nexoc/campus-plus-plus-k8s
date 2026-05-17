#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

require_tag
require_kubeconfig

manifest="${MANIFEST_OUT%.*}-${TAG}.yaml"

log "rendering prod overlay for $TAG"
CAMPUS_SECRETS_ROOT="$CAMPUS_SECRETS_ROOT" \
KUBECONFIG="$KUBECONFIG_PATH" \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag "$TAG" \
  --render-only \
  --manifest-out "$manifest"

log "checking rendered manifest with server dry-run"
kubectl --kubeconfig "$KUBECONFIG_PATH" apply \
  -f "$manifest" \
  --dry-run=server

log "rendered manifest"
echo "$manifest"
