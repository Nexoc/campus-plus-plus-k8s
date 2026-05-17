# Deployment Layer

This directory contains the Kubernetes-side delivery artifacts for Campus++.

Application code remains in:

- `frontend/`
- `auth/`
- `backend/`
- `importer/`
- `nginx/`

Deployment code lives in:

- `deploy/app/overlays/` for active application manifests
- `deploy/infra/` for shared infrastructure baselines
- `deploy/templates/` for example config and secret inputs
- `deploy/docs/` for operational notes

## Current Architecture

The current working DEV request path is:

`Internet -> gw -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> frontend/auth/backend -> PostgreSQL s4-db`

The current controlled PROD release path is:

`uni-v* tag -> production approval -> gw-campus-prod -> prod k3s HA -> campus-prod`

Key points:

- Kubernetes distro is `k3s`
- `campus-dev` and `campus-prod` are active application namespaces
- app manifests are managed with Kustomize from `deploy/app/overlays/`
- Envoy Gateway is the active Kubernetes entry layer
- `campus-nginx` remains the internal app gateway and auth boundary
- PostgreSQL stays outside Kubernetes
- `main` runs validation only, while environment releases are tag-driven
- host bootstrap, monitoring, and infrastructure checks live under `ops/`
- central monitoring runs on `s6-monitoring`, outside the app CD workflow
- concrete lab IP addresses are runtime inventory/config values, not
  architecture or workflow constants

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
- DEV secret env files are available on the runner host under `/home/nexoc/campus-secrets/dev`

## Config And Secrets

For the full file checklist, see `docs/runtime-inputs.md`.

Tracked DEV config files:

- `deploy/app/overlays/dev/config/auth-config.env`
- `deploy/app/overlays/dev/config/backend-config.env`
- `deploy/app/overlays/dev/config/importer-config.env`

Active HOME runtime inputs follow the same layout under:

- `deploy/app/overlays/home/`

Reference templates live in:

- `deploy/templates/config/`
- `deploy/templates/secrets/`

Staged secret files are generated at deploy time under:

```text
deploy/app/overlays/*/secrets/*.env
```

Rules:

- config files may be versioned
- secret templates may be versioned
- staged secret files are ignored by git
- real secrets must stay out of git

For the self-hosted runners, deploy workflows stage real secrets from:

- `/home/nexoc/campus-secrets/dev/db-secrets.env`
- `/home/nexoc/campus-secrets/dev/auth-secrets.env`
- `/home/nexoc/campus-secrets/home/db-secrets.env`
- `/home/nexoc/campus-secrets/home/auth-secrets.env`
- `/home/nexoc/campus-secrets/prod/db-secrets.env`
- `/home/nexoc/campus-secrets/prod/auth-secrets.env`
- `/home/nexoc/campus-secrets/prod/db-endpoint.env`

For PROD, `DB_HOST` remains `s4-db`. The deploy script renders `service/s4-db`
and `endpointslice/s4-db` from `db-endpoint.env`, so the real external database
address stays environment-specific and out of git.

Prepare the fixed DEV host path:

```bash
# server: s5-dev
cd /home/nexoc/campus-plus-plus-k8s
mkdir -p /home/nexoc/campus-secrets/dev
cp deploy/templates/secrets/db-secrets.env.example /home/nexoc/campus-secrets/dev/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example /home/nexoc/campus-secrets/dev/auth-secrets.env
chown -R nexoc:nexoc /home/nexoc/campus-secrets
chmod 700 /home/nexoc/campus-secrets /home/nexoc/campus-secrets/dev
chmod 600 /home/nexoc/campus-secrets/dev/*.env
```

Prepare the same layout on the future home runner before running the first
`home-dev-*` release. The required files are:

```text
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
```

Prepare the `prod` host path on `gw` before running a `uni-v*` release:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
mkdir -p /home/nexoc/campus-secrets/prod
cp deploy/templates/secrets/db-secrets.env.example /home/nexoc/campus-secrets/prod/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example /home/nexoc/campus-secrets/prod/auth-secrets.env
cp deploy/templates/secrets/db-endpoint.env.example /home/nexoc/campus-secrets/prod/db-endpoint.env
chown -R nexoc:nexoc /home/nexoc/campus-secrets/prod
chmod 700 /home/nexoc/campus-secrets/prod
chmod 600 /home/nexoc/campus-secrets/prod/*.env
```

## Install Or Update Envoy Gateway

Install or upgrade the controller on DEV:

```bash
# server: s5-dev
cd /home/nexoc/campus-plus-plus-k8s
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.7.2 \
  --namespace envoy-gateway-system \
  --create-namespace \
  -f deploy/infra/envoy-gateway/values-dev.yaml

kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
```

For PROD, prefer the Ansible wrapper from `gw`:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/install-envoy-prod.yml
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
curl -I -H 'Host: campus-dev.10-123-127-29.sslip.io' http://s5-dev:30080/
```

## Manual Non-Prod Apply

Render the manifests:

```bash
# server: s5-dev
bash deploy/scripts/apply-overlay.sh \
  --environment dev \
  --image-tag uni-dev-example \
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
IMAGE_TAG=uni-dev-example
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
kubectl delete job campus-importer -n campus-dev --ignore-not-found
bash deploy/scripts/apply-overlay.sh --environment dev --image-tag "${IMAGE_TAG}"
```

Verify the stack:

```bash
# server: s5-dev
bash deploy/scripts/verify-overlay.sh --environment dev --expected-nodeport 30080
```

## GitHub-Assisted Releases

Current workflow behavior:

- `.github/workflows/ci.yml` runs on `main`
- CI runs validation only on `push` and `pull_request`
- `.github/workflows/deploy-dev.yml` is the non-prod release workflow
- `.github/workflows/deploy-prod.yml` is the production release workflow
- `uni-dev-*` tags build and push GHCR images, then deploy to the `dev+s5+uni` runner
- `home-dev-*` tags build and push GHCR images, then deploy to the `dev+s5+home` runner
- `uni-v*` tags build and push GHCR images, wait for the `production` environment
  approval, then deploy to the `prod+gw+uni` runner
- `home-v*` tags build and push GHCR images, wait for the `home-production`
  environment approval, then deploy to the `prod+gw+home` runner

Production hostnames are environment-specific:

- university DEV uses `campus-dev.10-123-127-29.sslip.io`
- university PROD uses `campus-prod.10-123-127-29.sslip.io`
- university Grafana uses `grafana.10-123-127-29.sslip.io`
- home DEV uses `home-campus-dev.davl.at`
- home PROD uses `home-campus-prod.davl.at`
- home Grafana uses `home-grafana.davl.at`
- release image tags are exactly the Git tag that triggered the workflow
- deploy jobs create `ghcr-pull`, apply `deploy/app` overlays, and verify the
  Envoy/Gateway API rollout
- when `GHCR_PULL_TOKEN` is configured as a repository or organization secret,
  the deploy workflow uses it for a durable cluster pull secret; otherwise it
  falls back to `GITHUB_TOKEN` with a warning

Current release namespaces:

- `uni-dev-*` for the university `s5` cluster
- `home-dev-*` for the home dev cluster
- `uni-v*` for the university PROD HA cluster, namespace `campus-prod`
- `home-v*` for the home PROD HA cluster, namespace `campus-prod`

PROD keeps `DB_HOST=s4-db`. During `prod` render/apply,
`deploy/scripts/apply-overlay.sh` renders the `service/s4-db` and
`endpointslice/s4-db` resources from `/home/nexoc/campus-secrets/prod/db-endpoint.env`.
Only that host-local file changes between the current lab and a future
university environment.

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

Check the PROD render contract from `gw` without applying it:

```bash
# server: gw
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag v-render-test \
  --render-only \
  --manifest-out /tmp/campus-prod-rendered.yaml

grep -nE "namespace: campus-prod|campus-prod.10-123-127-29.sslip.io|nodePort: 30080|imagePullSecrets|ghcr-pull|kind: Service|kind: EndpointSlice|name: s4-db" /tmp/campus-prod-rendered.yaml -A3 -B3
rm -f /tmp/campus-prod-rendered.yaml
```

## Known Gaps

Current open issues:

- the current `gw` nginx baseline is HTTP-only lab configuration
- PROD edge hardening and an RBAC-limited deployer kubeconfig are still future work
- the `home` hostname is still a placeholder until the home edge is finalized
- Alertmanager and logs are monitoring follow-up work

## Related Docs

- `deploy/docs/environments.md`
- `deploy/docs/naming-convention.md`
- `deploy/docs/rollout-notes.md`
- `deploy/docs/production-cd-design.md`
- `deploy/docs/structure.md`
- `deploy/infra/envoy-gateway/README.md`
- `deploy/infra/gw-nginx/README.md`
- `ops/README.md`
- `ops/docs/monitoring-design.md`
