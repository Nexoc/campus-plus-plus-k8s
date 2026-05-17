#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

log "checking gw tools"
require_command git
require_command ansible
require_command ansible-playbook
require_command kubectl
require_command helm
require_command envsubst
require_command curl

git --version
ansible --version | head -n 1
ansible-playbook --version | head -n 1
kubectl version --client
helm version

log "checking runtime inventory"
require_inventory
ansible-inventory -i "$INVENTORY" --graph

log "checking ansible reachability"
ansible all -i "$INVENTORY" -m ping

log "running connectivity playbook"
run_playbook ops/playbooks/check-connectivity.yml

log "checking prod kubeconfig if present"
if [[ -f "$KUBECONFIG_PATH" ]]; then
  kubectl --kubeconfig "$KUBECONFIG_PATH" get nodes -o wide
else
  echo "warning: missing kubeconfig: $KUBECONFIG_PATH" >&2
  echo "Create it before running prod cluster or Envoy steps." >&2
fi
