# Deployment Structure

This document describes the current deployment layout.

The canonical Kubernetes rollout path is now `deploy/app/` with environment
overlays, not the older dedicated `deploy/dev/` tree.

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
│   └── ingress-nginx/
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

## `infra/`

Shared infrastructure-side baselines.

### `infra/envoy-gateway/`

Current active infra baseline:

- Helm values for the Envoy Gateway controller
- shared `GatewayClass`
- controller-level notes

### `infra/ingress-nginx/`

Legacy/reference baseline:

- retained for migration history and comparison
- not part of the active non-prod release flow

## `scripts/`

Operational helpers for manual and workflow-driven release actions:

- `apply-overlay.sh` renders and applies an overlay with a concrete release tag
- `verify-overlay.sh` verifies rollouts, importer completion, Gateway API health,
  and Envoy NodePort publication

## `templates/`

Example config and secret inputs:

- safe starter files for local preparation
- reference layout for the host-side secret paths
- no real secrets

## `docs/`

Operational notes for:

- environment layout
- release naming
- rollout flow
- deployment structure
