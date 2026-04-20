# Naming Convention

This document defines the current naming rules for Campus++ across namespaces,
workloads, images, and entrypoints.

## Guiding Rules

- keep workload names stable
- carry environment separation in namespace and entrypoint, not in every object name
- keep image names aligned with component names
- keep public names on the edge layer, not on raw node IPs

## Kubernetes Resource Naming

Current active names:

- namespace: `campus-dev`
- frontend deployment/service: `frontend`
- auth deployment/service: `auth`
- backend deployment/service: `backend`
- internal app gateway: `campus-nginx`
- importer job: `campus-importer`
- Envoy gateway: `campus-dev`
- Envoy route: `campus`
- Envoy proxy policy object: `campus-dev-edge`

These names are already in use and should remain stable.

## Image Naming

Current GHCR images:

- `ghcr.io/nexoc/campus-frontend`
- `ghcr.io/nexoc/campus-auth`
- `ghcr.io/nexoc/campus-backend`
- `ghcr.io/nexoc/campus-nginx`
- `ghcr.io/nexoc/campus-importer`

Rules:

- one image name per component
- deployment identity belongs in tags
- `sha-<shortsha>` is the immutable build identity
- `dev-latest` is a convenience pointer for the DEV workflow

## Hostname Strategy

Current confirmed public DEV hostname:

- `campus.davl.at`

Current internal smoke endpoint:

- `http://192.168.56.40:31080`

Policy:

- users should access the public hostname, not the raw node IP
- raw node IPs are acceptable for operator smoke tests only
- `sslip.io` is no longer the active DEV hostname strategy

## Naming Rules By Layer

At the public edge:

- use `campus.davl.at`

Inside Kubernetes:

- keep service names environment-neutral
- keep environment identity in namespace and edge routing

In CI and registry:

- keep stable component image names
- use `sha-<shortsha>` for immutable references
- use `dev-latest` only for fast-moving DEV delivery

## Current Repo Alignment

The repo currently reflects:

- stable workload names
- `campus-dev` as the active namespace
- GHCR image names aligned with the component model
- Envoy Gateway resources aligned with the DEV rollout

The repo still does not fully capture:

- final PROD naming
- the full public edge nginx configuration
