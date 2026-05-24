#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

INVENTORY="${ANSIBLE_INVENTORY:-ops/inventory/lab.local.ini}"
KUBECONFIG_PATH="${KUBECONFIG:-/home/nexoc/.kube/prod.yaml}"
CAMPUS_SECRETS_ROOT="${CAMPUS_SECRETS_ROOT:-/home/nexoc/campus-secrets}"
PROD_NAMESPACE="${PROD_NAMESPACE:-campus-prod}"
EXPECTED_NODEPORT="${EXPECTED_NODEPORT:-30080}"
EXPECTED_HOST="${EXPECTED_HOST:-home-campus-prod.davl.at}"
MANIFEST_OUT="${MANIFEST_OUT:-/tmp/campus-prod-render.yaml}"

cd "$REPO_ROOT"

log() {
  printf '== %s ==\n' "$*"
}

require_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "Missing required file: $path" >&2
    exit 1
  fi
}

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command on gw: $name" >&2
    exit 1
  fi
}

require_inventory() {
  require_file "$INVENTORY"
}

require_kubeconfig() {
  require_file "$KUBECONFIG_PATH"
}

require_tag() {
  if [[ -z "${TAG:-}" ]]; then
    echo "TAG is required, for example: TAG=home-vX.Y.Z" >&2
    exit 1
  fi

  case "$TAG" in
    home-v*) ;;
    *)
      echo "TAG must be a production tag starting with home-v*: $TAG" >&2
      exit 1
      ;;
  esac
}

run_playbook() {
  require_inventory
  ansible-playbook -i "$INVENTORY" "$@"
}
