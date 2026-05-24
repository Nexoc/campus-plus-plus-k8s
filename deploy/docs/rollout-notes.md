# Rollout Notes

This document captures the active home-only tag-driven rollout model for
Campus++.

## Current Status Summary

Current delivery path:

```text
Git tag -> GitHub Actions -> GHCR -> home-gw-runner -> k3s -> Envoy Gateway -> campus-nginx -> services
```

Current active home dev path:

```text
local pc -> gw -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> app
```

Current home production path:

```text
home-v* -> home-production approval -> home-gw-runner -> prod k3s HA -> campus-prod
```

Key characteristics:

- `main` runs validation only
- `home-dev-*` tags build and release to home dev on `s5-dev`
- `home-v*` tags build and release to home prod after `home-production` approval
- active manifests live under `deploy/app/overlays/`
- Envoy Gateway is the active entry layer on NodePort `30080`
- monitoring and host-level operations are handled through `ops/`

## Relevant Repo Files

Active files:

- `deploy/app/base/`
- `deploy/app/overlays/home/`
- `deploy/app/overlays/prod/`
- `deploy/infra/envoy-gateway/`
- `deploy/infra/gw-nginx/`
- `deploy/scripts/apply-overlay.sh`
- `deploy/scripts/verify-overlay.sh`
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-home-dev.yml`
- `.github/workflows/deploy-home-prod.yml`
- `deploy/docs/production-cd-design.md`

## Home Dev Release Workflow

1. Push to `main` or open a PR against `main`.
2. `CI Pipeline` runs validation only.
3. Push a `home-dev-*` tag.
4. The home dev workflow builds and publishes GHCR images tagged exactly with `github.ref_name`.
5. The deploy job runs on `home+gw+deploy` self-hosted runner labels.
6. The workflow creates or updates `ghcr-pull`, applies the shared `GatewayClass`, renders the `home` overlay, and applies it to `campus-dev`.
7. The workflow verifies rollouts, importer completion, Gateway API resources,
   Envoy NodePort `30080`, and an HTTP smoke check with `Host: home-campus-dev.davl.at`.

## Home Production Release Workflow

1. Push a `home-v*` tag.
2. The home prod workflow builds and publishes GHCR images tagged exactly with `github.ref_name`.
3. The deploy job waits on GitHub environment `home-production`.
4. After manual approval, the deploy job runs on `home+gw+deploy` self-hosted runner labels.
5. The workflow creates or updates `ghcr-pull`, applies the shared `GatewayClass`, renders the `prod` overlay, and applies it to `campus-prod`.
6. Prod render/apply generates the `s4-db` Service and EndpointSlice from `/home/nexoc/campus-secrets/prod/db-endpoint.env`.
7. The workflow verifies rollouts, importer completion, Gateway API resources,
   Envoy NodePort `30080`, and HTTP smoke checks with `Host: home-campus-prod.davl.at`.

## Suggested Manual Commands

Render a home dev release manifest:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash deploy/scripts/apply-overlay.sh \
  --environment home \
  --image-tag home-dev-example \
  --render-only
```

Apply the home dev overlay:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
kubectl delete job campus-importer -n campus-dev --ignore-not-found
bash deploy/scripts/apply-overlay.sh \
  --environment home \
  --image-tag home-dev-example
```

Verify home dev:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash deploy/scripts/verify-overlay.sh \
  --environment home \
  --expected-nodeport 30080

curl -I -H "Host: home-campus-dev.davl.at" http://s5-dev:30080/
```

Render a home prod release manifest from `gw` without applying it:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
CAMPUS_HTTPROUTE_HOSTNAME=home-campus-prod.davl.at \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag home-v-render-test \
  --render-only \
  --manifest-out /tmp/campus-prod-rendered.yaml
```

The prod render must include `service/s4-db` and `endpointslice/s4-db`.

## Verification Checklist

A successful verification pass should confirm:

- `frontend`, `auth`, `backend`, and `campus-nginx` roll out successfully
- `campus-importer` completes or has already been garbage-collected after success
- `gateway/campus` is `Programmed=True`
- `httproute/campus` is accepted and has `ResolvedRefs=True`
- `envoyproxy/campus-edge` exists
- `clienttrafficpolicy/campus-edge` exists
- Envoy publishes NodePort `30080`

## Known Open Gaps

- home prod edge hardening and TLS should be verified
- RBAC-limited deployer kubeconfigs are still future work
- Alertmanager and logs are monitoring follow-up work
