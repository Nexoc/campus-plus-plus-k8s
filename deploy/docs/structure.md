# Deployment Structure

This document describes the current deployment layout.

The canonical Kubernetes rollout path is `deploy/app/` with environment
overlays.

## High-Level Layout

```text
deploy/
├── app/
│   ├── base/
│   └── overlays/
│       ├── dev/
│       ├── home/
│       └── prod/
├── infra/
│   ├── envoy-gateway/
│   └── gw-nginx/
├── scripts/
├── templates/
└── docs/
```

## `app/`

This is the active manifest tree.

Contents:

- shared base workloads and Gateway API resources
- overlay-specific config, secrets, and edge patches
- image tag rewrite points for GHCR release tags

This tree is used by:

- `deploy/scripts/apply-overlay.sh`
- `deploy/scripts/verify-overlay.sh`
- `.github/workflows/deploy-dev.yml`
- `.github/workflows/deploy-prod.yml`

For PROD, `apply-overlay.sh` also renders a temporary `s4-db` Service and
EndpointSlice from host-local runtime config before running Kustomize. This
keeps the external database address out of tracked manifests while preserving a
stable `DB_HOST=s4-db` application contract.

## `infra/`

Shared infrastructure-side baselines.

### `infra/envoy-gateway/`

Current active infra baseline:

- Helm values for the Envoy Gateway controller
- shared `GatewayClass`
- controller-level notes

### `infra/gw-nginx/`

Current lab edge baseline:

- nginx site config for `gw`
- reverse proxy from `gw:80` to `s5-dev:30080`
- fixed Host header for the DEV `HTTPRoute`

## `scripts/`

Operational helpers for manual and workflow-driven release actions:

- `apply-overlay.sh` renders and applies an overlay with a concrete release tag
- `verify-overlay.sh` verifies rollouts, importer completion, Gateway API health,
  and Envoy NodePort publication

## `templates/`

Example config and secret inputs:

- safe starter files for local preparation
- reference layout for the host-side secret paths
- placeholder endpoint config for external database aliases
- no real secrets

## `docs/`

Operational notes for:

- environment layout
- release naming
- rollout flow
- production CD design
- deployment structure
