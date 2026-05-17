# Runtime Inputs

This document lists the files and settings that must exist outside normal
source code before running Campus++ locally, deploying it to Kubernetes, or
operating the lab infrastructure.

Real secrets, tokens, passwords, and environment-specific IP addresses must not
be committed.

## Source Of Truth

Tracked examples and templates:

```text
deploy/templates/config/*.env.example
deploy/templates/secrets/*.env.example
ops/inventory/*.example.ini
ops/templates/*.env.example
```

Untracked runtime files:

```text
.env.dev
.env.test
.env.prod
deploy/app/overlays/*/secrets/*.env
ops/inventory/*.local.ini
/home/nexoc/campus-secrets/*
```

## Local Docker Runtime

Used by:

```text
docker-compose.yml
```

Expected local files at repo root:

```text
.env.dev
.env.test
.env.prod
```

The Docker Compose runtime needs these variables:

```text
BACKEND_PROFILE
AUTH_PROFILE
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
JWT_SECRET
JWT_EXPIRATION
```

Typical local Docker command:

```text
docker compose --env-file .env.dev up -d --build
```

These files are ignored by git. Do not commit real database or JWT values.

## Kubernetes App Config

Tracked non-secret config files live under each overlay:

```text
deploy/app/overlays/dev/config/auth-config.env
deploy/app/overlays/dev/config/backend-config.env
deploy/app/overlays/dev/config/importer-config.env

deploy/app/overlays/home/config/auth-config.env
deploy/app/overlays/home/config/backend-config.env
deploy/app/overlays/home/config/importer-config.env

deploy/app/overlays/prod/config/auth-config.env
deploy/app/overlays/prod/config/backend-config.env
deploy/app/overlays/prod/config/importer-config.env
```

These define non-secret runtime settings such as:

```text
SPRING_PROFILES_ACTIVE
DB_HOST
DB_PORT
DB_NAME
```

Current stable app contract:

```text
DB_HOST=s4-db
```

For PROD, `s4-db` is a Kubernetes Service/EndpointSlice alias generated at
render/apply time. The real database endpoint is not stored in Kubernetes
manifests.

## Kubernetes App Secrets

Tracked templates:

```text
deploy/templates/secrets/db-secrets.env.example
deploy/templates/secrets/auth-secrets.env.example
deploy/templates/secrets/db-endpoint.env.example
```

Required keys:

```text
db-secrets.env:
  DB_USERNAME
  DB_PASSWORD

auth-secrets.env:
  JWT_SECRET
  JWT_EXPIRATION

db-endpoint.env:
  DB_ENDPOINT_ADDRESS
  DB_ENDPOINT_PORT
```

The deploy scripts stage real secret files from host-local paths.

Required host-local files by environment:

```text
dev:
  /home/nexoc/campus-secrets/dev/db-secrets.env
  /home/nexoc/campus-secrets/dev/auth-secrets.env

home:
  /home/nexoc/campus-secrets/home/db-secrets.env
  /home/nexoc/campus-secrets/home/auth-secrets.env

prod:
  /home/nexoc/campus-secrets/prod/db-secrets.env
  /home/nexoc/campus-secrets/prod/auth-secrets.env
  /home/nexoc/campus-secrets/prod/db-endpoint.env
```

`auth-secrets.env` is needed in every app environment because the auth service
needs `JWT_SECRET` and `JWT_EXPIRATION`.

`db-endpoint.env` is currently PROD-only. It is not an app secret mounted into
pods; it is read by `deploy/scripts/apply-overlay.sh` on `gw` to generate the
`s4-db` Service and EndpointSlice.

DEV runtime files on `s5-dev`:

```text
/home/nexoc/campus-secrets/dev/db-secrets.env
/home/nexoc/campus-secrets/dev/auth-secrets.env
```

HOME runtime files on the home runner:

```text
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
```

PROD runtime files on `gw`:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

Create directories with restrictive permissions:

```bash
# server: gw
mkdir -p /home/nexoc/campus-secrets/prod
chmod 700 /home/nexoc/campus-secrets /home/nexoc/campus-secrets/prod
chmod 600 /home/nexoc/campus-secrets/prod/*.env
```

Use the same pattern on `s5-dev` for DEV:

```bash
# server: s5-dev
mkdir -p /home/nexoc/campus-secrets/dev
chmod 700 /home/nexoc/campus-secrets /home/nexoc/campus-secrets/dev
chmod 600 /home/nexoc/campus-secrets/dev/*.env
```

Do not print the contents of these files in logs or chat.

## Kubernetes Access Files

DEV deploy runner on `s5-dev` expects:

```text
/home/nexoc/.kube/config
```

PROD deploy runner on `gw` expects:

```text
/home/nexoc/.kube/prod.yaml
```

The production workflow uses the explicit kubeconfig path:

```text
KUBECONFIG=/home/nexoc/.kube/prod.yaml
```

## GHCR Pull Credentials

Repository or organization secrets in GitHub Actions:

```text
GHCR_PULL_USERNAME
GHCR_PULL_TOKEN
```

These are not files in the repo. They are used by deploy workflows to create or
update the Kubernetes `ghcr-pull` image pull secret.

Do not print token values.

## GitHub Environments And Runners

Production release requires:

```text
GitHub environment: production
required reviewers: enabled
runner labels: self-hosted, Linux, X64, prod, gw
```

DEV release requires:

```text
runner labels: self-hosted, Linux, dev, s5
```

HOME release requires:

```text
runner labels: self-hosted, Linux, dev, home
```

## Ansible Inventory

Tracked examples:

```text
ops/inventory/lab.example.ini
ops/inventory/university.example.ini
```

Ignored runtime inventories:

```text
ops/inventory/lab.local.ini
ops/inventory/university.local.ini
```

Create the lab inventory on `gw`:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
cp ops/inventory/lab.example.ini ops/inventory/lab.local.ini
```

Then fill in environment-specific `ansible_host` and `monitoring_scrape_host`
values. See `ops/inventory/README.md`.

## Monitoring Runtime Files

Current implemented monitoring stack does not require committed secrets.

Optional or future runtime files:

```text
/home/nexoc/campus-secrets/monitoring/grafana.env
/home/nexoc/campus-secrets/monitoring/postgres-exporter.env
/home/nexoc/campus-secrets/monitoring/alertmanager.env
```

Tracked examples:

```text
ops/templates/grafana.env.example
ops/templates/postgres-exporter.env.example
ops/templates/alertmanager.env.example
```

Current monitoring core:

```text
node-exporter on all VMs
Prometheus on s6-monitoring
Grafana on s6-monitoring
Campus VM Overview dashboard
```

PostgreSQL exporter runtime input is:

```text
/home/nexoc/campus-secrets/monitoring/postgres-exporter.env
```

This file is generated on `s4-db` by:

```text
ops/playbooks/render-postgres-exporter-env.yml
```

The playbook derives the exporter `DATA_SOURCE_NAME` from existing PROD
database runtime inputs without printing the password or the final DSN. The
database host and port prefer `/home/nexoc/campus-secrets/prod/db-endpoint.env`
(`DB_ENDPOINT_ADDRESS`, `DB_ENDPOINT_PORT`), then tracked PROD config
(`DB_HOST`, `DB_PORT`), then the stable fallback host `s4-db`.

Install the exporter after rendering the env file:

```text
ops/playbooks/install-postgres-exporter.yml
```

Then re-run `ops/playbooks/install-prometheus.yml` so Prometheus includes the
`postgres-exporter` scrape job for `s4-db:9187`.

Kubernetes cluster metrics use kube-state-metrics inside each k3s cluster:

```text
dev cluster:  s5-dev NodePort 30091
prod cluster: prod NodePort 30092
```

The tracked manifests live under:

```text
deploy/monitoring/kube-state-metrics
```

The install playbook is:

```text
ops/playbooks/install-kube-state-metrics.yml
```

Prometheus scrape addresses come from inventory/runtime variables. The default
dev scrape host is the `dev` host, and the default prod scrape host is the first
`prod` host. Environments can override those with inventory variables without
changing tracked manifests or workflows. After installing kube-state-metrics,
re-run `ops/playbooks/install-prometheus.yml` and then
`ops/playbooks/check-monitoring-stack.yml`.

## Minimal Startup Checklist

For DEV deploy:

```text
1. s5-dev has /home/nexoc/.kube/config
2. s5-dev has /home/nexoc/campus-secrets/dev/db-secrets.env
3. s5-dev has /home/nexoc/campus-secrets/dev/auth-secrets.env
4. GitHub has GHCR_PULL_USERNAME and GHCR_PULL_TOKEN
5. runner s5-campus-dev is online with dev+s5 labels
6. s5-dev can reach PostgreSQL as s4-db:5432
```

For PROD deploy:

```text
1. gw has /home/nexoc/.kube/prod.yaml
2. gw has /home/nexoc/campus-secrets/prod/db-secrets.env
3. gw has /home/nexoc/campus-secrets/prod/auth-secrets.env
4. gw has /home/nexoc/campus-secrets/prod/db-endpoint.env
5. GitHub environment production exists with required reviewers
6. GitHub has GHCR_PULL_USERNAME and GHCR_PULL_TOKEN
7. runner gw-campus-prod is online with prod+gw labels
8. prod cluster has or can install Envoy Gateway
```

For ops/monitoring:

```text
1. gw has ops/inventory/lab.local.ini
2. Ansible can reach all logical hosts
3. s6-monitoring is reachable from gw
4. node-exporter, Prometheus, Grafana are installed through ops playbooks
5. postgres-exporter is rendered/installed if database metrics are enabled
6. kube-state-metrics is installed if cluster metrics are enabled
7. install-prometheus.yml has been re-run after changing scrape jobs
8. check-monitoring-stack.yml passes
```
