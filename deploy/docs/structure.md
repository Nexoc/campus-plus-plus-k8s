# Deployment Structure

This document describes the current layout of the deployment layer.

The active DEV path was simplified and now uses a dedicated `deploy/dev/`
directory instead of the older `deploy/app/overlays/dev` flow.

## High-Level Layout

```text
deploy/
├── dev/
│   ├── config/
│   └── secrets/
├── app/
│   ├── base/
│   └── overlays/
├── templates/
│   ├── config/
│   └── secrets/
├── infra/
│   ├── envoy-gateway/
│   └── ingress-nginx/
└── docs/
```

## `dev/`

This is the active DEV manifest set.

Contents:

- namespace
- frontend, auth, backend, and `campus-nginx` deployments/services
- importer job
- Envoy Gateway resources: `EnvoyProxy`, `Gateway`, `HTTPRoute`
- versioned config inputs
- ignored local secret inputs

This is the directory used by:

- manual `kubectl apply -k deploy/dev`
- `.github/workflows/deploy-dev.yml`

## `app/`

This is the older Kustomize base/overlay tree.

Current status:

- retained for reference and possible future reuse
- not the active DEV rollout path
- still useful as a source of historical manifests and PROD-oriented ideas

## `templates/`

This directory contains example config and secret inputs.

Purpose:

- document required keys
- provide safe starter files
- keep live secrets out of git

## `infra/`

This directory contains infrastructure-side baselines.

### `infra/envoy-gateway/`

Current active infra baseline for DEV:

- Helm values for the controller
- shared `GatewayClass`
- controller-level notes

### `infra/ingress-nginx/`

Legacy/reference baseline:

- old ingress-nginx Helm values
- retained for history and comparison
- not the active DEV entry layer today

## `docs/`

Operational documentation for the deployment layer.

Key files:

- `environments.md`
- `naming-convention.md`
- `rollout-notes.md`
- this file

## Separation Principle

The repository still follows the same rule:

- application code stays in service directories
- deployment artifacts stay under `deploy/`

That keeps:

- source layout stable
- business logic separate from cluster plumbing
- infrastructure notes separate from application manifests
