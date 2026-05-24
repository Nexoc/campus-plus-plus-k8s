# Campus++

[![CI Pipeline](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml)

Campus++ is a full-stack Hochschule Campus Wien application packaged as a
home Kubernetes lab on one physical PC with VM clones.

The active target is home-only:

```text
home lab on one physical PC
```

The server roles stay stable:

```text
gw          -> gateway / runner / ansible / edge
s4-db       -> PostgreSQL
s5-dev      -> dev k3s
s6-monitoring -> Prometheus + Grafana
s1-prod     -> prod k3s node 1
s2-prod     -> prod k3s node 2
s3-prod     -> prod k3s node 3
```

`uni` is not an active target. The names `s4-db`, `s5-dev`,
`s6-monitoring`, and `s1-prod/s2-prod/s3-prod` are home-lab VM roles.

## What This Demonstrates

- Vue 3 frontend, Spring Boot auth/backend services, importer, and nginx app gateway
- immutable GHCR images for all application components
- validation-only CI on `main`
- tag-driven home dev and home prod releases
- self-hosted GitHub Actions runner on `gw`
- k3s dev and production clusters
- Kustomize deployment overlays under `deploy/app`
- Envoy Gateway / Gateway API as the Kubernetes entry layer
- external PostgreSQL through the stable `s4-db` runtime alias
- Ansible ops automation under `ops/`
- central monitoring on `s6-monitoring`

## Current Status

Current platform target:

```text
home lab on one physical PC with VM clones
```

Release channels:

```text
home-dev-* -> s5-dev / campus-dev
home-v*    -> s1-prod/s2-prod/s3-prod / campus-prod
```

Current active paths:

```text
home dev request:
client -> gw -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> app -> s4-db

home prod request:
client -> gw -> s1-prod|s2-prod|s3-prod:30080 -> Envoy Gateway -> campus-prod -> app -> s4-db

home monitoring:
VMs and k3s clusters -> s6-monitoring -> Prometheus -> Grafana
```

Current hostnames:

```text
home dev      home-campus-dev.davl.at
home prod     home-campus-prod.davl.at
home grafana  home-grafana.davl.at
```

The portable production database alias is generated as `service/s4-db` and
`endpointslice/s4-db` in `campus-prod`. The real database endpoint comes from
host-local runtime files on `gw`.

Monitoring automation is available under `ops/`. Current home-lab verification
should be performed after the home-only documentation/workflow refactor.

## Architecture

Application components:

- `frontend`: Vue 3 single-page application
- `auth`: Spring Boot authentication service
- `backend`: Spring Boot API service
- `campus-nginx`: internal application gateway and auth boundary
- `campus-importer`: one-shot data import job
- PostgreSQL: external database on `s4-db`

Platform components:

- Docker for local development and image builds
- GHCR for application images
- GitHub Actions for CI and release orchestration
- `home-gw-runner` for deployment jobs
- k3s on `s5-dev` for dev
- k3s HA on `s1-prod`, `s2-prod`, and `s3-prod` for prod
- Kustomize for Kubernetes overlays
- Envoy Gateway / Gateway API for Kubernetes ingress
- nginx on `gw` for the home edge path
- Ansible for host checks, bootstrap, Envoy install, database access, and monitoring
- Prometheus and Grafana on `s6-monitoring`

## Runtime Modes

### Local Docker Runtime

Use the root `docker-compose.yml` for local workstation development and smoke
testing. This is not the server deployment path.

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

### Kubernetes Runtime

Active overlays:

- `deploy/app/overlays/home`: home dev, namespace `campus-dev`, target `s5-dev`
- `deploy/app/overlays/prod`: home prod, namespace `campus-prod`, target `s1-prod/s2-prod/s3-prod`

`deploy/app/overlays/dev` is legacy/manual compatibility. The active home dev
release channel is `home-dev-*` through `deploy/app/overlays/home`.

Shared deployment helpers:

- `deploy/scripts/apply-overlay.sh`
- `deploy/scripts/verify-overlay.sh`

Manual home dev render/apply shape:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash deploy/scripts/apply-overlay.sh \
  --environment home \
  --image-tag home-dev-example

bash deploy/scripts/verify-overlay.sh \
  --environment home \
  --expected-nodeport 30080
```

### Ops And Monitoring Runtime

Use `ops/` from `gw`:

```text
local pc -> gw -> ansible/ssh/kubectl/helm -> all servers/prod cluster
```

Use the home inventory:

```text
ops/inventory/home.local.ini
```

Monitoring roles:

- node-exporter on all VMs
- Prometheus on `s6-monitoring`
- Grafana on `s6-monitoring`
- postgres exporter on `s4-db`
- kube-state-metrics in dev/prod clusters

## CI/CD

`main` is the working branch.

Current GitHub Actions model:

- `push` to `main`: validation only
- `pull_request` to `main`: validation only
- `home-dev-*` tag: build/push images and deploy home dev to `s5-dev`
- `home-v*` tag: build/push images and deploy home prod after `home-production` approval

Release images are tagged exactly with the Git tag that triggered the workflow.

Example home dev release:

```bash
# server: local pc
git tag home-dev-example
git push origin home-dev-example
```

Expected result:

- GHCR images are published with tag `home-dev-example`
- deploy runs on runner labels `home+gw+deploy`
- `home` overlay is applied to namespace `campus-dev`
- smoke check uses `Host: home-campus-dev.davl.at` against `s5-dev:30080`

Example home prod release:

```bash
# server: local pc
git tag home-v0.1.2
git push origin home-v0.1.2
```

Expected result:

- GHCR images are published with tag `home-v0.1.2`
- deploy waits for GitHub environment `home-production`
- deploy runs on runner labels `home+gw+deploy`
- `prod` overlay is applied to namespace `campus-prod`
- smoke checks use `Host: home-campus-prod.davl.at` against prod nodes

## Runner Model

GitHub Actions targets self-hosted runners by labels, not runner names.

Active runner:

```text
runner name: home-gw-runner
labels: self-hosted, Linux, X64, home, gw, deploy
```

The runner needs:

- `/home/nexoc/.kube/dev.yaml`
- `/home/nexoc/.kube/prod.yaml`
- `/home/nexoc/campus-secrets/home/*`
- `/home/nexoc/campus-secrets/prod/*`
- GHCR pull credentials configured in GitHub Actions

## Repository Layout

```text
campus-plus-plus/
├── auth/
├── backend/
├── deploy/
│   ├── app/
│   ├── docs/
│   ├── infra/
│   ├── monitoring/
│   ├── scripts/
│   └── templates/
├── docs/
├── frontend/
├── importer/
├── nginx/
├── ops/
├── docker-compose.yml
└── README.md
```

## Documentation

Primary docs:

- [Home Lab Architecture](docs/home-lab-architecture.md)
- [Final Platform Status](docs/final-platform-status.md)
- [Platform Installation Runbook](docs/platform-installation-runbook.md)
- [Runtime Inputs](docs/runtime-inputs.md)
- [Deployment Runbook](deploy/README.md)
- [Environments](deploy/docs/environments.md)
- [Naming Convention](deploy/docs/naming-convention.md)
- [Rollout Notes](deploy/docs/rollout-notes.md)
- [Production CD Design](deploy/docs/production-cd-design.md)

Ops and monitoring docs:

- [Ops Automation](ops/README.md)
- [Ansible Inventories](ops/inventory/README.md)
- [Runtime Automation Wrappers](ops/scripts/runtime/README.md)
- [Monitoring Design](ops/docs/monitoring-design.md)
- [Monitoring Runtime Model](ops/docs/monitoring-runtime.md)

Product docs:

- [Requirements Snapshot](docs/requirements.md)
- [SRS](docs/SRS.md)

## Next Work

- verify home dev through `home-dev-*`
- verify home prod through `home-v*`
- verify monitoring on `s6-monitoring` with `home-grafana.davl.at`
- keep only `home-gw-runner` as the active deployment runner on `gw`
- replace initial prod kubeconfig with an RBAC-limited deployer kubeconfig
- add Prometheus alert rules and Alertmanager
- add Loki or Grafana Alloy later
