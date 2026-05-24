# Environments

This document describes the active Campus++ environment model.

The repository targets one active infrastructure model:

```text
home lab on one physical PC with VM clones
```

`uni` is not an active target. The server roles stay and are interpreted as
home-lab VM roles.

## Home Lab Roles

- `gw` / `192.168.56.10`: gateway, runner host, Ansible control host, kubectl/Helm control host, edge host
- `s4-db` / `192.168.56.20`: PostgreSQL VM outside Kubernetes
- `s6-monitoring` / `192.168.56.30`: Prometheus and Grafana VM
- `s5-dev` / `192.168.56.40`: single-node dev k3s cluster
- `s1-prod` / `192.168.56.51`, `s2-prod` / `192.168.56.52`, `s3-prod` / `192.168.56.53`: home production k3s HA nodes

IP addresses belong in ignored inventory files, DNS, or host-local runtime
configuration. Tracked Kubernetes manifests and workflows use logical names and
runtime contracts.

## Runtime Environments

Current active app environments:

- `home`: home dev release target, namespace `campus-dev`, cluster `s5-dev`
- `prod`: home production release target, namespace `campus-prod`, cluster `s1-prod/s2-prod/s3-prod`

`deploy/app/overlays/dev` is legacy/manual compatibility. The active home dev
release channel is `deploy/app/overlays/home`.

## Release Channels

```text
main        -> validation only
home-dev-*  -> campus-dev on s5-dev
home-v*     -> campus-prod on s1-prod/s2-prod/s3-prod
```

Deployment runner:

```text
home-gw-runner
labels: self-hosted, Linux, X64, home, gw, deploy
```

Production approval environment:

```text
home-production
```

## Hostname Matrix

```text
home dev      home-campus-dev.davl.at
home prod     home-campus-prod.davl.at
home grafana  home-grafana.davl.at
```

## Request Paths

Home dev:

```text
internet -> DNS -> VPS nginx HTTPS -> WireGuard -> home VM network -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> services -> s4-db
```

Home prod:

```text
internet -> DNS -> VPS nginx HTTPS -> WireGuard -> home VM network -> s1-prod|s2-prod|s3-prod:30080 -> Envoy Gateway -> campus-prod -> services -> s4-db
```

Verified external status:

```text
http://home-campus-dev.davl.at    -> 301
https://home-campus-dev.davl.at   -> 200
http://home-campus-prod.davl.at   -> 301
https://home-campus-prod.davl.at  -> 200
```

Monitoring:

```text
VMs -> node-exporter:9100 -> Prometheus on s6-monitoring -> Grafana
s4-db -> postgres-exporter:9187 -> Prometheus on s6-monitoring
dev/prod k3s -> kube-state-metrics -> Prometheus on s6-monitoring
admin browser -> home-grafana.davl.at -> VPS nginx basic auth -> s6-monitoring:3000 -> Grafana login
```

## Configuration Strategy

Current delivery uses:

- Kustomize overlays in `deploy/app/overlays/home` and `deploy/app/overlays/prod`
- Envoy Gateway baselines in `deploy/infra/envoy-gateway/`
- versioned non-secret config under each overlay
- optional ignored local fallback secret env files under each overlay
- GHCR images tagged exactly with the release tag
- Ansible inventory in `ops/inventory/home.local.ini`
- host-local runtime files under `/home/nexoc/campus-secrets`

Self-hosted deployments with `CAMPUS_SECRETS_ROOT` stage real secret env files
into a temporary overlay copy outside the repo checkout.

## Secrets

Home dev files:

```text
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
/home/nexoc/campus-secrets/home/db-endpoint.env
```

Home prod files:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

`prod` means the home production cluster.

For home dev and home prod, the app keeps `DB_HOST=s4-db`; the deploy script
renders the Kubernetes `s4-db` Service and EndpointSlice from the matching
`db-endpoint.env`.

Real secrets must not be committed or printed.

## Outside Repo

These remain runtime-only:

- real VM addresses
- kubeconfig files
- GitHub runner registration token
- GitHub Actions secrets
- public DNS/TLS setup
- k3s installation and cluster bootstrap
- VPS nginx routing and basic auth state
