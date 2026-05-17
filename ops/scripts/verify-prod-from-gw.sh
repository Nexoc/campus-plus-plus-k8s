#!/usr/bin/env bash
set -euo pipefail

# Execute from: gw

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

KUBECONFIG_PATH="${KUBECONFIG:-/home/nexoc/.kube/prod.yaml}"
EXPECTED_NODEPORT="${EXPECTED_NODEPORT:-30080}"
EXPECTED_HOST="${EXPECTED_HOST:-campus-prod.10-123-127-29.sslip.io}"
PROD_NAMESPACE="${PROD_NAMESPACE:-campus-prod}"

cd "$REPO_ROOT"

kubectl --kubeconfig "$KUBECONFIG_PATH" get nodes -o wide
kubectl --kubeconfig "$KUBECONFIG_PATH" -n "$PROD_NAMESPACE" get deploy,job,svc -o wide
kubectl --kubeconfig "$KUBECONFIG_PATH" -n "$PROD_NAMESPACE" get gateway,httproute,envoyproxy,clienttrafficpolicy -o wide
kubectl --kubeconfig "$KUBECONFIG_PATH" -n "$PROD_NAMESPACE" get service s4-db
kubectl --kubeconfig "$KUBECONFIG_PATH" -n "$PROD_NAMESPACE" get endpointslice s4-db

bash deploy/scripts/verify-overlay.sh \
  --environment prod \
  --expected-nodeport "$EXPECTED_NODEPORT"

for node in s1-prod s2-prod s3-prod; do
  curl -fsS -o /dev/null -H "Host: $EXPECTED_HOST" "http://$node:$EXPECTED_NODEPORT/"
  echo "ok: $node:$EXPECTED_NODEPORT -> $EXPECTED_HOST"
done
