#!/usr/bin/env bash

# apply-overlay.sh
#
# Purpose:
# - render the selected Kustomize overlay with a concrete immutable image tag
# - validate that required config and local secret files exist
# - optionally apply the rendered manifest to the target Kubernetes namespace
#
# This script is used both for manual operator runs and for the self-hosted
# DEV deploy workflow.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  deploy/scripts/apply-overlay.sh --environment <dev|prod> --image-tag <sha-...> [--render-only] [--manifest-out <path>]

Required:
  --environment   Target overlay: dev or prod
  --image-tag     Immutable image tag to inject, for example sha-157f8d0

Optional:
  --render-only   Render the overlay but do not run kubectl apply
  --manifest-out  Path for the rendered manifest output
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

if [[ "$environment" != "dev" && "$environment" != "prod" ]]; then
  echo "--environment must be either 'dev' or 'prod'" >&2
  usage
  exit 1
fi

if [[ ! "$image_tag" =~ ^sha-[A-Za-z0-9._-]+$ ]]; then
  echo "--image-tag must match ^sha-[A-Za-z0-9._-]+$" >&2
  usage
  exit 1
fi

case "$environment" in
  dev) namespace="campus-dev" ;;
  prod) namespace="campus-prod" ;;
esac

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
overlay_path="$repo_root/deploy/app/overlays/$environment"

require_command kubectl
require_command mktemp
require_command sed

required_files=(
  "$overlay_path/config/auth-config.env"
  "$overlay_path/config/backend-config.env"
  "$overlay_path/config/importer-config.env"
  "$overlay_path/secrets/db-secrets.env"
  "$overlay_path/secrets/auth-secrets.env"
)

for required_file in "${required_files[@]}"; do
  require_file "$required_file"
done

tmp_workspace_root="$repo_root/deploy/.tmp"
mkdir -p "$tmp_workspace_root"
tmp_root="$(mktemp -d "$tmp_workspace_root/campus-kustomize-XXXXXX")"
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

tmp_kustomization_path="$tmp_overlay_path/kustomization.yaml"
sed -E -i "s/^([[:space:]]*newTag:[[:space:]]*).+$/\1$image_tag/" "$tmp_kustomization_path"

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
kubectl -n "$namespace" get ingress
kubectl -n "$namespace" get jobs
