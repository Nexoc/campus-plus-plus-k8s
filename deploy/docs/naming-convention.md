# Naming Convention

This document defines the current naming rules for Campus++ across non-prod
clusters, images, and release tags.

## Guiding Rules

- keep workload names stable across environments
- keep environment identity in overlays, runner labels, and edge routing
- keep image names aligned with component names
- keep deploy identity in immutable Git tags

## Kubernetes Resource Naming

Current canonical names:

- namespace: `campus-dev`
- frontend deployment/service: `frontend`
- auth deployment/service: `auth`
- backend deployment/service: `backend`
- internal app gateway: `campus-nginx`
- importer job: `campus-importer`
- Gateway: `campus`
- HTTPRoute: `campus`
- EnvoyProxy: `campus-edge`
- ClientTrafficPolicy: `campus-edge`

These names stay stable across the lab and home non-prod overlays.

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
- non-prod images are tagged exactly with the Git tag that triggered the release

Examples:

- `dev-2026.04.24-1`
- `home-2026.04.24-1`

Reserved:

- `v1.0.0` style tags for future PROD

## Runner Label Naming

Current workflow targeting rules:

- lab deploy job requires labels `self-hosted`, `Linux`, `dev`, `s5`
- home deploy job requires labels `self-hosted`, `Linux`, `dev`, `home`

The workflow schedules against labels, not runner names.

## Hostname Strategy

Lab non-prod:

- active route hostname: `campus-dev.s5-dev.local`
- active NodePort entry: `30080`

Home non-prod:

- overlay placeholder hostname: `campus-home.local`
- replace this with the real home edge hostname when that edge is finalized

Production:

- active planned route hostname: `campus-prod.davl.at`

## Repo Alignment

The repo now reflects:

- stable component and service names
- `deploy/app` as the canonical manifest tree
- Envoy/Gateway API as the active ingress layer
- tag-driven non-prod delivery via `dev-*` and `home-*`
