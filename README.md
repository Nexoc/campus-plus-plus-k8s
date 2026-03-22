# Campus++

[![CI Pipeline](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml)

Campus++ is a containerized full-stack application for Hochschule Campus Wien.

Main components:

- frontend
- auth service
- backend
- importer
- campus-nginx
- PostgreSQL

## Runtime Modes

This repository currently supports two practical runtime paths.

### 1. Local Container Runtime

Use the root `docker-compose.yml` when you want a self-contained local stack for
development or smoke testing.

This path includes:

- local container builds
- local `campus-nginx`
- bundled PostgreSQL container

### 2. Kubernetes Deployment Layer

Use `deploy/` when you want the Kubernetes-oriented deployment path.

This path includes:

- Kustomize application manifests in `deploy/app/`
- infrastructure-side Helm values in `deploy/infra/`
- environment docs and rollout notes in `deploy/docs/`
- DEV and PROD overlay separation

The current deployment direction is:

`Client -> GW -> ingress-nginx -> campus-nginx -> services`

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

The local runtime uses the canonical root `docker-compose.yml`.

Required environment variables:

- `BACKEND_PROFILE`
- `AUTH_PROFILE`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION`

Profiles and infrastructure-specific values are injected via environment
variables.

For local development, create and use a local `.env.dev` file.

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

## Kubernetes Path

The Kubernetes deployment layer is intentionally kept next to the application
code instead of reshaping the app directories.

High-level rules:

- application source stays in `frontend/`, `auth/`, `backend/`, `importer/`,
  `nginx/`
- Kubernetes application resources live under `deploy/app/`
- ingress-nginx values live under `deploy/infra/ingress-nginx/`
- secrets are expected as local ignored files generated from templates

Start with:

- [deploy/README.md](deploy/README.md)

## Architecture

```text
Client
  ↓
GW / Edge Reverse Proxy
  ↓
ingress-nginx
  ↓
campus-nginx
  ├── Frontend
  ├── Auth Service
  └── Backend API
       ↓
    PostgreSQL
```

## Services

### Frontend

- Vue 3 SPA
- served as static files
- sends JWT in `Authorization: Bearer <token>`

### campus-nginx

- single application entry point
- serves frontend
- routes requests
- validates protected requests via `auth_request`
- forwards trusted identity headers to backend

### Auth Service

- Spring Boot
- login / registration
- password hashing
- JWT issuing and validation
- Flyway migrations

### Backend

- Spring Boot
- business logic only
- protected behind nginx
- trusts forwarded identity headers

### Importer

- one-shot import service for initial course data

### PostgreSQL

- shared relational database
- used by auth and backend
- schema managed by Flyway

## Profiles

Supported Spring profiles:

- `dev`
- `test`
- `prod`

Profiles are injected via environment variables and are not hardcoded in the
runtime model.

## CI And Images

GitHub Actions currently includes:

- auth unit tests and coverage
- backend build
- Docker Compose smoke test
- nginx config validation
- image build and push to GHCR on `main`

CI publishes:

- immutable tags in the form `sha-<shortsha>`
- moving `dev-latest` tags as a convenience pointer

## Project Structure

```text
campus-plus-plus-k8s/
├── auth/
├── backend/
├── deploy/
├── docs/
├── frontend/
├── importer/
├── nginx/
├── docker-compose.yml
└── README.md
```

## Notes

- backend does not parse JWT directly
- `campus-nginx` is the central application security gate
- frontend image no longer depends on build-time `HOST`
- importer is import-only, not scraping
- scraper repository:
  https://github.com/loonaarc/campuswiki_coursescraper

