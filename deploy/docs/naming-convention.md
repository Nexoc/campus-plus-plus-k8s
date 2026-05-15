# Naming Convention

This document defines the current naming rules for Campus++ across clusters,
images, and release tags.

## Guiding Rules

- keep workload names stable across environments
- keep environment identity in overlays, runner labels, and edge routing
- keep image names aligned with component names
- keep deploy identity in immutable Git tags

## Kubernetes Resource Naming

Current canonical names:

- DEV namespace: `campus-dev`
- PROD namespace: `campus-prod`
- frontend deployment/service: `frontend`
- auth deployment/service: `auth`
- backend deployment/service: `backend`
- internal app gateway: `campus-nginx`
- importer job: `campus-importer`
- external database alias: `s4-db`
- Gateway: `campus`
- HTTPRoute: `campus`
- EnvoyProxy: `campus-edge`
- ClientTrafficPolicy: `campus-edge`

These names stay stable across the lab, home, and production overlays.

## Image Naming

Current GHCR images:

- `ghcr.io/nexoc/campus-frontend`
- `ghcr.io/nexoc/campus-auth`
- `ghcr.io/nexoc/campus-backend`
- `ghcr.io/nexoc/campus-nginx`
- `ghcr.io/nexoc/campus-importer`

Rules:

- one image name per component
- deployment identity belongs in the tag
- release images are tagged exactly with the Git tag that triggered the workflow

Examples:

- `dev-2026.04.24-1`
- `home-2026.04.24-1`
- `v1.0.0`

## Runner Label Naming

Current workflow targeting rules:

- lab deploy job requires labels `self-hosted`, `Linux`, `dev`, `s5`
- home deploy job requires labels `self-hosted`, `Linux`, `dev`, `home`
- prod deploy job requires labels `self-hosted`, `Linux`, `X64`, `prod`, `gw`

The workflow schedules against labels, not runner names.

## Hostname Strategy

Lab non-prod:

- active route hostname: `campus-dev.s5-dev.local`
- active NodePort entry: `30080`

Home non-prod:

- overlay placeholder hostname: `campus-home.local`
- replace this with the real home edge hostname when that edge is finalized

Production:

- active route hostname: `campus-prod.davl.at`
- stable in-cluster database alias: `s4-db`
- real external database endpoint: environment-specific host-local
  `db-endpoint.env`

## Repo Alignment

The repo now reflects:

- stable component and service names
- `deploy/app` as the canonical manifest tree
- Envoy/Gateway API as the active ingress layer
- tag-driven non-prod delivery via `dev-*` and `home-*`
- controlled PROD delivery via `v*`
