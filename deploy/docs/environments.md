# Environments

This document describes the current environment model for Campus++ and maps it
to the repository structure under `deploy/`.

It is intentionally split into:

- current confirmed state
- target state that is already reflected by the repo layout
- known gaps that are not yet fully encoded in git

## Infrastructure Roles

The current infrastructure model uses six hosts:

- `GW` (`192.168.50.1`): edge reverse proxy, SSH jump host, external entry path, not part of k3s
- `S1` (`192.168.50.10`): planned PROD k3s node
- `S2` (`192.168.50.2`): planned PROD k3s node
- `S3` (`192.168.50.3`): planned PROD k3s node
- `S4` (`192.168.50.4`): PostgreSQL server outside Kubernetes
- `S5` (`192.168.50.5`): DEV k3s single-node cluster, intended self-hosted runner host for assisted DEV deploys, recommended runner label `campus-dev`

## Deployment Model

Campus++ keeps application code and deployment code separate:

- application code stays in `frontend/`, `auth/`, `backend/`, `importer/`, `nginx/`
- Kubernetes application manifests live under `deploy/app/`
- infrastructure-related Helm values and notes live under `deploy/infra/`
- templates for env-based config and secrets live under `deploy/templates/`

This separation is already reflected in the repo and should be preserved.

## Runtime Environments

Two Kubernetes environments are planned:

- `DEV`: namespace `campus-dev`, currently deployed to `S5`
- `PROD`: namespace `campus-prod`, target deployment on the `S1` + `S2` + `S3` k3s cluster

The namespace mapping is already encoded in:

- `deploy/app/overlays/dev/kustomization.yaml`
- `deploy/app/overlays/prod/kustomization.yaml`

## Current Confirmed DEV State

The current working DEV vertical slice is:

`Client -> GW -> S5 NodePort -> ingress-nginx -> campus-nginx -> services -> PostgreSQL on S4`

Confirmed characteristics:

- `S5` runs k3s
- bundled Traefik is disabled
- `ingress-nginx` is installed and active
- the controller is reachable through NodePort `30080/30443`
- `IngressClass` is `nginx`
- the DEV overlay pulls application images from GHCR
- application traffic enters Kubernetes through an Ingress and then reaches `campus-nginx`
- `campus-nginx` keeps the app-level routing and `auth_request` logic
- PostgreSQL stays outside Kubernetes on `S4`

## Current Confirmed PROD Direction

The repo already prepares a separate PROD overlay, but PROD should currently be
treated as a target deployment path, not as a finished rollout.

The intended model is:

- k3s cluster on `S1`, `S2`, `S3`
- ingress-nginx as the cluster ingress layer
- `campus-nginx` as the internal application gateway
- PostgreSQL still external on `S4`
- external entry through `GW`

## Database Placement

The database is intentionally outside Kubernetes.

Current confirmed endpoint characteristics:

- host: `192.168.50.4`
- port: `5432`
- database name: `campus`

Credentials are provided through secret env files and must not be committed to
git. The repository should only contain templates and examples for those values.

## Configuration Strategy

Application deployment uses:

- Kustomize for app manifests under `deploy/app/`
- Helm only for shared infrastructure such as `ingress-nginx`
- environment-specific `ConfigMap` generation from env files
- environment-specific `Secret` generation from env files

This means:

- non-secret values belong in `deploy/app/overlays/<env>/config/`
- secret values belong outside git and should be injected via local env files
- `deploy/templates/` documents the expected keys

## What Is In Repo Today

The repository already includes:

- a reusable app base in `deploy/app/base`
- separate `dev` and `prod` overlays
- a DEV image strategy based on GHCR images
- versioned ingress-nginx values baselines in `deploy/infra/ingress-nginx`
- a DEV deploy workflow for a Linux self-hosted runner on `S5`
- an automatic `main -> CI -> DEV deploy` path for the `S5` runner

## What Is Still Outside Repo Or Incomplete

The following are not yet fully captured in git:

- the actual `GW` nginx configuration
- the final PROD delivery flow
- self-hosted runner registration and service management on `S5`

Those gaps should be documented and then reduced over time so the repo becomes
the main source of truth.
