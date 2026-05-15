#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$REPO_ROOT"
ansible all -i ops/inventory/lab.ini -m ping

