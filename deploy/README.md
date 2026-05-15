# Deployment Layer

This directory contains the Kubernetes-side delivery artifacts for Campus++.

Application code remains in:

- `frontend/`
- `auth/`
- `backend/`
- `importer/`
- `nginx/`

Deployment code lives in:

- `deploy/app/overlays/` for the active non-prod manifests
- `deploy/infra/` for shared infrastructure baselines
- `deploy/templates/` for example config and secret inputs
- `deploy/docs/` for operational notes

## Current Active Architecture

The current working lab request path is:

`Internet -> gw -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> frontend/auth/backend -> PostgreSQL s4-db`

Key points:

- Kubernetes distro is `k3s`
- `campus-dev` is the active namespace
- app manifests are managed with Kustomize from `deploy/app/overlays/`
- Envoy Gateway is the active DEV entry layer
- `campus-nginx` remains the internal app gateway and auth boundary
- PostgreSQL stays outside Kubernetes
- `main` runs validation only, while non-prod releases are tag-driven

## Structure

```text
deploy/
├── app/
│   └── overlays/
├── scripts/
├── templates/
│   ├── config/
│   └── secrets/
├── infra/
│   ├── envoy-gateway/
│   └── gw-nginx/
└── docs/
```

## Prerequisites

Before applying the active DEV manifests, make sure:

- your `kubectl` context points to the DEV k3s cluster
- Envoy Gateway is installed in `envoy-gateway-system`
- the cluster can pull images from GHCR
- the `local-path` StorageClass is available for the `course-materials` PVC
- the cluster can reach PostgreSQL at `s4-db:5432`
- the hostnames `gw`, `s5-dev`, and `s4-db` resolve through DNS or `/etc/hosts`
  on the servers that use them
- DEV secret env files are available either locally or on the runner host

## Config And Secrets

Active DEV runtime inputs:

- `deploy/app/overlays/dev/config/auth-config.env`
- `deploy/app/overlays/dev/config/backend-config.env`
- `deploy/app/overlays/dev/config/importer-config.env`
- `deploy/app/overlays/dev/secrets/db-secrets.env`
- `deploy/app/overlays/dev/secrets/auth-secrets.env`

Active HOME runtime inputs follow the same layout under:

- `deploy/app/overlays/home/`

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
- `/home/nexoc/campus-secrets/home/db-secrets.env`
- `/home/nexoc/campus-secrets/home/auth-secrets.env`

Prepare the fixed host path:

```bash
mkdir -p /home/nexoc/campus-secrets/dev
cp deploy/templates/secrets/db-secrets.env.example /home/nexoc/campus-secrets/dev/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example /home/nexoc/campus-secrets/dev/auth-secrets.env
chown -R nexoc:nexoc /home/nexoc/campus-secrets
chmod 700 /home/nexoc/campus-secrets /home/nexoc/campus-secrets/dev
chmod 600 /home/nexoc/campus-secrets/dev/*.env
```

Prepare the same layout for `home` before running the first `home-*` release:

```bash
mkdir -p /home/nexoc/campus-secrets/home
cp deploy/templates/secrets/db-secrets.env.example /home/nexoc/campus-secrets/home/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example /home/nexoc/campus-secrets/home/auth-secrets.env
chown -R nexoc:nexoc /home/nexoc/campus-secrets/home
chmod 700 /home/nexoc/campus-secrets/home
chmod 600 /home/nexoc/campus-secrets/home/*.env
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

## Configure Lab GW Reverse Proxy

The `gw` host forwards external lab HTTP traffic to Envoy on `s5-dev`.

Reference config:

- `deploy/infra/gw-nginx/campus-dev.conf`

Install it on `gw` as:

```text
/etc/nginx/sites-enabled/campus-dev
```

Then validate and reload nginx on `gw`:

```bash
# server: gw
sudo nginx -t
sudo systemctl reload nginx
```

Verify the proxy path:

```bash
# server: gw
curl -I http://gw/
curl -I -H 'Host: campus-dev.s5-dev.local' http://s5-dev:30080/
```

## Manual Non-Prod Apply

Render the manifests:

```bash
# server: s5-dev
bash deploy/scripts/apply-overlay.sh \
  --environment dev \
  --image-tag dev-2026.04.24-1 \
  --render-only
```

Create or update the GHCR pull secret:

```bash
# server: s5-dev
kubectl create namespace campus-dev --dry-run=client -o yaml | kubectl apply -f -

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
# server: s5-dev
IMAGE_TAG=dev-2026.04.24-1
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
kubectl delete job campus-importer -n campus-dev --ignore-not-found
bash deploy/scripts/apply-overlay.sh --environment dev --image-tag "${IMAGE_TAG}"
```

Verify the stack:

```bash
# server: s5-dev
bash deploy/scripts/verify-overlay.sh --environment dev --expected-nodeport 30080
```

## GitHub-Assisted Non-Prod Release

Current workflow behavior:

- `.github/workflows/ci.yml` runs on `main`
- CI runs validation only on `push` and `pull_request`
- `.github/workflows/deploy-dev.yml` is the non-prod release workflow
- `dev-*` tags build and push GHCR images, then deploy to the `dev+s5` runner
- `home-*` tags build and push GHCR images, then deploy to the `dev+home` runner
- release image tags are exactly the Git tag that triggered the workflow
- deploy jobs create `ghcr-pull`, apply `deploy/app` overlays, and verify the
  Envoy/Gateway API rollout
- when `GHCR_PULL_TOKEN` is configured as a repository or organization secret,
  the deploy workflow uses it for a durable cluster pull secret; otherwise it
  falls back to `GITHUB_TOKEN` with a warning

Current release namespaces:

- `dev-*` for the lab `s5` cluster
- `home-*` for the home cluster
- `v*` reserved for future PROD work

## Verification

Inspect the active stack:

```bash
# server: s5-dev
kubectl get all -n campus-dev -o wide
kubectl get gateway,httproute,envoyproxy -n campus-dev -o wide
kubectl logs job/campus-importer -n campus-dev
```

Check the Envoy/Gateway API entry:

```bash
# server: s5-dev
kubectl get gateway,httproute,envoyproxy,clienttrafficpolicy -n campus-dev -o wide
bash deploy/scripts/verify-overlay.sh --environment dev --expected-nodeport 30080
```

Check the lab edge entry through `gw`:

```bash
# server: gw
curl -I http://gw/
```

Expected result:

- `frontend`, `auth`, `backend`, and `campus-nginx` are `Ready`
- `campus-importer` is `Complete`
- `gateway/campus` is `Programmed=True`
- `httproute/campus` is accepted
- lab edge requests respond through Envoy

## Known Gaps

Current open issues:

- the single-node DEV cluster still shows intermittent instability
- Envoy-related components have had probe failures and restarts during host/API
  hiccups
- the current `gw` nginx baseline is HTTP-only lab configuration
- PROD delivery is still not the active rollout path

## Related Docs

- `deploy/docs/environments.md`
- `deploy/docs/naming-convention.md`
- `deploy/docs/rollout-notes.md`
- `deploy/docs/structure.md`
- `deploy/infra/envoy-gateway/README.md`
- `deploy/infra/gw-nginx/README.md`
