#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

log "checking helm and prod kubeconfig"
require_command helm
require_kubeconfig
helm version
kubectl --kubeconfig "$KUBECONFIG_PATH" get nodes -o wide

log "ensuring prod namespace exists before Envoy install"
kubectl --kubeconfig "$KUBECONFIG_PATH" create namespace "$PROD_NAMESPACE" \
  --dry-run=client -o yaml | \
  kubectl --kubeconfig "$KUBECONFIG_PATH" apply -f -

log "installing Envoy Gateway in prod cluster"
export KUBECONFIG="$KUBECONFIG_PATH"
run_playbook ops/playbooks/install-envoy-prod.yml

log "verifying Envoy Gateway resources"
kubectl --kubeconfig "$KUBECONFIG_PATH" get pods -n envoy-gateway-system -o wide
kubectl --kubeconfig "$KUBECONFIG_PATH" get gatewayclass
