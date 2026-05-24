# Deployment Layer

This directory contains the Kubernetes-side delivery artifacts for Campus++.

Application code remains in:

- `frontend/`
- `auth/`
- `backend/`
- `importer/`
- `nginx/`

Deployment code lives in:

- `deploy/app/overlays/` for application manifests
- `deploy/infra/` for shared infrastructure baselines
- `deploy/templates/` for example config and secret inputs
- `deploy/docs/` for operational notes

## Current Architecture

Active target:

```text
home lab on one physical PC with VM clones
```

Home dev request path:

```text
internet -> DNS -> VPS nginx HTTPS -> WireGuard -> home VM network -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> frontend/auth/backend -> PostgreSQL s4-db
```

Home production release path:

```text
home-v* tag -> home-production approval -> home gw control runner -> prod k3s HA -> campus-prod
```

Key points:

- Kubernetes distro is `k3s`
- `campus-dev` and `campus-prod` are active application namespaces
- app manifests are managed with Kustomize from `deploy/app/overlays/`
- Envoy Gateway is the active Kubernetes entry layer
- `campus-nginx` remains the internal app gateway and auth boundary
- PostgreSQL stays outside Kubernetes on `s4-db`
- `main` runs validation only; releases are tag-driven
- host bootstrap, monitoring, and infrastructure checks live under `ops/`
- central monitoring runs on `s6-monitoring`

## Prerequisites

Before applying home dev manifests:

- `kubectl` points to the dev k3s cluster or `KUBECONFIG=/home/nexoc/.kube/dev.yaml`
- Envoy Gateway is installed in `envoy-gateway-system`
- the cluster can pull images from GHCR
- `local-path` StorageClass is available for `course-materials`
- `db-endpoint.env` points at the real `s4-db` endpoint
- the cluster can reach the endpoint address and port from `db-endpoint.env`
- `gw` and `s5-dev` resolve through DNS or `/etc/hosts`
- home dev secrets exist under `/home/nexoc/campus-secrets/home`

Before applying home prod manifests:

- `KUBECONFIG=/home/nexoc/.kube/prod.yaml`
- prod secrets exist under `/home/nexoc/campus-secrets/prod`
- `db-endpoint.env` points at the real `s4-db` endpoint
- the prod cluster can pull GHCR images
- Envoy Gateway is installed or can be installed by the ops wrapper

## Config And Secrets

For the full checklist, see `docs/runtime-inputs.md`.

Home dev runtime inputs:

```text
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
/home/nexoc/campus-secrets/home/db-endpoint.env
```

Home prod runtime inputs:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

For home dev and home prod, `DB_HOST` remains `s4-db`. The deploy script
renders `service/s4-db` and `endpointslice/s4-db` from `db-endpoint.env`, so the
real database address stays out of git.

## Install Or Update Envoy Gateway

For home dev:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.7.2 \
  --namespace envoy-gateway-system \
  --create-namespace \
  -f deploy/infra/envoy-gateway/values-dev.yaml

kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
```

For home prod, prefer the Ansible wrapper from `gw`:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/install-envoy-prod.yml
```

## Configure Home GW Reverse Proxy

The `gw` host forwards home HTTP traffic to Envoy on `s5-dev`.

Reference config:

- `deploy/infra/gw-nginx/campus-dev.conf`

Verify the proxy path:

```bash
# server: gw
curl -I http://gw/
curl -I -H 'Host: home-campus-dev.davl.at' http://s5-dev:30080/
```

## Manual Home Dev Apply

Render:

```bash
# server: gw
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
bash deploy/scripts/apply-overlay.sh \
  --environment home \
  --image-tag home-dev-example \
  --render-only
```

Apply:

```bash
# server: gw
IMAGE_TAG=home-dev-example
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
kubectl delete job campus-importer -n campus-dev --ignore-not-found
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
bash deploy/scripts/apply-overlay.sh --environment home --image-tag "${IMAGE_TAG}"
```

Verify:

```bash
# server: gw
bash deploy/scripts/verify-overlay.sh --environment home --expected-nodeport 30080
curl -I -H 'Host: home-campus-dev.davl.at' http://s5-dev:30080/
```

## GitHub-Assisted Releases

Current target workflow behavior:

- `.github/workflows/ci.yml` runs validation only on `main`
- `.github/workflows/deploy-home-dev.yml` deploys `home-dev-*`
- `.github/workflows/deploy-home-prod.yml` deploys `home-v*`
- `home-dev-*` tags deploy from the `home+gw+deploy` runner to `campus-dev`
- `home-v*` tags wait for `home-production` approval, then deploy from the `home+gw+deploy` runner to `campus-prod`

Hostnames:

- home dev: `home-campus-dev.davl.at`
- home prod: `home-campus-prod.davl.at`
- home Grafana: `home-grafana.davl.at`

External HTTPS status:

- `http://home-campus-dev.davl.at` returns `301`
- `https://home-campus-dev.davl.at` returns `200`
- `http://home-campus-prod.davl.at` returns `301`
- `https://home-campus-prod.davl.at` returns `200`

Protected Grafana access:

- `https://home-grafana.davl.at` reaches Grafana through VPS nginx HTTPS
- requests without nginx basic auth return `401`
- requests with nginx basic auth reach Grafana login with `302 /login`
- Grafana viewer user can see the provisioned dashboards
- Prometheus, exporters, and PostgreSQL are not public

Release image tags are exactly the Git tag that triggered the workflow.

## Verification

Inspect home dev:

```bash
# server: gw
kubectl get all -n campus-dev -o wide
kubectl get gateway,httproute,envoyproxy -n campus-dev -o wide
kubectl logs job/campus-importer -n campus-dev
bash deploy/scripts/verify-overlay.sh --environment home --expected-nodeport 30080
```

Check home prod render contract from `gw` without applying it:

```bash
# server: gw
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
CAMPUS_HTTPROUTE_HOSTNAME=home-campus-prod.davl.at \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag home-v-render-test \
  --render-only \
  --manifest-out /tmp/campus-prod-rendered.yaml

grep -nE "namespace: campus-prod|home-campus-prod.davl.at|nodePort: 30080|imagePullSecrets|ghcr-pull|kind: Service|kind: EndpointSlice|name: s4-db" /tmp/campus-prod-rendered.yaml -A3 -B3
```

## Known Gaps

- final security hardening pass is still planned
- possible later: rate limits, basic auth for dev, default deny server, fail2ban, firewall review
- RBAC-limited deployer kubeconfigs are future work
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
