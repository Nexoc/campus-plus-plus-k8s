# Campus++

[![CI Pipeline](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml)

Campus++ is a full-stack Hochschule Campus Wien application packaged and
operated as a production-like Kubernetes / DevOps portfolio project.

The repository is not only application code. It also contains the delivery,
operations, and monitoring model around the app:

```text
Campus++ app
  -> Docker images
  -> GHCR
  -> k3s dev/prod clusters
  -> Envoy Gateway / Gateway API
  -> tag-based GitHub Actions CI/CD
  -> Ansible ops automation
  -> central monitoring on s6-monitoring
```

## What This Demonstrates

This repository is maintained as a DevOps/Kubernetes migration project, not
just as a full-stack app repository.

It currently demonstrates:

- Vue 3 frontend, Spring Boot auth/backend services, importer, and nginx app gateway
- containerized application components published as immutable GHCR images
- validation-only CI on `main`
- controlled non-prod releases from `dev-*` and `home-*` tags
- controlled production releases from `v*` tags with GitHub environment approval
- self-hosted GitHub Actions runners pinned by labels
- Kustomize-based Kubernetes deployment overlays under `deploy/app`
- k3s single-node DEV and HA PROD Kubernetes targets
- Envoy Gateway / Gateway API as the active Kubernetes entry layer
- `gw` nginx edge baseline for lab traffic forwarding
- external PostgreSQL reached through the stable `s4-db` runtime alias
- environment-specific database endpoint config kept outside git
- Ansible-based ops checks and host-level automation under `ops/`
- central monitoring on `s6-monitoring` with node-exporter, Prometheus, Grafana,
  postgres exporter, kube-state-metrics, and Campus++ dashboards

## Current Status

Current platform baseline:

- technical baseline is complete in the current lab infrastructure
- final implemented platform summary: [Campus++ Final Platform Status](docs/final-platform-status.md)
- DEV CD is working through `dev-*` tags, `s5-campus-dev`, `campus-dev`, and Envoy NodePort `30080`
- PROD CD is working through `v*` tags, GitHub `production` approval, `gw-campus-prod`, `campus-prod`, and Envoy NodePort `30080`
- the documented PROD release baseline is `v0.1.1`
- the portable PROD database alias is working through generated `service/s4-db` and `endpointslice/s4-db`
- the Ansible ops/check layer is ready
- central monitoring is ready on `s6-monitoring`

Current DEV request path:

```text
client
  -> gw
  -> s5-dev:30080
  -> Envoy Gateway / Gateway API
  -> campus-nginx
  -> frontend / auth / backend
  -> PostgreSQL s4-db
```

Current PROD request path:

```text
client
  -> gw edge
  -> s1-prod|s2-prod|s3-prod:30080
  -> Envoy Gateway / Gateway API
  -> campus-prod
  -> campus-nginx
  -> frontend / auth / backend
  -> PostgreSQL s4-db
```

Current monitoring path:

```text
all lab VMs
  -> node-exporter:9100
  -> Prometheus on s6-monitoring
  -> Grafana on s6-monitoring

s4-db
  -> postgres-exporter:9187
  -> Prometheus on s6-monitoring

dev/prod k3s clusters
  -> kube-state-metrics:30091/30092
  -> Prometheus on s6-monitoring

Prometheus
  -> Grafana dashboards:
     Campus VM Overview
     Campus PostgreSQL Overview
     Campus Kubernetes Overview
```

Confirmed Kubernetes runtime state:

- `frontend`, `auth`, `backend`, and `campus-nginx` run in Kubernetes
- `campus-importer` completes successfully
- `Gateway/campus` is `Programmed=True`
- `HTTPRoute/campus` is accepted and routes to `campus-nginx`
- `EnvoyProxy/campus-edge` publishes NodePort `30080`
- `ClientTrafficPolicy/campus-edge` is present
- Envoy Gateway / Gateway API is the active Kubernetes entry layer

## Architecture

Application components:

- `frontend`: Vue 3 single-page application
- `auth`: Spring Boot authentication service
- `backend`: Spring Boot API service
- `campus-nginx`: internal application gateway and auth boundary
- `campus-importer`: one-shot data import job
- PostgreSQL: external database reached through `s4-db`

Delivery and platform components:

- Docker for local and image build runtime
- GHCR for application images
- GitHub Actions for CI and release orchestration
- self-hosted runners for cluster deployment
- k3s for DEV and PROD Kubernetes
- Kustomize for app overlays and Kubernetes monitoring manifests
- Helm for Kubernetes add-ons such as Envoy Gateway
- Envoy Gateway / Gateway API for Kubernetes ingress
- nginx on `gw` for the lab edge proxy
- Ansible for host bootstrap, checks, firewall/database access, Envoy install, and monitoring setup
- systemd for host services such as GitHub runners, node-exporter, postgres exporter, Prometheus, and Grafana
- iptables for lab firewall boundaries
- Prometheus and Grafana for central monitoring and dashboards

## Runtime Modes

### Local Docker Runtime

Use the root `docker-compose.yml` for local workstation development and smoke
testing. This is not part of the server deployment path.

```text
docker compose --env-file .env.dev up -d --build
```

Open:

```text
http://localhost
```

Stop:

```text
docker compose --env-file .env.dev down -v --remove-orphans
```

### Kubernetes Runtime

Use `deploy/app/overlays/` for Kubernetes deployments.

Active overlays:

- `deploy/app/overlays/dev`: lab DEV cluster on `s5-dev`
- `deploy/app/overlays/home`: home cluster, same app model with its own edge patch
- `deploy/app/overlays/prod`: production cluster on `s1-prod`, `s2-prod`, and `s3-prod`

Shared deployment helpers:

- `deploy/scripts/apply-overlay.sh`
- `deploy/scripts/verify-overlay.sh`

Shared infrastructure baselines:

- `deploy/infra/envoy-gateway/`
- `deploy/infra/gw-nginx/`

Manual Kubernetes deployment uses the same release contract as the workflow:

```bash
# server: s5-dev
cd /home/nexoc/campus-plus-plus-k8s
bash deploy/scripts/apply-overlay.sh \
  --environment dev \
  --image-tag dev-example

bash deploy/scripts/verify-overlay.sh \
  --environment dev \
  --expected-nodeport 30080
```

### Ops And Monitoring Runtime

Use `ops/` from `gw` for Ansible-based operations.

The model is:

```text
local pc -> gw -> ansible/ssh/kubectl/helm -> all servers/prod cluster
```

Monitoring core currently runs as systemd services on `s6-monitoring`:

- Prometheus on port `9090`
- Grafana on port `3000`
- node-exporter on all VMs on port `9100`
- postgres exporter on `s4-db` on port `9187`
- kube-state-metrics exposed from dev/prod clusters on NodePorts `30091` and `30092`

Monitoring is installed and checked through `ops/`. Alertmanager and Loki remain
future monitoring extensions and are not part of the current application release
workflow.

## CI/CD

`main` is the only working branch.

Current GitHub Actions behavior:

- `push` to `main`: validation only
- `pull_request` to `main`: validation only
- `dev-*` tag: build/push images and deploy to lab `s5`
- `home-*` tag: build/push images and deploy to the home runner
- `v*` tag: build/push images and deploy to PROD after `production` approval

Release images are tagged exactly with the Git tag that triggered the workflow.

Example DEV release:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
git tag dev-example
git push origin dev-example
```

Expected result:

- `Non-Prod Release` workflow starts
- GHCR images are published with tag `dev-example`
- `Deploy DEV to s5` runs on runner labels `dev+s5`
- `Deploy HOME to home runner` is skipped
- Kubernetes rollout is verified through Envoy/Gateway API checks

Example PROD release:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
git tag v0.1.2
git push origin v0.1.2
```

Expected result:

- `Production Release` workflow starts
- GHCR images are published with tag `v0.1.2`
- `Deploy PROD to k3s HA cluster` waits for `production` environment approval
- after approval, deploy runs on runner labels `prod+gw`
- Kubernetes rollout is verified in namespace `campus-prod`

## Runner Model

GitHub Actions targets self-hosted runners by labels, not by runner names.

Current runner routing:

- `dev-*` requires `self-hosted`, `Linux`, `dev`, `s5`
- `home-*` requires `self-hosted`, `Linux`, `dev`, `home`
- `v*` requires `self-hosted`, `Linux`, `X64`, `prod`, `gw`

Current runner names:

- DEV runner: `s5-campus-dev`
- PROD runner: `gw-campus-prod`

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
│   ├── monitoring/
│   ├── scripts/
│   └── templates/
├── docs/
├── frontend/
├── importer/
├── nginx/
├── ops/
│   ├── docs/
│   ├── inventory/
│   ├── playbooks/
│   ├── scripts/
│   └── templates/
├── docker-compose.yml
└── README.md
```

## Documentation

Deployment docs:

- [Final Platform Status](docs/final-platform-status.md)
- [Deployment Runbook](deploy/README.md)
- [Runtime Inputs](docs/runtime-inputs.md)
- [Environments](deploy/docs/environments.md)
- [Naming Convention](deploy/docs/naming-convention.md)
- [Rollout Notes](deploy/docs/rollout-notes.md)
- [Production CD Design](deploy/docs/production-cd-design.md)
- [Deployment Structure](deploy/docs/structure.md)
- [GW nginx baseline](deploy/infra/gw-nginx/README.md)
- [Envoy Gateway baseline](deploy/infra/envoy-gateway/README.md)

Ops and monitoring docs:

- [Ops Automation](ops/README.md)
- [Monitoring Design](ops/docs/monitoring-design.md)
- [Monitoring Runtime Model](ops/docs/monitoring-runtime.md)

Product docs:

- [Requirements Snapshot](docs/requirements.md)
- [SRS](docs/SRS.md)

## Next Work

Current planned work:

- apply and harden the repo-owned `gw` nginx edge path with a stable public hostname and TLS
- harden the PROD edge path through `gw`
- replace the initial PROD kubeconfig with an RBAC-limited deployer kubeconfig
- add Prometheus alert rules and Alertmanager
- add Loki and log collection later
- review the `home` overlay before the first real `home-*` release
