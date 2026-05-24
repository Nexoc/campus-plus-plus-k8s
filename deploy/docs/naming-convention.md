# Naming Convention

This document defines naming rules for the home-only Campus++ lab.

## Guiding Rules

- keep workload names stable across home dev and home prod
- keep release identity in immutable Git tags
- keep runner routing in labels
- keep VM roles as stable logical hostnames
- keep real addresses out of tracked manifests and workflows

## Kubernetes Resource Naming

Canonical names:

- dev namespace: `campus-dev`
- prod namespace: `campus-prod`
- frontend deployment/service: `frontend`
- auth deployment/service: `auth`
- backend deployment/service: `backend`
- internal app gateway: `campus-nginx`
- importer job: `campus-importer`
- external database alias: `s4-db`
- Gateway: `campus`
- HTTPRoute: `campus`
- EnvoyProxy: `campus-edge`
- ClientTrafficPolicy: `campus-edge`

These names stay stable across the `home` and `prod` overlays.

## Image Naming

GHCR images:

- `ghcr.io/nexoc/campus-frontend`
- `ghcr.io/nexoc/campus-auth`
- `ghcr.io/nexoc/campus-backend`
- `ghcr.io/nexoc/campus-nginx`
- `ghcr.io/nexoc/campus-importer`

Rules:

- one image name per component
- deployment identity belongs in the tag
- release images are tagged exactly with the Git tag that triggered the workflow

Examples:

- `home-dev-example`
- `home-v0.1.0`
- `home-v-render-test`

## Release Tag Naming

Active release tags:

```text
home-dev-* -> home dev on s5-dev / campus-dev
home-v*    -> home prod on s1-prod/s2-prod/s3-prod / campus-prod
```

## Runner Label Naming

Active workflow targeting rules:

```text
self-hosted, Linux, X64, home, gw, deploy
```

Recommended runner name:

```text
home-gw-runner
```

GitHub Actions schedules against labels, not runner names.

## Hostname Strategy

Home dev:

- route hostname: `home-campus-dev.davl.at`
- NodePort entry: `s5-dev:30080`
- stable in-cluster database alias: `s4-db`
- real external database endpoint: `/home/nexoc/campus-secrets/home/db-endpoint.env`

Home prod:

- route hostname: `home-campus-prod.davl.at`
- NodePort entries: `s1-prod:30080`, `s2-prod:30080`, `s3-prod:30080`
- stable in-cluster database alias: `s4-db`
- real external database endpoint: `/home/nexoc/campus-secrets/prod/db-endpoint.env`

Home monitoring:

- Grafana hostname: `home-grafana.davl.at`

## Ops And Monitoring Naming

Stable logical hostnames:

- control host: `gw`
- database host: `s4-db`
- dev cluster host: `s5-dev`
- monitoring host: `s6-monitoring`
- prod cluster hosts: `s1-prod`, `s2-prod`, `s3-prod`

Monitoring service names:

- node-exporter systemd service: `prometheus-node-exporter`
- Prometheus systemd service: `prometheus`
- Grafana systemd service: `grafana-server`
- Prometheus scrape job for VMs: `node-exporter`
- Grafana datasource name: `Campus Prometheus`
- Grafana datasource UID: `campus-prometheus`
- Grafana dashboard folder: `Campus++`

Monitoring labels use stable host identity:

- `instance`: logical hostname from inventory, such as `s1-prod`
- `role`: `gw`, `db`, `dev`, `monitoring`, or `prod`
- `environment`: inventory-defined monitoring environment label

## Repo Alignment

The repo should reflect:

- home-only active target
- stable component and service names
- `deploy/app` as the canonical manifest tree
- Envoy/Gateway API as the active ingress layer
- tag-driven home dev delivery via `home-dev-*`
- controlled home prod delivery via `home-v*`
- `ops/` as the canonical Ansible and monitoring automation tree
