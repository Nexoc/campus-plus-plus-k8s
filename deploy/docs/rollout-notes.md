# Rollout Notes

This document captures the current working DEV rollout.

It reflects the setup that was actually verified:

- GitHub Actions on `main`
- GHCR image publishing
- self-hosted deploy on the DEV node
- Envoy Gateway entry on `31080`
- external access through `davl.at`

## Current Status Summary

Current confirmed path:

`GitHub -> GHCR -> DEV k3s -> Envoy Gateway -> campus-nginx -> frontend/auth/backend -> PostgreSQL`

Current confirmed external path:

`Internet -> davl.at -> private/VPN path -> DEV 192.168.56.40:31080 -> Envoy Gateway -> app`

Confirmed DEV characteristics:

- images are published to GHCR on `push` to `main`
- active manifests live under `deploy/dev/`
- `frontend`, `auth`, `backend`, `campus-nginx`, and `campus-importer` run in `campus-dev`
- importer completed successfully and populated the database
- Gateway API objects `GatewayClass`, `Gateway`, `HTTPRoute`, and `EnvoyProxy` are active
- the app responds through `192.168.56.40:31080`
- the public hostname is fronted by the `davl.at` VPS

## Relevant Repo Files

Active DEV files:

- `deploy/dev/`
- `deploy/infra/envoy-gateway/`
- `.github/workflows/ci.yml`
- `.github/workflows/deploy-dev.yml`

Legacy/reference files:

- `deploy/app/`
- `deploy/infra/ingress-nginx/`

## DEV Rollout Workflow

The current repo supports this flow:

1. Push to `main`.
2. `CI Pipeline` runs auth tests and backend build.
3. CI builds and pushes images to GHCR.
4. CI publishes both `sha-<shortsha>` and `dev-latest`.
5. `Deploy DEV` runs on the self-hosted DEV runner.
6. The deploy workflow stages secrets, creates `ghcr-pull`, applies `deploy/dev`, and restarts app deployments for `dev-latest`.
7. The workflow waits for `frontend`, `auth`, `backend`, and `campus-nginx`.
8. The workflow waits for `campus-importer` completion.
9. Envoy serves the app through `31080`.

## Suggested Manual DEV Commands

Render manifests:

```bash
kubectl kustomize deploy/dev
```

Apply manifests:

```bash
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
kubectl delete job campus-importer -n campus-dev --ignore-not-found
kubectl apply -k deploy/dev
```

Force refresh when using `dev-latest`:

```bash
kubectl rollout restart deployment/frontend -n campus-dev
kubectl rollout restart deployment/auth -n campus-dev
kubectl rollout restart deployment/backend -n campus-dev
kubectl rollout restart deployment/campus-nginx -n campus-dev
```

Inspect resources:

```bash
kubectl -n campus-dev get all -o wide
kubectl -n campus-dev get gateway,httproute,envoyproxy -o wide
kubectl get gatewayclass
kubectl -n envoy-gateway-system get all -o wide
```

Check rollout status:

```bash
kubectl -n campus-dev rollout status deployment/frontend --timeout=300s
kubectl -n campus-dev rollout status deployment/auth --timeout=300s
kubectl -n campus-dev rollout status deployment/backend --timeout=300s
kubectl -n campus-dev rollout status deployment/campus-nginx --timeout=300s
kubectl -n campus-dev wait --for=condition=complete job/campus-importer --timeout=600s
```

Check importer logs:

```bash
kubectl -n campus-dev logs job/campus-importer
```

## Importer Notes

Important current behavior:

- the importer is a Kubernetes Job
- it waits for DB connectivity and schema readiness
- it skips cleanly when the database is already populated
- it uses `ttlSecondsAfterFinished: 600`

Rerun flow:

```bash
kubectl -n campus-dev delete job campus-importer --ignore-not-found
kubectl apply -k deploy/dev
```

## DEV Verification Checklist

A successful verification pass should confirm:

- `frontend` pod is `Ready`
- `auth` pod is `Ready`
- `backend` pod is `Ready`
- `campus-nginx` pod is `Ready`
- `campus-importer` is `Complete`
- `gateway/campus-dev` is `Programmed=True`
- `httproute/campus` is accepted
- `curl http://192.168.56.40:31080/` returns `200`
- public API requests work through Envoy
- `https://campus.davl.at/` works through the public VPS

## Known Open Gaps

Current open issues:

- the single-node DEV cluster is still unstable at times
- Envoy-related components have shown probe failures and restarts during node/API hiccups
- the repo still lacks the exact public VPS nginx config
- PROD rollout remains incomplete
