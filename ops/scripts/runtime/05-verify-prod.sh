#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

require_kubeconfig
export KUBECONFIG="$KUBECONFIG_PATH"

log "verifying prod release"
run_playbook ops/playbooks/verify-prod-release.yml

log "checking prod cluster"
if [[ -n "${TAG:-}" ]]; then
  run_playbook ops/playbooks/check-prod-cluster.yml -e "expected_tag=$TAG"
else
  run_playbook ops/playbooks/check-prod-cluster.yml
fi

log "smoke testing prod nodes"
for node in s1-prod s2-prod s3-prod; do
  curl -fsS -o /dev/null -H "Host: $EXPECTED_HOST" "http://$node:$EXPECTED_NODEPORT/"
  echo "ok: $node:$EXPECTED_NODEPORT -> $EXPECTED_HOST"
done
