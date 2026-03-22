#!/usr/bin/env bash

# verify-overlay.sh
#
# Purpose:
# - verify rollout status for the main application deployments in a target namespace
# - confirm importer completion when the Job still exists
# - check the ingress resource and optionally run an HTTP smoke test
#
# This script is used both for manual verification and for the self-hosted
# DEV deploy workflow after apply has finished.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  deploy/scripts/verify-overlay.sh --environment <dev|prod> [--smoke-url <url>] [--smoke-host-header <host>] [--timeout-seconds <seconds>]

Required:
  --environment     Target namespace selector: dev or prod

Optional:
  --smoke-url       HTTP endpoint to probe after rollout checks
  --smoke-host-header Optional Host header for ingress smoke checks
  --timeout-seconds Timeout used for rollout and job completion checks
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
if [[ -n "$smoke_url" ]]; then
  require_command curl
fi

deployments=(frontend auth backend campus-nginx)

echo "Verifying namespace '$namespace'..."
kubectl -n "$namespace" get pods
kubectl -n "$namespace" get ingress
kubectl -n "$namespace" get jobs

for deployment in "${deployments[@]}"; do
  echo "Waiting for deployment/$deployment rollout..."
  kubectl -n "$namespace" rollout status "deployment/$deployment" --timeout="${timeout_seconds}s"
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

if [[ -n "$smoke_url" ]]; then
  echo "Running smoke check against $smoke_url ..."

  curl_args=(
    -sS
    -o /dev/null
    -w '%{http_code}'
    --max-time "$timeout_seconds"
  )

  if [[ -n "$smoke_host_header" ]]; then
    echo "Using Host header: $smoke_host_header"
    curl_args+=(-H "Host: $smoke_host_header")
  fi

  http_code="$(curl "${curl_args[@]}" "$smoke_url")"

  if [[ "$http_code" -lt 200 || "$http_code" -ge 400 ]]; then
    echo "Smoke check returned HTTP $http_code for '$smoke_url'" >&2
    exit 1
  fi

  echo "Smoke check succeeded with HTTP $http_code."
else
  echo "Smoke URL not provided. Skipping HTTP smoke check."
fi

echo "Verification completed successfully for '$environment'."
