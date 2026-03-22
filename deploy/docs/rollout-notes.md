# Rollout Notes

This document captures the current rollout behavior for Campus++ and the checks
used to verify the working DEV slice.

It is not a full CD guide yet. It documents the current manual-or-assisted
deployment path so it can be repeated consistently.

## Current Status Summary

Current confirmed working path:

`GitHub -> GHCR -> S5 k3s -> ingress-nginx -> campus-nginx -> app services -> PostgreSQL on S4`

Current confirmed DEV characteristics:

- GitHub Actions builds and pushes images to GHCR
- the DEV overlay rewrites image references to GHCR
- the DEV overlay is intended to deploy from pinned immutable image tags
- application manifests are applied with Kustomize
- auth, backend, frontend, campus-nginx, and importer are present in the app model
- the application uses PostgreSQL on `S4`, outside Kubernetes
- `GW` provides the practical external access path to DEV

## Relevant Repo Files

Primary app deployment files:

- `deploy/app/base/`
- `deploy/app/overlays/dev/`
- `deploy/app/overlays/prod/`

Primary delivery file:

- `.github/workflows/ci.yml`

Current DEV ingress host in repo:

- `campus-dev.192-168-50-5.sslip.io`

## DEV Rollout Workflow

The current repo supports this deployment flow:

1. Build and push images through GitHub Actions.
2. Select the immutable release tag to deploy.
3. Prepare environment-specific config and secret env files for the DEV overlay.
4. Apply the DEV overlay to the `S5` cluster, either manually or through the
   helper script under `deploy/scripts/`.
5. Wait for deployments to become ready.
6. Verify that the importer job completed successfully.
7. Verify access through ingress and through the `GW` forwarding path.

## Suggested Manual DEV Commands

Render manifests:

```bash
kubectl kustomize deploy/app/overlays/dev
```

Apply manifests:

```bash
kubectl apply -k deploy/app/overlays/dev
```

Inspect resources:

```bash
kubectl -n campus-dev get all
kubectl -n campus-dev get ingress
kubectl -n campus-dev get jobs
```

Check rollout status:

```bash
kubectl -n campus-dev rollout status deployment/frontend
kubectl -n campus-dev rollout status deployment/auth
kubectl -n campus-dev rollout status deployment/backend
kubectl -n campus-dev rollout status deployment/campus-nginx
```

Check importer logs:

```bash
kubectl -n campus-dev logs job/campus-importer
```

Note:

- this check is time-sensitive
- the importer Job is cleaned up automatically after completion
- if the Job is already gone, that does not automatically mean the rollout failed
- verify deployments, ingress, and smoke access first

Scripted alternatives:

```bash
bash deploy/scripts/apply-overlay.sh --environment dev --image-tag sha-676e768
bash deploy/scripts/verify-overlay.sh --environment dev --smoke-url http://campus-dev.192-168-50-5.sslip.io
```

Before apply, replace `sha-change-me` in:

```text
deploy/app/overlays/dev/kustomization.yaml
```

with the selected immutable tag such as `sha-676e768`.

## Importer Behavior Notes

Important operational detail:

- the importer code is safe to skip if the database is already populated
- the Kubernetes Job object will not rerun automatically on every `apply`
- the Kubernetes Job currently uses `ttlSecondsAfterFinished: 600`
- after about 10 minutes, a completed importer Job may be removed automatically
- if a fresh importer run is required, recreate the Job intentionally

Example reset flow:

```bash
kubectl -n campus-dev delete job campus-importer --ignore-not-found
kubectl apply -k deploy/app/overlays/dev
```

Use this only when a rerun is actually desired.

## DEV Verification Checklist

A DEV verification pass should confirm:

- `frontend` pod is `Ready`
- `auth` pod is `Ready`
- `backend` pod is `Ready`
- `campus-nginx` pod is `Ready`
- `campus-importer` job completed with exit code `0`
- `Ingress` exists in namespace `campus-dev`
- app is reachable via the DEV ingress host
- app is reachable through the `GW` reverse-proxy path
- auth-protected requests work through `campus-nginx`
- backend is using the external PostgreSQL instance on `S4`

## Known Open Gaps

The current working DEV rollout is real, but these items are still open:

- deploy is still effectively manual
- the repo does not yet store the actual `GW` config
- ingress-nginx Helm values are now versioned as a baseline, but still need
  reconciliation against the live Helm release
- choosing and applying the DEV release tag is still manual
- PROD rollout should still be considered incomplete

## Exit Criteria For "DEV Verification Closed"

DEV can be treated as formally closed when all of the following are true:

- the rollout steps are documented and repeatable
- config and secret handling is documented clearly
- importer rerun behavior is documented
- `GW` access path is documented
- the repo documents the current ingress-nginx setup
- delivery moves toward immutable image references for actual deployments
