# envoy-gateway

This directory contains the infrastructure-side configuration for the active
Envoy Gateway controller used by Campus++ DEV.

App routing remains in the application layer:

- Envoy Gateway provides the Kubernetes entry point
- `campus-nginx` keeps app routing and `auth_request`

## Role In The Architecture

Current active path:

`Internet -> davl.at -> private/VPN path -> DEV 192.168.56.40:31080 -> Envoy Gateway -> campus-nginx -> services`

Responsibilities:

- public VPS / edge nginx: public hostname and TLS
- Envoy Gateway: Gateway API controller and data plane
- `campus-nginx`: internal app gateway

## Deployment Model

This repo uses the standard Envoy Gateway deployment mode:

- controller runs in `envoy-gateway-system`
- managed Envoy data plane resources also run there
- `Gateway` and `HTTPRoute` live in `campus-dev`
- `GatewayClass` is applied separately as a cluster-scoped resource

## Repo Contents

Current files:

- `values-dev.yaml`
- `values-prod.yaml`
- `gatewayclass.yaml`

## Recommended Helm Workflow

Install or upgrade the controller:

```bash
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.7.0 \
  --namespace envoy-gateway-system \
  --create-namespace \
  -f deploy/infra/envoy-gateway/values-dev.yaml
```

Apply the shared `GatewayClass`:

```bash
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
```

## Current DEV Notes

- the active app entry `NodePort` is `31080`
- the active `GatewayClass` is `campus-envoy`
- the active `Gateway` is `campus-dev`
- the active `HTTPRoute` is `campus`
- current routing target is `service/campus-nginx`

## Operational Note

The current DEV cluster is single-node and has shown intermittent instability.
If Envoy components restart, verify:

```bash
kubectl get gateway,httproute,envoyproxy -n campus-dev -o wide
kubectl get all -n envoy-gateway-system -o wide
kubectl logs -n envoy-gateway-system deployment/envoy-gateway --tail=200
```
