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

require_key() {
  local file="$1"
  local key="$2"

  if ! grep -Eq "^${key}=.+" "$file"; then
    echo "Missing or empty key $key in $file" >&2
    exit 1
  fi
}

require_key "$CAMPUS_SECRETS_ROOT/prod/db-secrets.env" "DB_USERNAME"
require_key "$CAMPUS_SECRETS_ROOT/prod/db-secrets.env" "DB_PASSWORD"
require_key "$CAMPUS_SECRETS_ROOT/prod/auth-secrets.env" "JWT_SECRET"
require_key "$CAMPUS_SECRETS_ROOT/prod/auth-secrets.env" "JWT_EXPIRATION"
require_key "$CAMPUS_SECRETS_ROOT/prod/db-endpoint.env" "DB_ENDPOINT_ADDRESS"
require_key "$CAMPUS_SECRETS_ROOT/prod/db-endpoint.env" "DB_ENDPOINT_PORT"

log "checking kubeconfig"
require_kubeconfig
kubectl --kubeconfig "$KUBECONFIG_PATH" get nodes -o wide

log "runtime files check ok"
