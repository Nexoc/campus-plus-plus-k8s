#!/usr/bin/env bash

# apply-overlay.sh
#
# Purpose:
# - render the selected Kustomize overlay with a concrete immutable image tag
# - validate that required config files exist and secret files are available
#   either in the overlay or via a fixed host secret path staged into a
#   temporary overlay copy
# - optionally apply the rendered manifest to the target Kubernetes namespace
# - show the current Envoy/Gateway API rollout resources after apply
# - for PROD, render a Kubernetes DNS alias for the external PostgreSQL endpoint
#   from host-local runtime config without committing environment IPs
#
# This script is used both for manual operator runs and for the self-hosted
# non-prod release workflow.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  deploy/scripts/apply-overlay.sh --environment <dev|home|prod> --image-tag <tag> [--render-only] [--manifest-out <path>]

Required:
  --environment   Target overlay: dev, home, or prod
  --image-tag     Immutable image tag to inject, for example uni-dev-example

Optional:
  --render-only   Render the overlay but do not run kubectl apply
  --manifest-out  Path for the rendered manifest output

Environment:
  CAMPUS_HTTPROUTE_HOSTNAME  Optional hostname override for the rendered HTTPRoute
EOF
}

require_command() {
  local name="$1"
  command -v "$name" >/dev/null 2>&1 || {
    echo "Required command not found: $name" >&2
    exit 1
  }
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || {
    echo "Required file not found: $path" >&2
    exit 1
  }
}

environment=""
image_tag=""
render_only="false"
manifest_out=""
host_secrets_root="${CAMPUS_SECRETS_ROOT:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment)
      environment="${2:-}"
      shift 2
      ;;
    --image-tag)
      image_tag="${2:-}"
      shift 2
      ;;
    --render-only)
      render_only="true"
      shift
      ;;
    --manifest-out)
      manifest_out="${2:-}"
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

if [[ "$environment" != "dev" && "$environment" != "home" && "$environment" != "prod" ]]; then
  echo "--environment must be one of 'dev', 'home', or 'prod'" >&2
  usage
  exit 1
fi

if [[ ! "$image_tag" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "--image-tag must match ^[A-Za-z0-9._-]+$" >&2
  usage
  exit 1
fi

case "$environment" in
  dev) namespace="campus-dev" ;;
  # The home cluster mirrors the DEV namespace layout and differs at the edge layer.
  home) namespace="campus-dev" ;;
  prod) namespace="campus-prod" ;;
esac

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
overlay_path="$repo_root/deploy/app/overlays/$environment"

require_command kubectl
require_command mktemp
require_command sed
require_command install

stage_host_secret_file() {
  local source_path="$1"
  local target_path="$2"

  [[ -f "$source_path" ]] || {
    echo "Required host secret file not found: $source_path" >&2
    exit 1
  }

  mkdir -p "$(dirname "$target_path")"
  install -m 600 "$source_path" "$target_path"
}

trim_value() {
  local value="$1"
  printf '%s' "$value" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//'
}

read_env_value() {
  local source_path="$1"
  local key="$2"
  local line=""
  local name=""
  local value=""

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*$ ]] && continue
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" == *"="* ]] || continue

    name="${line%%=*}"
    name="$(trim_value "$name")"
    [[ "$name" == "$key" ]] || continue

    value="${line#*=}"
    value="${value%%#*}"
    value="$(trim_value "$value")"

    if [[ "${#value}" -ge 2 && "$value" == \"*\" && "$value" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${#value}" -ge 2 && "$value" == \'* && "$value" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi

    printf '%s' "$value"
    return 0
  done <"$source_path"

  return 0
}

validate_ipv4_address() {
  local address="$1"
  local part=""
  local parts=()

  [[ "$address" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || return 1

  IFS='.' read -r -a parts <<<"$address"
  [[ "${#parts[@]}" -eq 4 ]] || return 1

  for part in "${parts[@]}"; do
    [[ "$part" =~ ^[0-9]+$ ]] || return 1
    (( part >= 0 && part <= 255 )) || return 1
  done
}

validate_tcp_port() {
  local port="$1"

  [[ "$port" =~ ^[0-9]+$ ]] || return 1
  (( port >= 1 && port <= 65535 ))
}

validate_hostname() {
  local hostname="$1"

  [[ "$hostname" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*[A-Za-z0-9]$ ]] || return 1
  [[ "$hostname" == *..* ]] && return 1
  [[ "$hostname" == *.* ]] || return 1
}

write_prod_db_endpoint_manifest() {
  local target_path="$1"
  local endpoint_address="$2"
  local endpoint_port="$3"

  cat >"$target_path" <<EOF
apiVersion: v1
kind: Service
metadata:
  name: s4-db
  namespace: $namespace
  labels:
    app.kubernetes.io/name: s4-db
    app.kubernetes.io/component: database
    app.kubernetes.io/part-of: campus-plus-plus
spec:
  type: ClusterIP
  ports:
    - name: postgres
      port: $endpoint_port
      targetPort: $endpoint_port
      protocol: TCP
---
apiVersion: discovery.k8s.io/v1
kind: EndpointSlice
metadata:
  name: s4-db
  namespace: $namespace
  labels:
    kubernetes.io/service-name: s4-db
    app.kubernetes.io/name: s4-db
    app.kubernetes.io/component: database
    app.kubernetes.io/part-of: campus-plus-plus
addressType: IPv4
ports:
  - name: postgres
    protocol: TCP
    port: $endpoint_port
endpoints:
  - addresses:
      - "$endpoint_address"
EOF
}

prod_db_endpoint_address=""
prod_db_endpoint_port=""

if [[ "$environment" == "prod" ]]; then
  [[ -n "$host_secrets_root" ]] || {
    echo "CAMPUS_SECRETS_ROOT is required for PROD db endpoint config." >&2
    exit 1
  }

  prod_db_endpoint_file="$host_secrets_root/$environment/db-endpoint.env"
  [[ -f "$prod_db_endpoint_file" ]] || {
    echo "Required PROD db endpoint config not found: $prod_db_endpoint_file" >&2
    exit 1
  }

  prod_db_endpoint_address="$(read_env_value "$prod_db_endpoint_file" DB_ENDPOINT_ADDRESS)"
  prod_db_endpoint_port="$(read_env_value "$prod_db_endpoint_file" DB_ENDPOINT_PORT)"

  [[ -n "$prod_db_endpoint_address" ]] || {
    echo "DB_ENDPOINT_ADDRESS is required in $prod_db_endpoint_file" >&2
    exit 1
  }

  [[ -n "$prod_db_endpoint_port" ]] || {
    echo "DB_ENDPOINT_PORT is required in $prod_db_endpoint_file" >&2
    exit 1
  }

  validate_ipv4_address "$prod_db_endpoint_address" || {
    echo "DB_ENDPOINT_ADDRESS must be a valid IPv4 address." >&2
    exit 1
  }

  validate_tcp_port "$prod_db_endpoint_port" || {
    echo "DB_ENDPOINT_PORT must be a TCP port from 1 to 65535." >&2
    exit 1
  }
fi

tmp_root="$(mktemp -d "${TMPDIR:-/tmp}/campus-kustomize-XXXXXX")"
tmp_app_root="$tmp_root/app"
tmp_base_path="$tmp_app_root/base"
tmp_overlay_path="$tmp_app_root/overlays/$environment"

cleanup() {
  rm -rf "$tmp_root"
}
trap cleanup EXIT

mkdir -p "$tmp_app_root/overlays"
cp -R "$repo_root/deploy/app/base" "$tmp_base_path"
cp -R "$overlay_path" "$tmp_overlay_path"

if [[ -n "$host_secrets_root" ]]; then
  host_secret_dir="$host_secrets_root/$environment"

  echo "Staging secret files into temporary overlay from host path '$host_secret_dir'..."
  stage_host_secret_file "$host_secret_dir/db-secrets.env" "$tmp_overlay_path/secrets/db-secrets.env"
  stage_host_secret_file "$host_secret_dir/auth-secrets.env" "$tmp_overlay_path/secrets/auth-secrets.env"
fi

required_files=(
  "$tmp_overlay_path/config/auth-config.env"
  "$tmp_overlay_path/config/backend-config.env"
  "$tmp_overlay_path/config/importer-config.env"
  "$tmp_overlay_path/secrets/db-secrets.env"
  "$tmp_overlay_path/secrets/auth-secrets.env"
)

for required_file in "${required_files[@]}"; do
  require_file "$required_file"
done

tmp_kustomization_path="$tmp_overlay_path/kustomization.yaml"
sed -E -i "s/^([[:space:]]*newTag:[[:space:]]*).+$/\1$image_tag/" "$tmp_kustomization_path"

httproute_hostname_override="${CAMPUS_HTTPROUTE_HOSTNAME:-}"

if [[ -n "$httproute_hostname_override" ]]; then
  validate_hostname "$httproute_hostname_override" || {
    echo "CAMPUS_HTTPROUTE_HOSTNAME must be a valid DNS hostname." >&2
    exit 1
  }

  httproute_patch_path="$tmp_overlay_path/httproute-patch.yaml"
  require_file "$httproute_patch_path"
  sed -E -i "s/^([[:space:]]*-[[:space:]]*).+$/\1$httproute_hostname_override/" "$httproute_patch_path"
fi

if [[ "$environment" == "prod" ]]; then
  prod_db_endpoint_resource="prod-db-endpoint.yaml"
  write_prod_db_endpoint_manifest \
    "$tmp_overlay_path/$prod_db_endpoint_resource" \
    "$prod_db_endpoint_address" \
    "$prod_db_endpoint_port"
  sed -i "/^[[:space:]]*-[[:space:]]*\\.\\.\\/\\.\\.\\/base[[:space:]]*$/a\\  - $prod_db_endpoint_resource" "$tmp_kustomization_path"
  grep -q "$prod_db_endpoint_resource" "$tmp_kustomization_path" || {
    echo "Failed to add PROD db endpoint resource to temporary kustomization." >&2
    exit 1
  }
fi

if [[ -z "$manifest_out" ]]; then
  rendered_manifest_path="$tmp_root/campus-$environment-rendered.yaml"
else
  rendered_manifest_path="$manifest_out"
fi

echo "Rendering overlay '$environment' with tag '$image_tag'..."
kubectl kustomize "$tmp_overlay_path" >"$rendered_manifest_path"
echo "Rendered manifest: $rendered_manifest_path"

if [[ "$render_only" == "true" ]]; then
  echo "Render-only mode enabled. Skipping kubectl apply."
  exit 0
fi

echo "Applying manifest to namespace '$namespace'..."
kubectl apply -f "$rendered_manifest_path"

echo "Current resource overview:"
kubectl -n "$namespace" get pods
kubectl -n "$namespace" get gateway
kubectl -n "$namespace" get httproute
kubectl -n "$namespace" get envoyproxy
kubectl -n "$namespace" get clienttrafficpolicy
kubectl -n "$namespace" get jobs
