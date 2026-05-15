# Campus++

[![CI Pipeline](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml)

Campus++ is a full-stack Hochschule Campus Wien application packaged and
deployed as a production-like Kubernetes / DevOps portfolio project.

The current focus is not only the application code, but the delivery platform
around it:

```text
Campus++ app
  -> Docker images
  -> GHCR
  -> k3s non-prod clusters
  -> Envoy Gateway / Gateway API
  -> tag-based CI/CD
  -> monitoring and PROD later
```

## What This Demonstrates

This repository is maintained as a DevOps/Kubernetes migration project, not just
as a full-stack app repository.

It currently demonstrates:

- containerized frontend, backend, auth, importer, and nginx services
- immutable GHCR release images tagged from Git tags
- validation-only CI on `main`
- controlled non-prod releases from `dev-*` and `home-*` tags
- self-hosted GitHub Actions runners pinned by labels
- Kustomize-based Kubernetes deployment overlays under `deploy/app`
- Envoy Gateway / Gateway API as the active Kubernetes entry layer
- a dedicated `gw` nginx edge baseline in repo
- external PostgreSQL on `s4-db`
- monitoring and future PROD rollout planned as separate phases

## Current Status

The active lab DEV deployment is working through Envoy Gateway on `s5-dev`.

Verified release:

- Git tag: `dev-2026.04.24-1`
- Images: `ghcr.io/nexoc/campus-*:dev-2026.04.24-1`
- Target cluster: `s5-dev`
- Namespace: `campus-dev`
- Entry point: Envoy Gateway `NodePort 30080`

Current lab request path:

```text
client
  -> gw
  -> s5-dev:30080
  -> Envoy Gateway / Gateway API
  -> campus-nginx
  -> frontend / auth / backend
  -> PostgreSQL s4-db
```

Confirmed runtime state:

- `frontend`, `auth`, `backend`, and `campus-nginx` are running in Kubernetes
- `campus-importer` completes successfully
- `Gateway/campus` is `Programmed=True`
- `HTTPRoute/campus` is accepted and routes to `campus-nginx`
- `EnvoyProxy/campus-edge` publishes `NodePort 30080`
- `ClientTrafficPolicy/campus-edge` is present
- Envoy Gateway / Gateway API is the only active Kubernetes entry layer

## Architecture

Main application components:

- `frontend`: Vue 3 single-page application
- `auth`: Spring Boot authentication service
- `backend`: Spring Boot API service
- `campus-nginx`: internal application gateway and auth boundary
- `campus-importer`: one-shot data import job
- PostgreSQL: external database on `s4-db`

Delivery and platform components:

- GHCR for application images
- GitHub Actions for CI and release orchestration
- self-hosted runners for cluster deployment
- k3s for non-prod Kubernetes
- Envoy Gateway / Gateway API for Kubernetes ingress
- nginx on `gw` for the lab edge proxy

## Runtime Modes

### Local Docker Runtime

Use the root `docker-compose.yml` for local development and smoke testing.

```bash
docker compose --env-file .env.dev up -d --build
```

Open:

```text
http://localhost
```

Stop:

```bash
docker compose --env-file .env.dev down -v --remove-orphans
```

### Kubernetes Non-Prod Runtime

Use `deploy/app/overlays/` for Kubernetes deployments.

Active overlays:

- `deploy/app/overlays/dev`: lab cluster on `s5-dev`
- `deploy/app/overlays/home`: home cluster, same app model with its own edge patch
- `deploy/app/overlays/prod`: reserved for future PROD work

Shared deployment helpers:

- `deploy/scripts/apply-overlay.sh`
- `deploy/scripts/verify-overlay.sh`

Shared infrastructure baselines:

- `deploy/infra/envoy-gateway/`
- `deploy/infra/gw-nginx/`

Manual Kubernetes deployment uses the same release contract as the workflow:

```bash
# server: s5-dev
bash deploy/scripts/apply-overlay.sh \
  --environment dev \
  --image-tag dev-2026.04.24-1

bash deploy/scripts/verify-overlay.sh \
  --environment dev \
  --expected-nodeport 30080
```

## CI/CD

`main` is the only working branch.

Current GitHub Actions behavior:

- `push` to `main`: validation only
- `pull_request` to `main`: validation only
- `dev-*` tag: build/push images and deploy to lab `s5`
- `home-*` tag: build/push images and deploy to the home runner
- `v*` tags: reserved for controlled PROD releases; no active PROD workflow yet

Release images are tagged exactly with the Git tag that triggered the workflow.

Example DEV release:

```bash
git tag dev-2026.04.24-1
git push origin dev-2026.04.24-1
```

Expected result:

- `Non-Prod Release` workflow starts
- GHCR images are published with tag `dev-2026.04.24-1`
- `Deploy DEV to s5` runs on runner labels `dev+s5`
- `Deploy HOME to home runner` is skipped
- Kubernetes rollout is verified through Envoy/Gateway API checks

## Runner Model

GitHub Actions targets self-hosted runners by labels, not by runner names.

Current non-prod label contract:

- lab runner: `self-hosted`, `Linux`, `X64`, `dev`, `s5`
- home runner: `self-hosted`, `Linux`, `X64`, `dev`, `home`

The release workflow depends on these labels:

- `dev-*` requires `dev+s5`
- `home-*` requires `dev+home`

## Repository Layout

```text
campus-plus-plus/
├── auth/
├── backend/
├── deploy/
│   ├── app/
│   │   ├── base/
│   │   └── overlays/
│   ├── docs/
│   ├── infra/
│   │   ├── envoy-gateway/
│   │   └── gw-nginx/
│   ├── scripts/
│   └── templates/
├── docs/
├── frontend/
├── importer/
├── nginx/
├── docker-compose.yml
└── README.md
```

## Deployment Docs

- [Deployment Runbook](deploy/README.md)
- [Environments](deploy/docs/environments.md)
- [Naming Convention](deploy/docs/naming-convention.md)
- [Rollout Notes](deploy/docs/rollout-notes.md)
- [Production CD Design](deploy/docs/production-cd-design.md)
- [Deployment Structure](deploy/docs/structure.md)
- [GW nginx baseline](deploy/infra/gw-nginx/README.md)
- [Envoy Gateway baseline](deploy/infra/envoy-gateway/README.md)

## Next Work

Current planned work:

- apply and verify the repo-owned `gw` nginx baseline on the gateway host
- harden the lab edge with a stable public hostname and TLS
- bring up monitoring on `s6`
- review the `home` overlay before the first real `home-*` release
- prepare the documented PROD release path for `v*` tags

Recommended monitoring target for `s6`:

- 2 vCPU
- 4 GB RAM
- 30-50 GB disk
- Prometheus, Grafana, Alertmanager, node-exporter, kube-state-metrics
