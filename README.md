# Campus++

[![CI Pipeline](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml)

Campus++ is a full-stack Hochschule Campus Wien application with a local Docker
runtime and a Kubernetes-based non-prod Kubernetes deployment model.

Main runtime components:

- `frontend`
- `auth`
- `backend`
- `campus-nginx`
- `campus-importer`
- PostgreSQL
- Envoy Gateway

## Current Status

The current working lab DEV entry path is:

`Internet -> gw -> DEV 192.168.50.5:30080 -> Envoy Gateway -> campus-nginx -> frontend/auth/backend -> PostgreSQL 192.168.50.4`

Confirmed today:

- `campus-dev` namespace is deployed on k3s
- `frontend`, `auth`, `backend`, `campus-nginx`, and `campus-importer` run in Kubernetes
- `campus-importer` completed and populated the database
- Envoy Gateway exposes the app through `NodePort 30080`
- `deploy/app` is the canonical Kubernetes deployment tree
- non-prod releases are tag-driven, not branch auto-deploy driven

Known gap:

- the single-node DEV cluster is still operationally unstable and shows restarts
  around Envoy and other control-plane components

## Next Steps

The practical next plan is:

1. Close the current security and access-control gaps first.
   This includes removing the bootstrap admin with a known password, fixing missing
   moderator-only checks where write access is too broad, and eliminating runtime
   secret staging inside the self-hosted runner workspace.
2. Stabilize the single-node DEV cluster.
   Focus on the `NodeNotReady` episodes, short API disconnects, and the restarts
   around Envoy-related and control-plane components.
3. Harden the external entry path.
   The repo now contains the lab `gw` nginx baseline; the remaining work is a
   stable public hostname/TLS shape instead of HTTP-only lab access.
4. Make the DEV deploy path safer and more reproducible.
   Manual and workflow-driven deploys should not depend on staging secret env files
   inside the repo checkout, and the runbook should reflect the actual deploy flow.
5. After DEV is secure and operationally stable, define the production-like rollout
   path.
   That includes the final public host shape, deployment topology, and the future
   PROD environment model.

## Runtime Modes

### 1. Local Docker Runtime

Use the root `docker-compose.yml` for local development and smoke testing.

This path includes:

- local image builds
- bundled PostgreSQL container
- local `campus-nginx`

### 2. Kubernetes Non-Prod Runtime

Use `deploy/app/overlays/` and the GitHub Actions release workflow for the
active non-prod path.

This path includes:

- Kustomize overlays in `deploy/app/overlays/`
- Envoy Gateway controller baselines in `deploy/infra/envoy-gateway/`
- self-hosted GitHub Actions deploys on the `s5` and `home` runners
- GHCR-backed image delivery

Current release channels:

- `dev-*` deploys to the lab `s5` cluster
- `home-*` deploys to the home cluster
- `main` runs validation only

## Documentation

General project docs:

- [SRS](docs/SRS.md)
- [Requirements](docs/requirements.md)

Deployment docs:

- [Deployment Runbook](deploy/README.md)
- [Environments](deploy/docs/environments.md)
- [Naming Convention](deploy/docs/naming-convention.md)
- [Rollout Notes](deploy/docs/rollout-notes.md)

## Local Quick Start

### Prerequisites

- Docker
- Docker Compose v2
- free port `80`

### Configuration

The local runtime uses the root `docker-compose.yml` and a local `.env.dev`
file.

Required variables:

- `BACKEND_PROFILE`
- `AUTH_PROFILE`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION`

### Start

```bash
docker compose --env-file .env.dev up -d --build
```

Open:

- `http://localhost`

### Show container status

```bash
docker compose --env-file .env.dev ps -a
```

### Stop

```bash
docker compose --env-file .env.dev down -v --remove-orphans
```

## Architecture

```text
Internet
  ↓
davl.at / Public VPS
  ↓
Private path / VPN
  ↓
DEV NodePort 30080
  ↓
Envoy Gateway
  ↓
campus-nginx
  ├── frontend
  ├── auth
  └── backend
       ↓
    PostgreSQL 192.168.50.4
```

## Services

### Frontend

- Vue 3 SPA
- served behind `campus-nginx`
- API calls use same-origin paths such as `/api/*` and `/auth/*`

### campus-nginx

- internal application gateway
- serves frontend assets
- routes `/auth/*`, `/api/*`, `/account/*`, `/admin/*`
- enforces auth via `auth_request`
- forwards trusted identity headers to backend services

### Auth Service

- Spring Boot
- login, registration, account operations
- JWT issuing and validation
- Flyway-managed auth schema

### Backend

- Spring Boot
- public and protected REST API
- Flyway-managed app schema
- trusts upstream identity headers from `campus-nginx`

### Importer

- one-shot Kubernetes Job
- waits for DB connectivity and schema readiness
- imports HCW study programs and courses into PostgreSQL

## Profiles

Supported Spring profiles:

- `dev`
- `test`
- `prod`

## CI/CD

Current GitHub Actions behavior:

- `CI Pipeline` runs auth tests, backend tests, and backend build
- `push` and `pull_request` on `main` run validation only
- `dev-*` tags build and publish GHCR images, then deploy to the `s5` runner
- `home-*` tags build and publish GHCR images, then deploy to the `home` runner
- release image tags are exactly the pushed Git tag
- deploys use the canonical `deploy/app` overlays and Envoy/Gateway API path

## Project Structure

```text
campus-plus-plus/
├── auth/
├── backend/
├── deploy/
│   ├── app/
│   ├── docs/
│   ├── infra/
│   └── templates/
├── docs/
├── frontend/
├── importer/
├── nginx/
├── docker-compose.yml
└── README.md
```

## Notes

- `campus-nginx` is the application security boundary
- backend does not parse JWT directly
- the public DEV hostname currently terminates on `davl.at`
- the active DEV deployment path is Envoy/Gateway API based

