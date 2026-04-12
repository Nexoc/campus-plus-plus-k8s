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

current_epoch_seconds() {
  date +%s
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
  require_command mktemp
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

print_gateway_diagnostics() {
  echo "Gateway API diagnostics for namespace '$namespace'..."
  kubectl -n "$namespace" get gateway,httproute,envoyproxy,clienttrafficpolicy || true
  kubectl -n "$namespace" describe gateway campus || true
  kubectl -n "$namespace" describe httproute campus || true
  kubectl -n "$namespace" get envoyproxy campus-edge -o yaml || true
  kubectl -n "$namespace" get clienttrafficpolicy campus-edge -o yaml || true
  kubectl -n envoy-gateway-system get pods,svc || true
  kubectl -n envoy-gateway-system logs deployment/envoy-gateway --tail=100 || true
}

wait_until() {
  local description="$1"
  local timeout="$2"
  shift 2
  local deadline=$(( $(current_epoch_seconds) + timeout ))

  echo "Waiting for $description ..."
  while true; do
    if "$@"; then
      echo "$description is ready."
      return 0
    fi

    if (( $(current_epoch_seconds) >= deadline )); then
      echo "Timed out waiting for $description." >&2
      return 1
    fi

    sleep 5
  done
}

gateway_has_condition() {
  local type="$1"
  local expected_status="$2"

  kubectl -n "$namespace" get gateway campus \
    -o jsonpath='{range .status.conditions[*]}{.type}{"="}{.status}{"\n"}{end}' 2>/dev/null \
    | grep -q "^${type}=${expected_status}$"
}

httproute_has_condition() {
  local type="$1"
  local expected_status="$2"

  kubectl -n "$namespace" get httproute campus \
    -o jsonpath='{range .status.parents[*].conditions[*]}{.type}{"="}{.status}{"\n"}{end}' 2>/dev/null \
    | grep -q "^${type}=${expected_status}$"
}

envoyproxy_exists() {
  kubectl -n "$namespace" get envoyproxy campus-edge >/dev/null 2>&1
}

clienttrafficpolicy_exists() {
  kubectl -n "$namespace" get clienttrafficpolicy campus-edge >/dev/null 2>&1
}

envoy_nodeport_is_published() {
  local envoy_smoke_port="${1:-}"

  [[ -n "$envoy_smoke_port" ]] || return 0

  kubectl get svc -A \
    -o jsonpath='{range .items[*]}{.spec.type}{"|"}{range .spec.ports[*]}{.nodePort}{" "}{end}{"\n"}{end}' 2>/dev/null \
    | grep -Eq "^NodePort\\|.*([^0-9]|^)${envoy_smoke_port}([^0-9]|$)"
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

  local deadline=$(( $(current_epoch_seconds) + timeout_seconds ))
  local attempt=1

  while true; do
    local curl_stderr
    curl_stderr="$(mktemp)"

    local http_code=""
    local curl_exit_code=0

    set +e
    http_code="$(curl "${curl_args[@]}" "$url" 2>"$curl_stderr")"
    curl_exit_code=$?
    set -e

    if [[ "$curl_exit_code" -eq 0 && "$http_code" =~ ^[0-9]+$ && "$http_code" -ge 200 && "$http_code" -lt 400 ]]; then
      rm -f "$curl_stderr"
      echo "$label smoke check succeeded with HTTP $http_code."
      return 0
    fi

    if (( $(current_epoch_seconds) >= deadline )); then
      if [[ -s "$curl_stderr" ]]; then
        cat "$curl_stderr" >&2
      fi
      rm -f "$curl_stderr"
      echo "$label smoke check failed after ${attempt} attempts for '$url'." >&2
      if [[ -n "$http_code" ]]; then
        echo "$label smoke check last HTTP code: $http_code" >&2
      fi
      return 1
    fi

    rm -f "$curl_stderr"
    attempt=$(( attempt + 1 ))
    sleep 5
  done
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

  if ! wait_until "EnvoyProxy/campus-edge" "$timeout_seconds" envoyproxy_exists; then
    print_gateway_diagnostics
    exit 1
  fi

  if ! wait_until "ClientTrafficPolicy/campus-edge" "$timeout_seconds" clienttrafficpolicy_exists; then
    print_gateway_diagnostics
    exit 1
  fi

  if ! wait_until "Gateway/campus Accepted=True" "$timeout_seconds" gateway_has_condition Accepted True; then
    print_gateway_diagnostics
    exit 1
  fi

  if ! wait_until "HTTPRoute/campus Accepted=True" "$timeout_seconds" httproute_has_condition Accepted True; then
    print_gateway_diagnostics
    exit 1
  fi

  if ! wait_until "HTTPRoute/campus ResolvedRefs=True" "$timeout_seconds" httproute_has_condition ResolvedRefs True; then
    print_gateway_diagnostics
    exit 1
  fi

  if ! wait_until "Gateway/campus Programmed=True" "$timeout_seconds" gateway_has_condition Programmed True; then
    print_gateway_diagnostics
    exit 1
  fi

  envoy_smoke_port=""
  if [[ "$envoy_smoke_url" =~ :([0-9]+)/?$ ]]; then
    envoy_smoke_port="${BASH_REMATCH[1]}"
  fi

  if [[ -n "$envoy_smoke_port" ]]; then
    if ! wait_until "Envoy NodePort ${envoy_smoke_port}" "$timeout_seconds" envoy_nodeport_is_published "${envoy_smoke_port}"; then
      print_gateway_diagnostics
      exit 1
    fi
  fi
fi

if ! run_smoke_check "Primary ingress" "$smoke_url" "$smoke_host_header"; then
  exit 1
fi

if [[ -z "$smoke_url" ]]; then
  echo "Primary smoke URL not provided. Skipping primary ingress smoke check."
fi

if ! run_smoke_check "Envoy staging" "$envoy_smoke_url" "${envoy_smoke_host_header:-$smoke_host_header}"; then
  print_gateway_diagnostics
  exit 1
fi

if [[ -z "$envoy_smoke_url" ]]; then
  echo "Envoy staging smoke URL not provided. Skipping Envoy staging smoke check."
fi

echo "Verification completed successfully for '$environment'."
