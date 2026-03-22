# Deployment Layer

This directory contains the Kubernetes and deployment-side artifacts for
Campus++.

The goal is to keep deployment concerns next to the application repository
without forcing a reorganization of the application source itself.

Application code remains in:

- `frontend/`
- `auth/`
- `backend/`
- `importer/`
- `nginx/`

Deployment code lives in:

- `deploy/app/` for Kubernetes application manifests
- `deploy/infra/` for infrastructure-side Helm values and notes
- `deploy/templates/` for config and secret examples
- `deploy/docs/` for operational documentation

## Current Architecture

Current target request path:

`Client -> GW -> ingress-nginx -> campus-nginx -> services`

Current confirmed DEV path:

`GitHub -> GHCR -> S5 k3s -> ingress-nginx -> campus-nginx -> app -> PostgreSQL on S4`

Key points:

- Kubernetes distro is `k3s`
- app manifests are managed with Kustomize
- shared infrastructure such as `ingress-nginx` is managed with Helm values
- PostgreSQL stays outside Kubernetes on `S4`
- `campus-nginx` keeps the application-specific routing and `auth_request`
  behavior

## Structure

```text
deploy/
├── app/
│   ├── base/
│   └── overlays/
│       ├── dev/
│       └── prod/
├── scripts/
├── templates/
│   ├── config/
│   └── secrets/
├── infra/
│   └── ingress-nginx/
└── docs/
```

## Prerequisites

Before applying any overlay, make sure:

- your `kubectl` context points to the intended cluster
- `ingress-nginx` is installed for that cluster
- the target cluster can pull images from GHCR
- the cluster can reach PostgreSQL on `S4`
- overlay config and secret env files are populated with environment-specific
  values

## Config And Secrets Inputs

Application overlays load runtime settings from env files:

- `deploy/app/overlays/dev/config/`
- `deploy/app/overlays/dev/secrets/`
- `deploy/app/overlays/prod/config/`
- `deploy/app/overlays/prod/secrets/`

Reference templates live in:

- `deploy/templates/config/`
- `deploy/templates/secrets/`

Important rule:

- config inputs may be versioned
- secret templates and docs may be versioned
- real secret values must stay out of git

Current repo model:

- local secret env files are expected under each overlay `secrets/` directory
- those files are ignored by git
- create them from `deploy/templates/secrets/*.env.example`

Prepare DEV secret files:

```bash
cp deploy/templates/secrets/db-secrets.env.example deploy/app/overlays/dev/secrets/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example deploy/app/overlays/dev/secrets/auth-secrets.env
```

Prepare PROD secret files:

```bash
cp deploy/templates/secrets/db-secrets.env.example deploy/app/overlays/prod/secrets/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example deploy/app/overlays/prod/secrets/auth-secrets.env
```

## Install Or Update ingress-nginx

Add the Helm repo once:

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
```

Install or upgrade DEV controller:

```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  -f deploy/infra/ingress-nginx/values-dev.yaml
```

Install or upgrade PROD controller:

```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  -f deploy/infra/ingress-nginx/values-prod.yaml
```

## DEV Runbook

### Scripted Shortcuts

Render or apply with the helper script:

```bash
bash deploy/scripts/apply-overlay.sh --environment dev --image-tag sha-676e768 --render-only
bash deploy/scripts/apply-overlay.sh --environment dev --image-tag sha-676e768
```

Verify the rollout:

```bash
bash deploy/scripts/verify-overlay.sh --environment dev --smoke-url http://127.0.0.1:30080/ --smoke-host-header campus-dev.192-168-50-5.sslip.io
```

The helper scripts do not replace operator judgment, but they reduce repeated
manual command sequences.

GitHub-assisted alternative:

- use `.github/workflows/deploy-dev.yml`
- every successful `push` to `main` now triggers automatic DEV deploy after the CI pipeline finishes
- the same workflow still supports manual `workflow_dispatch` for reruns or controlled rechecks
- manual runs can provide an immutable image tag such as `sha-19a6a44`
- if manual `image_tag` is left empty, the workflow falls back to the checked-out commit SHA
- the workflow is intended for a Linux self-hosted runner on `S5`
- the workflow expects a custom runner label `campus-dev`
- expected workflow inputs are `image_tag`, `render_only`, `smoke_url`, `smoke_host_header`, and `timeout_seconds`

Helper script prerequisites on Debian-like hosts:

- `bash`
- `kubectl`
- `mktemp`
- `sed`
- `curl` for smoke checks in `verify-overlay.sh`

Self-hosted runner notes:

- the runner should have access to `/etc/rancher/k3s/k3s.yaml`
- local ignored secret files must already exist under `deploy/app/overlays/dev/secrets/`
- the deploy workflow uses `actions/checkout` with `clean: false` so those local secret files survive between runs
- recommended custom runner labels are `campus-dev` and `s5`

### 1. Prepare DEV secret files

Create local ignored secret files from the templates:

```bash
cp deploy/templates/secrets/db-secrets.env.example deploy/app/overlays/dev/secrets/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example deploy/app/overlays/dev/secrets/auth-secrets.env
```

### 2. Review DEV overlay inputs

Check:

- `deploy/app/overlays/dev/config/auth-config.env`
- `deploy/app/overlays/dev/config/backend-config.env`
- `deploy/app/overlays/dev/config/importer-config.env`
- `deploy/app/overlays/dev/secrets/db-secrets.env`
- `deploy/app/overlays/dev/secrets/auth-secrets.env`

### 3. Select the DEV release tag

The DEV overlay uses immutable tags.

- CI still publishes `dev-latest` as a convenience pointer
- deploys should pin a concrete `sha-<shortsha>` tag
- replace `sha-change-me` in `deploy/app/overlays/dev/kustomization.yaml`
  before apply

### 4. Render manifests before apply

```bash
kubectl kustomize deploy/app/overlays/dev
```

Use this to verify:

- namespace is `campus-dev`
- image names resolve to `ghcr.io/nexoc/...`
- current DEV host is rendered correctly
- the selected DEV tag is rendered instead of `sha-change-me`

### 5. Apply the DEV overlay

```bash
kubectl apply -k deploy/app/overlays/dev
```

### 6. Wait for workloads

```bash
kubectl -n campus-dev rollout status deployment/frontend
kubectl -n campus-dev rollout status deployment/auth
kubectl -n campus-dev rollout status deployment/backend
kubectl -n campus-dev rollout status deployment/campus-nginx
```

### 7. Inspect resources

```bash
kubectl -n campus-dev get all
kubectl -n campus-dev get ingress
kubectl -n campus-dev get jobs
```

Expected high-level result:

- `frontend`, `auth`, `backend`, and `campus-nginx` deployments are ready
- `campus-importer` completes successfully
- the `campus` ingress exists in namespace `campus-dev`

### 8. Verify importer result

```bash
kubectl -n campus-dev logs job/campus-importer
```

The importer is designed to:

- wait for the database
- wait for the schema
- skip cleanly if data already exists
- be garbage-collected after completion by Kubernetes Job TTL

Important timing note:

- `campus-importer` uses `ttlSecondsAfterFinished: 600`
- after about 10 minutes, the completed Job may no longer exist
- if that happens, `kubectl logs job/campus-importer` will no longer work
- inspect importer logs shortly after apply if you need to confirm the exact run

### 9. Verify access

Check both:

- ingress host from the DEV overlay
- `GW` forwarding path to `S5:30080`

From `S5` itself, the most reliable ingress smoke probe is:

```bash
curl -I -H 'Host: campus-dev.192-168-50-5.sslip.io' http://127.0.0.1:30080/
```

At the time of writing, the DEV ingress host in repo is:

- `campus-dev.192-168-50-5.sslip.io`

## Re-running The Importer

The importer is a Kubernetes `Job`, not a long-running deployment.

That means:

- it will not rerun automatically on every `kubectl apply`
- reruns should be intentional

If a rerun is required:

```bash
kubectl -n campus-dev delete job campus-importer --ignore-not-found
kubectl apply -k deploy/app/overlays/dev
```

If the Job has already been removed by TTL cleanup, the delete step is harmless
and the apply step recreates it.

## PROD Runbook Status

The repository already contains:

- `deploy/app/overlays/prod/`
- `deploy/infra/ingress-nginx/values-prod.yaml`

But PROD should still be treated as a target path, not a completed rollout.

Current caveats:

- the PROD delivery process is not automated yet
- final `GW` production hostnames are not yet fully documented
- the PROD overlay now points to GHCR, but release tags must be pinned
  intentionally before apply

Use the PROD overlay only after those items are closed.

## Selecting A PROD Release Tag

The PROD overlay is designed to use immutable GHCR tags, not moving tags.

Current behavior:

- image names already point to `ghcr.io/nexoc/...`
- the tag placeholder is `sha-change-me`
- before a PROD rollout, replace that placeholder with the chosen release tag
  such as `sha-676e768`

This keeps PROD aligned with the tags already produced by CI.

Helper examples:

```bash
bash deploy/scripts/apply-overlay.sh --environment prod --image-tag sha-676e768 --render-only
bash deploy/scripts/verify-overlay.sh --environment prod --smoke-url http://campus.example.com
```

## Operational Checks

Useful commands during rollout:

```bash
kubectl -n campus-dev describe ingress campus
kubectl -n campus-dev describe job campus-importer
kubectl -n campus-dev get pods -o wide
kubectl -n campus-dev logs deployment/auth
kubectl -n campus-dev logs deployment/backend
kubectl -n campus-dev logs deployment/campus-nginx
```

## Delivery Notes

Current repo behavior:

- CI builds and pushes images to GHCR
- CI produces immutable `sha-<shortsha>` tags
- CI also publishes moving `dev-latest` tags as a convenience pointer
- repo now also contains a DEV deploy workflow for a self-hosted Linux runner on `S5`
- on successful `push` builds for `main`, that workflow deploys the matching immutable tag to DEV automatically

Operational implication:

- `main` now acts as the DEV auto-deploy branch
- DEV and PROD deploys should use pinned immutable tags
- `dev-latest` should not be treated as deployment truth

## Related Docs

- `deploy/docs/environments.md`
- `deploy/docs/naming-convention.md`
- `deploy/docs/rollout-notes.md`
- `deploy/infra/ingress-nginx/README.md`
