# Campus++

[![CI Pipeline](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml)

Campus++ is a full-stack Hochschule Campus Wien application with a local Docker
runtime and a Kubernetes-based DEV deployment.

Main runtime components:

- `frontend`
- `auth`
- `backend`
- `campus-nginx`
- `campus-importer`
- PostgreSQL
- Envoy Gateway

## Current Status

The current working DEV entry path is:

`Internet -> davl.at (nginx + TLS) -> private/VPN path -> DEV 192.168.56.40:31080 -> Envoy Gateway -> campus-nginx -> frontend/auth/backend -> PostgreSQL 192.168.56.20`

Confirmed today:

- `campus-dev` namespace is deployed on k3s
- `frontend`, `auth`, `backend`, `campus-nginx`, and `campus-importer` run in Kubernetes
- `campus-importer` completed and populated the database
- Envoy Gateway exposes the app through `NodePort 31080`
- `campus.davl.at` is served through the public VPS with HTTPS

Known gap:

- the single-node DEV cluster is still operationally unstable and shows restarts
  around Envoy and other control-plane components

## Runtime Modes

### 1. Local Docker Runtime

Use the root `docker-compose.yml` for local development and smoke testing.

This path includes:

- local image builds
- bundled PostgreSQL container
- local `campus-nginx`

### 2. Kubernetes DEV Runtime

Use `deploy/dev/` and the GitHub Actions workflows for the active DEV path.

This path includes:

- Kustomize manifests in `deploy/dev/`
- Envoy Gateway controller baselines in `deploy/infra/envoy-gateway/`
- self-hosted GitHub Actions deploy on the DEV node
- GHCR-backed image delivery

The older `deploy/app/` overlay tree is still in the repository, but it is not
the active DEV rollout path anymore.

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
DEV NodePort 31080
  ↓
Envoy Gateway
  ↓
campus-nginx
  ├── frontend
  ├── auth
  └── backend
       ↓
    PostgreSQL 192.168.56.20
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
- on `push` to `main`, CI builds and pushes images to GHCR
- images are published as both `sha-<shortsha>` and `dev-latest`
- `Deploy DEV` runs on the self-hosted DEV runner
- the deploy workflow stages secrets, creates the GHCR pull secret, pins
  `deploy/dev` to the successful CI commit tag, applies the manifests, and
  waits for importer completion

## Project Structure

```text
campus-plus-plus/
├── auth/
├── backend/
├── deploy/
│   ├── dev/
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
- the active DEV deployment path is Envoy-based, not ingress-nginx-based

