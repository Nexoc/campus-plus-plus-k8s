# Environments

This document describes the current Campus++ environment model after the move
to tag-driven releases and the addition of the Ansible/monitoring layer.

## Infrastructure Roles

Lab environment:

- `gw`: gateway, NAT, SSH jump host, Ansible control host, and PROD deploy control host
- `s5-dev`: single-node k3s DEV cluster and lab self-hosted runner
- `s4-db`: PostgreSQL outside Kubernetes
- `s6-monitoring`: central monitoring VM
- `s1-prod`, `s2-prod`, `s3-prod`: k3s HA production cluster nodes

IP addresses belong in inventory, DNS, or host-local runtime configuration, not
in Kubernetes application manifests or GitHub workflows. Runtime configuration
uses hostnames and stable contracts so the same repo can be deployed from a
future environment without editing tracked Kubernetes files.

Home environment:

- separate self-hosted runner and k3s cluster
- same application layout as lab
- its own edge hostname patch and runner labels

Production environment:

- `s1-prod`, `s2-prod`, and `s3-prod`: k3s HA production cluster nodes
- `gw`: production deployment control host for the `v*` workflow
- application hostname: `campus-prod.davl.at`
- `s4-db`: stable Kubernetes DNS alias for external PostgreSQL in `campus-prod`

Monitoring environment:

- `s6-monitoring`: central monitoring VM
- Prometheus and Grafana run as systemd services on `s6-monitoring`
- node-exporter runs as a systemd service on every lab VM
- Kubernetes monitoring add-ons are installed later inside dev/prod clusters

## Deployment Model

Campus++ keeps application code, deployment code, and operations code separate:

- application code stays in `frontend/`, `auth/`, `backend/`, `importer/`, and `nginx/`
- active Kubernetes manifests live under `deploy/app/overlays/`
- shared infra baselines live under `deploy/infra/`
- templates live under `deploy/templates/`
- Ansible inventories, playbooks, scripts, and monitoring templates live under `ops/`

## Runtime Environments

Current active app environments:

- `dev`: lab cluster on `s5-dev`, namespace `campus-dev`
- `home`: home cluster, same namespace layout on a separate cluster
- `prod`: HA cluster on `s1-prod`, `s2-prod`, and `s3-prod`, namespace `campus-prod`

Current release channels:

- `dev-*` deploys to the lab cluster on runner labels `dev+s5`
- `home-*` deploys to the home cluster on runner labels `dev+home`
- `main` runs validation only
- `v*` deploys to PROD through the `production` environment and `prod+gw` runner

Current ops/monitoring channels:

- Ansible playbooks are run manually from `gw`
- host bootstrap and checks are not part of application release tags
- monitoring services are installed and verified through `ops/playbooks/`

## Current Request Paths

Lab DEV path:

`Internet -> gw -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> services -> PostgreSQL s4-db`

Home path:

`Home edge hostname -> home cluster NodePort 30080 -> Envoy Gateway -> campus-nginx -> services`

Production path:

`Internet -> gw -> prod nodes NodePort 30080 -> Envoy Gateway -> campus-prod -> services -> PostgreSQL s4-db`

Monitoring path:

`VMs -> node-exporter:9100 -> Prometheus on s6-monitoring -> Grafana on s6-monitoring`

Notes:

- Envoy Gateway is the active entry layer for non-prod and PROD environments
- `campus-nginx` remains the internal app gateway and auth boundary
- PostgreSQL stays outside Kubernetes for the lab environment
- in PROD, the real external PostgreSQL endpoint is provided by
  `/home/nexoc/campus-secrets/prod/db-endpoint.env` on `gw`, not by committed
  manifests
- monitoring is operational infrastructure, not an application CD artifact

## Configuration Strategy

Current delivery uses:

- Kustomize overlays in `deploy/app/overlays/dev` and `deploy/app/overlays/home`
- the production overlay in `deploy/app/overlays/prod`
- Envoy Gateway baselines in `deploy/infra/envoy-gateway/`
- versioned non-secret config files under each overlay
- ignored secret env files under each overlay
- GHCR images tagged exactly with the pushed release tag
- Ansible inventory in `ops/inventory/lab.local.ini` for lab host operations
- host-local runtime files under `/home/nexoc/campus-secrets`

## Secrets

Self-hosted runners stage app secrets from fixed host paths:

- `/home/nexoc/campus-secrets/dev/`
- `/home/nexoc/campus-secrets/home/`
- `/home/nexoc/campus-secrets/prod/`

Expected app secret files per environment:

- `db-secrets.env`
- `auth-secrets.env`

PROD also requires:

- `db-endpoint.env`

Monitoring runtime secret files, when needed, stay under:

- `/home/nexoc/campus-secrets/monitoring/`

Real secrets must not be committed or printed.

## What Is Still Outside Repo Or Incomplete

The following remain future or partially external work:

- TLS/public hostname hardening for the lab edge
- the final public hostname for the home overlay
- PROD edge hardening and an RBAC-limited deployer kubeconfig
- PostgreSQL exporter for `s4-db`
- kube-state-metrics for dev/prod clusters
- Prometheus alert rules and Alertmanager
- Loki or Grafana Alloy/log collection
