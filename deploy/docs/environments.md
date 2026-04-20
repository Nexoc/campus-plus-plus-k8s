# Environments

This document describes the current environment model for Campus++.

It reflects the active DEV setup that is working today, not the older
bootstrap state.

## Infrastructure Roles

Current confirmed hosts:

- `davl` / `davl.at`: public VPS, nginx reverse proxy, TLS termination
- `GW`: private gateway / NAT / VPN hop used to reach the lab network
- `DEV` (`192.168.56.40`): single-node k3s cluster and self-hosted GitHub runner
- `DB` (`192.168.56.20`): PostgreSQL server outside Kubernetes
- `MON` (`192.168.56.30`): monitoring and security tooling host

## Deployment Model

Campus++ keeps application code and deployment code separate:

- application code stays in `frontend/`, `auth/`, `backend/`, `importer/`, `nginx/`
- active DEV manifests live under `deploy/dev/`
- infrastructure baselines live under `deploy/infra/`
- example config and secret templates live under `deploy/templates/`

The older `deploy/app/` hierarchy is still in git but is not the active DEV
rollout path anymore.

## Runtime Environments

Current active environment:

- `DEV`: namespace `campus-dev` on the `DEV` k3s node

Planned but not active:

- `PROD`: future multi-node or separate-cluster rollout path

## Current Confirmed DEV State

Current working request path:

`Internet -> davl.at -> private/VPN path -> DEV 192.168.56.40:31080 -> Envoy Gateway -> campus-nginx -> services -> PostgreSQL 192.168.56.20`

Confirmed characteristics:

- `DEV` runs k3s as a single-node cluster
- Traefik is disabled
- Envoy Gateway is installed and active in `envoy-gateway-system`
- the app namespace is `campus-dev`
- active workloads are `frontend`, `auth`, `backend`, `campus-nginx`, and `campus-importer`
- the active Envoy `NodePort` is `31080`
- `campus-nginx` remains the internal app gateway and auth boundary
- PostgreSQL stays outside Kubernetes on the dedicated DB host
- public access is provided through the `davl.at` reverse proxy

## Database Placement

The database is intentionally outside Kubernetes.

Current confirmed endpoint:

- host: `192.168.56.20`
- port: `5432`
- database: `campus`

Credentials are injected via secret env files and must not be committed.

## Configuration Strategy

Current active deployment uses:

- Kustomize for app manifests under `deploy/dev/`
- Helm for Envoy Gateway installation under `deploy/infra/envoy-gateway/`
- versioned config files under `deploy/dev/config/`
- ignored secret files under `deploy/dev/secrets/`
- GHCR for deployable images

## What Is In Repo Today

The repository currently includes:

- an active DEV manifest set in `deploy/dev/`
- Envoy Gateway controller baselines in `deploy/infra/envoy-gateway/`
- legacy ingress-nginx baselines in `deploy/infra/ingress-nginx/`
- GitHub Actions CI build and GHCR publishing on `main`
- self-hosted DEV auto-deploy via `.github/workflows/deploy-dev.yml`

## What Is Still Outside Repo Or Incomplete

The following are still incomplete or partly external:

- the exact public VPS nginx configuration
- the full GW private routing configuration
- long-term stabilization of the single-node DEV cluster
- the final PROD rollout path
