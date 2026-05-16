# envoy-gateway

This directory contains the infrastructure-side configuration for the active
Envoy Gateway controller used by Campus++ Kubernetes environments.

App routing remains in the application layer:

- Envoy Gateway provides the Kubernetes entry point
- `campus-nginx` keeps the internal app routing and `auth_request` boundary

## Role In The Architecture

Current lab path:

`Internet -> gw -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> services`

Current home path:

`Home edge hostname -> NodePort 30080 -> Envoy Gateway -> campus-nginx -> services`

Current production path:

`gw edge -> prod nodes NodePort 30080 -> Envoy Gateway -> campus-nginx -> services`

Responsibilities:

- external edge host or reverse proxy: public hostname and TLS
- Envoy Gateway: Gateway API controller and data plane
- `campus-nginx`: internal application gateway

## Deployment Model

This repo uses the standard Envoy Gateway deployment mode:

- controller runs in `envoy-gateway-system`
- managed Envoy data plane resources also run there
- `Gateway`, `HTTPRoute`, `EnvoyProxy`, and `ClientTrafficPolicy` live in the
  target application namespace, such as `campus-dev` or `campus-prod`
- `GatewayClass` is applied separately as a cluster-scoped resource

## Repo Contents

Current files:

- `values-dev.yaml`
- `values-prod.yaml`
- `gatewayclass.yaml`

## Recommended Helm Workflow

Install or upgrade the controller:

```bash
# server: s5-dev
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.7.2 \
  --namespace envoy-gateway-system \
  --create-namespace \
  -f deploy/infra/envoy-gateway/values-dev.yaml
```

For PROD, run the same operation against the production kubeconfig from `gw`
and use `values-prod.yaml`:

```bash
# server: gw
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.7.2 \
  --namespace envoy-gateway-system \
  --create-namespace \
  -f deploy/infra/envoy-gateway/values-prod.yaml \
  --kubeconfig /home/nexoc/.kube/prod.yaml
```

Apply the shared `GatewayClass`:

```bash
# server: s5-dev
kubectl apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
```

```bash
# server: gw
kubectl --kubeconfig /home/nexoc/.kube/prod.yaml apply -f deploy/infra/envoy-gateway/gatewayclass.yaml
```

## Current Notes

- active app entry `NodePort`: `30080`
- active `GatewayClass`: `campus-envoy`
- active `Gateway`: `campus`
- active `HTTPRoute`: `campus`
- current routing target: `service/campus-nginx`

## Operational Note

If Envoy components restart or the edge stops routing correctly, verify:

```bash
# server: s5-dev
kubectl get gateway,httproute,envoyproxy,clienttrafficpolicy -n campus-dev -o wide
kubectl get all -n envoy-gateway-system -o wide
kubectl logs -n envoy-gateway-system deployment/envoy-gateway --tail=200
```

```bash
# server: gw
kubectl --kubeconfig /home/nexoc/.kube/prod.yaml get gateway,httproute,envoyproxy,clienttrafficpolicy -n campus-prod -o wide
kubectl --kubeconfig /home/nexoc/.kube/prod.yaml get all -n envoy-gateway-system -o wide
kubectl --kubeconfig /home/nexoc/.kube/prod.yaml logs -n envoy-gateway-system deployment/envoy-gateway --tail=200
```
