#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

log "checking prod runtime files without printing values"

prod_files=(
  "$CAMPUS_SECRETS_ROOT/prod/db-secrets.env"
  "$CAMPUS_SECRETS_ROOT/prod/auth-secrets.env"
  "$CAMPUS_SECRETS_ROOT/prod/db-endpoint.env"
)

for file in "${prod_files[@]}"; do
  require_file "$file"
  ls -l "$file"
  awk -F= '
    /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
    NF >= 2 { print $1 ": length=" length($2) }
  ' "$file"
done

log "checking required prod runtime keys"
grep -q '^DB_USERNAME=' "$CAMPUS_SECRETS_ROOT/prod/db-secrets.env"
grep -q '^DB_PASSWORD=' "$CAMPUS_SECRETS_ROOT/prod/db-secrets.env"
grep -q '^JWT_SECRET=' "$CAMPUS_SECRETS_ROOT/prod/auth-secrets.env"
grep -q '^JWT_EXPIRATION=' "$CAMPUS_SECRETS_ROOT/prod/auth-secrets.env"
grep -q '^DB_ENDPOINT_ADDRESS=' "$CAMPUS_SECRETS_ROOT/prod/db-endpoint.env"
grep -q '^DB_ENDPOINT_PORT=' "$CAMPUS_SECRETS_ROOT/prod/db-endpoint.env"

log "checking kubeconfig"
require_kubeconfig
kubectl --kubeconfig "$KUBECONFIG_PATH" get nodes -o wide

log "checking bootstrap-gw expectations"
run_playbook ops/playbooks/bootstrap-gw.yml
