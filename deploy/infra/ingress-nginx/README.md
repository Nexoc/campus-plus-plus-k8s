# ingress-nginx

This directory is reserved for the infrastructure-side configuration of the
`ingress-nginx` controller used by Campus++.

App routing remains in the application layer:

- cluster ingress is handled by `ingress-nginx`
- application routing and `auth_request` remain inside `campus-nginx`

This separation is intentional and should be preserved.

## Role In The Architecture

Current target traffic path:

`Client -> GW -> ingress-nginx -> campus-nginx -> services`

Responsibilities of each layer:

- `GW`: edge reverse proxy and external entry point
- `ingress-nginx`: Kubernetes ingress controller
- `campus-nginx`: internal application gateway with app-specific routing and auth checks

## Current Confirmed DEV State

The current DEV cluster behavior is:

- k3s on `S5`
- bundled Traefik disabled
- `ingress-nginx` installed separately
- `IngressClass` is `nginx`
- NodePort exposure uses `30080` for HTTP and `30443` for HTTPS
- `GW` forwards DEV traffic to `S5:30080`

Application Ingress resources in the repo assume this controller class:

- `deploy/app/base/ingress.yaml`
- `deploy/app/overlays/dev/ingress-patch.yaml`
- `deploy/app/overlays/prod/ingress-patch.yaml`

## Repo Expectations

This folder should contain:

- a short operational README
- environment-specific Helm values files
- any small notes needed to reproduce the controller installation

Current files:

- `values-dev.yaml`
- `values-prod.yaml`

These files now contain a versioned baseline that reflects the currently known
installation model. If the controller was already installed manually, compare
the live release against these values and reconcile any drift.

## Recommended Helm Workflow

Example install or upgrade pattern:

```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  -f deploy/infra/ingress-nginx/values-dev.yaml
```

Equivalent production installs should use `values-prod.yaml`.

## What Must Be Captured Here

When the real values are exported or cleaned up, this directory should capture:

- service type and ports
- NodePort choices if they are pinned
- ingress class settings
- controller replica count
- any admission webhook or service settings that differ by environment

## Known Gap Today

The repo now contains baseline values for DEV and PROD, but the live Helm
release should still be checked against them before this layer is treated as
fully reproducible.
