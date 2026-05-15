# Rollout Notes

This document captures the current non-prod rollout model for Campus++.

## Current Status Summary

Current delivery path:

`Git tag -> GitHub Actions -> GHCR -> target runner -> k3s cluster -> Envoy Gateway -> campus-nginx -> services`

Current active lab path:

`Internet -> gw -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> app`

Key characteristics:

- `main` runs validation only
- `dev-*` tags build and release to the lab cluster
- `home-*` tags build and release to the home cluster
- `v*` is reserved for controlled PROD releases through the future `gw` runner
- active manifests live under `deploy/app/overlays/`
- Envoy Gateway is the active entry layer on NodePort `30080`

## Relevant Repo Files

Active files:

- `deploy/app/base/`
- `deploy/app/overlays/dev/`
- `deploy/app/overlays/home/`
- `deploy/infra/envoy-gateway/`
- `deploy/infra/gw-nginx/`
- `deploy/scripts/apply-overlay.sh`
- `deploy/scripts/verify-overlay.sh`
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-dev.yml`
- `deploy/docs/production-cd-design.md`

## Non-Prod Release Workflow

The current repo supports this flow:

1. Push to `main` or open a PR against `main`.
2. `CI Pipeline` runs validation only.
3. Push a `dev-*` or `home-*` tag.
4. `Non-Prod Release` builds and publishes GHCR images tagged exactly with `github.ref_name`.
5. The matching deploy job runs on the label-pinned self-hosted runner.
6. The workflow creates or updates `ghcr-pull`, applies the shared `GatewayClass`, renders the selected overlay, and applies it.
7. The workflow verifies rollouts, importer completion, Gateway API resources, and Envoy NodePort `30080`.

## Suggested Manual Commands

Render a release manifest:

```bash
# server: s5-dev
bash deploy/scripts/apply-overlay.sh \
  --environment dev \
  --image-tag dev-2026.04.24-1 \
  --render-only
```

Apply a non-prod overlay:

```bash
# server: s5-dev
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
kubectl delete job campus-importer -n campus-dev --ignore-not-found
bash deploy/scripts/apply-overlay.sh \
  --environment dev \
  --image-tag dev-2026.04.24-1
```

Verify a non-prod overlay:

```bash
# server: s5-dev
bash deploy/scripts/verify-overlay.sh \
  --environment dev \
  --expected-nodeport 30080
```

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

Current open issues:

- the lab `gw` nginx baseline is now in repo, but public hostname/TLS hardening is still future work
- the home hostname is still a placeholder in the overlay
- PROD delivery is intentionally not active yet
