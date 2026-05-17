#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

require_tag
require_kubeconfig

if [[ "${CONFIRM_PROD_APPLY:-}" != "apply-prod" ]]; then
  echo "Refusing manual prod apply without explicit confirmation." >&2
  echo "Use: TAG=$TAG CONFIRM_PROD_APPLY=apply-prod bash ops/scripts/runtime/04-apply-prod.sh" >&2
  echo "For normal releases, prefer the GitHub Actions v* workflow with production approval." >&2
  exit 1
fi

log "rendering and validating prod overlay before apply"
TAG="$TAG" bash ops/scripts/runtime/03-render-prod.sh

log "applying prod overlay for $TAG"
CAMPUS_SECRETS_ROOT="$CAMPUS_SECRETS_ROOT" \
KUBECONFIG="$KUBECONFIG_PATH" \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag "$TAG"

log "showing prod resources"
kubectl --kubeconfig "$KUBECONFIG_PATH" get pods -n "$PROD_NAMESPACE" -o wide
kubectl --kubeconfig "$KUBECONFIG_PATH" get gateway,httproute,clienttrafficpolicy,envoyproxy -n "$PROD_NAMESPACE"
