# Deployment Layer

This directory contains the Kubernetes-side delivery artifacts for Campus++.

Application code remains in:

- `frontend/`
- `auth/`
- `backend/`
- `importer/`
- `nginx/`

Deployment code lives in:

- `deploy/dev/` for the active DEV manifests
- `deploy/infra/` for shared infrastructure baselines
- `deploy/templates/` for example config and secret inputs
- `deploy/docs/` for operational notes

## Current Active Architecture

The current working request path is:

`Internet -> davl.at -> private/VPN path -> DEV 192.168.56.40:31080 -> Envoy Gateway -> campus-nginx -> frontend/auth/backend -> PostgreSQL 192.168.56.20`

Key points:

- Kubernetes distro is `k3s`
- `campus-dev` is the active namespace
- app manifests are managed with Kustomize from `deploy/dev/`
- Envoy Gateway is the active DEV entry layer
- `campus-nginx` remains the internal app gateway and auth boundary
- PostgreSQL stays outside Kubernetes

The older `deploy/app/` overlay tree and `ingress-nginx` baselines are kept in
the repo as legacy/reference material and are not the current DEV rollout path.

## Structure

```text
deploy/
├── dev/
│   ├── config/
│   └── secrets/
├── app/
│   └── ...
├── scripts/
├── templates/
│   ├── config/
│   └── secrets/
├── infra/
│   ├── envoy-gateway/
│   └── ingress-nginx/
└── docs/
```

## Prerequisites

Before applying the active DEV manifests, make sure:

- your `kubectl` context points to the DEV k3s cluster
- Envoy Gateway is installed in `envoy-gateway-system`
- the cluster can pull images from GHCR
- the `local-path` StorageClass is available for the `course-materials` PVC
- the cluster can reach PostgreSQL at `192.168.56.20:5432`
- DEV secret env files are available either locally or on the runner host

## Config And Secrets

Active DEV runtime inputs:

- `deploy/dev/config/auth-config.env`
- `deploy/dev/config/backend-config.env`
- `deploy/dev/config/importer-config.env`
- `deploy/dev/secrets/db-secrets.env`
- `deploy/dev/secrets/auth-secrets.env`

Reference templates live in:

- `deploy/templates/config/`
- `deploy/templates/secrets/`

Rules:

- config files may be versioned
- secret templates may be versioned
- real secrets must stay out of git

For the self-hosted runner, the deploy workflow stages secrets from:

- `/home/nexoc/campus-secrets/dev/db-secrets.env`
- `/home/nexoc/campus-secrets/dev/auth-secrets.env`

Prepare the fixed host path:

```bash
mkdir -p /home/nexoc/campus-secrets/dev
cp deploy/templates/secrets/db-secrets.env.example /home/nexoc/campus-secrets/dev/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example /home/nexoc/campus-secrets/dev/auth-secrets.env
chown -R nexoc:nexoc /home/nexoc/campus-secrets
chmod 700 /home/nexoc/campus-secrets /home/nexoc/campus-secrets/dev
chmod 600 /home/nexoc/campus-secrets/dev/*.env
```

## Install Or Update Envoy Gateway

Install or upgrade the controller:

```bash
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.7.0 \
  --namespace envoy-gateway-system \
  --create-namespace \
  -f deploy/infra/envoy-gateway/values-dev.yaml

kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
```

## Manual DEV Apply

Render the manifests:

```bash
kubectl kustomize deploy/dev
```

Create or update the GHCR pull secret:

```bash
kubectl apply -f deploy/dev/namespace.yaml

kubectl create secret docker-registry ghcr-pull \
  --namespace campus-dev \
  --docker-server ghcr.io \
  --docker-username YOUR_GHCR_PULL_USERNAME \
  --docker-password YOUR_GHCR_PULL_TOKEN \
  --dry-run=client -o yaml | kubectl apply -f -
```

The recommended credential here is a long-lived read-only GHCR token dedicated
to image pulls. Avoid using a workflow-scoped `GITHUB_TOKEN` for cluster pull
secrets because it expires after the job finishes.

Apply the shared GatewayClass and the DEV stack:

```bash
IMAGE_TAG=sha-<shortsha>
sed -i "s/newTag: dev-latest/newTag: ${IMAGE_TAG}/g" deploy/dev/kustomization.yaml
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
kubectl delete job campus-importer -n campus-dev --ignore-not-found
kubectl apply -k deploy/dev
```

Wait for the stack:

```bash
kubectl rollout status deployment/frontend -n campus-dev --timeout=300s
kubectl rollout status deployment/auth -n campus-dev --timeout=300s
kubectl rollout status deployment/backend -n campus-dev --timeout=300s
kubectl rollout status deployment/campus-nginx -n campus-dev --timeout=300s
kubectl wait --for=condition=complete job/campus-importer -n campus-dev --timeout=600s
```

## GitHub-Assisted DEV Deploy

Current workflow behavior:

- `.github/workflows/ci.yml` runs on `main`
- CI runs auth and backend tests before image publish
- CI builds and pushes images to GHCR
- CI publishes both `sha-<shortsha>` and `dev-latest`
- `.github/workflows/deploy-dev.yml` runs on the self-hosted DEV runner after a
  successful CI run
- the deploy workflow pins `deploy/dev` to the exact `sha-<shortsha>` of the
  successful CI run, creates `ghcr-pull`, applies `deploy/dev`, and waits for
  importer completion
- when `GHCR_PULL_TOKEN` is configured as a repository or organization secret,
  the deploy workflow uses it for a durable cluster pull secret; otherwise it
  falls back to `GITHUB_TOKEN` with a warning

This means the active DEV branch is:

- `main`

## Verification

Inspect the active stack:

```bash
kubectl get all -n campus-dev -o wide
kubectl get gateway,httproute,envoyproxy -n campus-dev -o wide
kubectl logs job/campus-importer -n campus-dev
```

Check the internal Envoy entry:

```bash
curl -I http://192.168.56.40:31080/
curl http://192.168.56.40:31080/api/public/study-programs | head -c 500
curl http://192.168.56.40:31080/api/public/courses | head -c 500
```

Check the external public entry:

```bash
curl -I https://campus.davl.at/
```

Expected result:

- `frontend`, `auth`, `backend`, and `campus-nginx` are `Ready`
- `campus-importer` is `Complete`
- `gateway/campus-dev` is `Programmed=True`
- `httproute/campus` is accepted
- public API paths respond through Envoy

## Known Gaps

Current open issues:

- the single-node DEV cluster still shows intermittent instability
- Envoy-related components have had probe failures and restarts during host/API
  hiccups
- the repo does not yet contain the exact public VPS nginx configuration
- PROD delivery is still not the active rollout path

## Related Docs

- `deploy/docs/environments.md`
- `deploy/docs/naming-convention.md`
- `deploy/docs/rollout-notes.md`
- `deploy/docs/structure.md`
- `deploy/infra/envoy-gateway/README.md`
- `deploy/infra/ingress-nginx/README.md`
