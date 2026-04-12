#!/usr/bin/env bash

# verify-overlay.sh
#
# Purpose:
# - verify rollout status for the main application deployments in a target namespace
# - confirm importer completion when the Job still exists
# - check the ingress resource and optionally run one or more HTTP smoke tests
#
# This script is used both for manual verification and for the self-hosted
# DEV deploy workflow after apply has finished.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  deploy/scripts/verify-overlay.sh --environment <dev|prod> [--smoke-url <url>] [--smoke-host-header <host>] [--envoy-smoke-url <url>] [--envoy-smoke-host-header <host>] [--timeout-seconds <seconds>]

Required:
  --environment     Target namespace selector: dev or prod

Optional:
  --smoke-url               HTTP endpoint to probe after rollout checks
  --smoke-host-header       Optional Host header for the primary ingress smoke check
  --envoy-smoke-url         Optional HTTP endpoint for the Envoy staging smoke check
  --envoy-smoke-host-header Optional Host header for the Envoy staging smoke check
  --timeout-seconds         Timeout used for rollout and job completion checks
EOF
}

require_command() {
  local name="$1"
  command -v "$name" >/dev/null 2>&1 || {
    echo "Required command not found: $name" >&2
    exit 1
  }
}

environment=""
smoke_url=""
smoke_host_header=""
envoy_smoke_url=""
envoy_smoke_host_header=""
timeout_seconds="180"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment)
      environment="${2:-}"
      shift 2
      ;;
    --smoke-url)
      smoke_url="${2:-}"
      shift 2
      ;;
    --smoke-host-header)
      smoke_host_header="${2:-}"
      shift 2
      ;;
    --envoy-smoke-url)
      envoy_smoke_url="${2:-}"
      shift 2
      ;;
    --envoy-smoke-host-header)
      envoy_smoke_host_header="${2:-}"
      shift 2
      ;;
    --timeout-seconds)
      timeout_seconds="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "$environment" != "dev" && "$environment" != "prod" ]]; then
  echo "--environment must be either 'dev' or 'prod'" >&2
  usage
  exit 1
fi

if [[ ! "$timeout_seconds" =~ ^[0-9]+$ ]]; then
  echo "--timeout-seconds must be a positive integer" >&2
  usage
  exit 1
fi

case "$environment" in
  dev) namespace="campus-dev" ;;
  prod) namespace="campus-prod" ;;
esac

require_command kubectl
if [[ -n "$smoke_url" || -n "$envoy_smoke_url" ]]; then
  require_command curl
fi

deployments=(frontend auth backend campus-nginx)

print_rollout_diagnostics() {
  local deployment="$1"

  echo "Rollout for deployment/$deployment failed. Collecting diagnostics..."
  kubectl -n "$namespace" describe "deployment/$deployment" || true
  kubectl -n "$namespace" get pods -l "app.kubernetes.io/name=$deployment" -o wide || true

  local pod_names=()
  while IFS= read -r pod_name; do
    [[ -n "$pod_name" ]] || continue
    pod_names+=("$pod_name")
  done < <(
    kubectl -n "$namespace" get pods -l "app.kubernetes.io/name=$deployment" \
      -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true
  )

  for pod_name in "${pod_names[@]}"; do
    echo "Describing pod/$pod_name ..."
    kubectl -n "$namespace" describe "pod/$pod_name" || true
  done
}

run_smoke_check() {
  local label="$1"
  local url="$2"
  local host_header="$3"

  [[ -n "$url" ]] || return 0

  echo "Running $label smoke check against $url ..."

  local -a curl_args=(
    -sS
    -o /dev/null
    -w '%{http_code}'
    --max-time "$timeout_seconds"
  )

  if [[ -n "$host_header" ]]; then
    echo "Using Host header for $label smoke check: $host_header"
    curl_args+=(-H "Host: $host_header")
  fi

  local http_code
  http_code="$(curl "${curl_args[@]}" "$url")"

  if [[ "$http_code" -lt 200 || "$http_code" -ge 400 ]]; then
    echo "$label smoke check returned HTTP $http_code for '$url'" >&2
    exit 1
  fi

  echo "$label smoke check succeeded with HTTP $http_code."
}

echo "Verifying namespace '$namespace'..."
kubectl -n "$namespace" get pods
kubectl -n "$namespace" get ingress
kubectl -n "$namespace" get jobs

for deployment in "${deployments[@]}"; do
  echo "Waiting for deployment/$deployment rollout..."
  if ! kubectl -n "$namespace" rollout status "deployment/$deployment" --timeout="${timeout_seconds}s"; then
    print_rollout_diagnostics "$deployment"
    exit 1
  fi
done

echo "Waiting for importer job completion..."
if kubectl -n "$namespace" get job campus-importer >/dev/null 2>&1; then
  kubectl -n "$namespace" wait --for=condition=complete job/campus-importer --timeout="${timeout_seconds}s"
else
  echo "Importer job not found in namespace '$namespace'."
  echo "Continuing verification because the Job may have been garbage-collected after completion."
  echo "Campus++ importer currently uses ttlSecondsAfterFinished, so this is expected when verification runs later."
fi

echo "Checking ingress resource..."
kubectl -n "$namespace" get ingress campus

if [[ -n "$envoy_smoke_url" ]]; then
  echo "Checking Gateway API resources..."
  kubectl -n "$namespace" get gateway campus
  kubectl -n "$namespace" get httproute campus
  kubectl -n "$namespace" get envoyproxy campus-edge
  kubectl -n "$namespace" get clienttrafficpolicy campus-edge
fi

run_smoke_check "Primary ingress" "$smoke_url" "$smoke_host_header"

if [[ -z "$smoke_url" ]]; then
  echo "Primary smoke URL not provided. Skipping primary ingress smoke check."
fi

run_smoke_check "Envoy staging" "$envoy_smoke_url" "${envoy_smoke_host_header:-$smoke_host_header}"

if [[ -z "$envoy_smoke_url" ]]; then
  echo "Envoy staging smoke URL not provided. Skipping Envoy staging smoke check."
fi

echo "Verification completed successfully for '$environment'."
