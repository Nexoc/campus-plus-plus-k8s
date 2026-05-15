# Environments

This document describes the current Campus++ environment model after the move
to tag-driven non-prod releases.

## Infrastructure Roles

Lab environment:

- `gw`: gateway, NAT, SSH jump host
- `s5-dev`: single-node k3s cluster and lab self-hosted runner
- `s4-db`: PostgreSQL outside Kubernetes

IP addresses belong in host inventory or DNS, not in application manifests.
Runtime configuration uses hostnames so the same repo can be deployed from
different lab networks without editing tracked files.

Home environment:

- separate self-hosted runner and k3s cluster
- same application layout as lab
- its own edge hostname patch and runner labels

Reserved for later:

- `v*` tags and a future PROD rollout path

## Deployment Model

Campus++ keeps application code and deployment code separate:

- application code stays in `frontend/`, `auth/`, `backend/`, `importer/`, and `nginx/`
- active Kubernetes manifests live under `deploy/app/overlays/`
- shared infra baselines live under `deploy/infra/`
- templates live under `deploy/templates/`

## Runtime Environments

Current active non-prod environments:

- `dev`: lab cluster on `s5-dev`, namespace `campus-dev`
- `home`: home cluster, same namespace layout on a separate cluster

Current release channels:

- `dev-*` deploys to the lab cluster on runner labels `dev+s5`
- `home-*` deploys to the home cluster on runner labels `dev+home`
- `main` runs validation only
- `v*` is reserved for future PROD work

## Current Request Paths

Lab path:

`Internet -> gw -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> services -> PostgreSQL s4-db`

Home path:

`Home edge hostname -> home cluster NodePort 30080 -> Envoy Gateway -> campus-nginx -> services`

Notes:

- Envoy Gateway is the active entry layer for both non-prod environments
- `campus-nginx` remains the internal app gateway and auth boundary
- PostgreSQL stays outside Kubernetes for the lab environment

## Configuration Strategy

Current non-prod delivery uses:

- Kustomize overlays in `deploy/app/overlays/dev` and `deploy/app/overlays/home`
- Envoy Gateway baselines in `deploy/infra/envoy-gateway/`
- versioned non-secret config files under each overlay
- ignored secret env files under each overlay
- GHCR images tagged exactly with the pushed release tag

## Secrets

Self-hosted runners stage secrets from fixed host paths:

- `/home/nexoc/campus-secrets/dev/`
- `/home/nexoc/campus-secrets/home/`

Expected files per environment:

- `db-secrets.env`
- `auth-secrets.env`

Real secrets must not be committed.

## What Is Still Outside Repo Or Incomplete

The following remain future or partially external work:

- TLS/public hostname hardening for the lab edge
- the final public hostname for the home overlay
- future PROD rollout design
- broader cluster hardening and monitoring
