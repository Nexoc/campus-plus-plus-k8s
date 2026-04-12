# envoy-gateway

This directory contains the infrastructure-side configuration for the
`Envoy Gateway` controller used during the phase-1 migration away from
`ingress-nginx`.

App routing remains in the application layer:

- cluster ingress is handled by `Envoy Gateway`
- application routing and `auth_request` remain inside `campus-nginx`

This phase intentionally keeps the existing application security model intact.

## Role In The Architecture

Phase-1 target traffic paths:

- active path: `Client -> GW -> ingress-nginx -> campus-nginx -> services`
- staging path: `Client -> GW -> Envoy Gateway -> campus-nginx -> services`

Responsibilities of each layer:

- `GW`: edge reverse proxy and external entry point
- `Envoy Gateway`: Kubernetes Gateway API controller
- `campus-nginx`: internal application gateway with app-specific routing and auth checks

## Deployment Model Chosen Here

This repo uses the standard Envoy Gateway deployment mode for phase 1:

- Envoy Gateway controller runs in namespace `envoy-gateway-system`
- managed Envoy data plane resources stay in the controller namespace
- `Gateway` and `HTTPRoute` remain in the application namespace
- `GatewayClass` is applied separately as a cluster-scoped resource

This avoids the extra RBAC and operational complexity of Gateway Namespace
Mode during the first migration phase.

## Repo Expectations

This folder contains:

- a short operational README
- environment-specific Helm values
- the cluster-scoped `GatewayClass`

Current files:

- `values-dev.yaml`
- `values-prod.yaml`
- `gatewayclass.yaml`

## Recommended Helm Workflow

Install or upgrade the DEV controller:

```bash
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.7.0 \
  --namespace envoy-gateway-system \
  --create-namespace \
  -f deploy/infra/envoy-gateway/values-dev.yaml
```

Install or upgrade the PROD controller:

```bash
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.7.0 \
  --namespace envoy-gateway-system \
  --create-namespace \
  -f deploy/infra/envoy-gateway/values-prod.yaml
```

Apply the shared `GatewayClass` after the controller is available:

```bash
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
```

## Phase-1 Notes

- phase 1 keeps the old `Ingress` resources in place
- Envoy is validated through a staging NodePort instead of the live `30080`
- the external `GW` configuration is not changed in this phase
- cutover to the live NodePort belongs to phase 2
