#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT"

INVENTORY="${ANSIBLE_INVENTORY:-ops/inventory/home.local.ini}"

if [[ ! -f "$INVENTORY" ]]; then
  echo "Missing Ansible inventory: $INVENTORY" >&2
  echo "Create it from ops/inventory/home.example.ini and keep real addresses out of git." >&2
  exit 1
fi

ansible all -i "$INVENTORY" -m ping
