# Home Lab Refactor Status

This file is the root orientation note for the home-only refactor.

## Active Target

```text
home lab on one physical PC with VM clones
```

Server roles stay stable:

```text
gw          -> gateway / runner / ansible / edge
s4-db       -> PostgreSQL
s5-dev      -> dev k3s
s6-monitoring -> Prometheus + Grafana
s1-prod     -> prod k3s node 1
s2-prod     -> prod k3s node 2
s3-prod     -> prod k3s node 3
```

## Release Model

```text
home-dev-* -> s5-dev / campus-dev
home-v*    -> s1-prod/s2-prod/s3-prod / campus-prod
```

## Active Hostnames

```text
home dev      home-campus-dev.davl.at
home prod     home-campus-prod.davl.at
home grafana  home-grafana.davl.at
```

## Active Workflows

```text
.github/workflows/ci.yml
.github/workflows/deploy-home-dev.yml
.github/workflows/deploy-home-prod.yml
```

## Active Inventory

Tracked example:

```text
ops/inventory/home.example.ini
```

Runtime file on `gw`:

```text
ops/inventory/home.local.ini
```

## Runtime Files On gw

```text
/home/nexoc/.kube/dev.yaml
/home/nexoc/.kube/prod.yaml
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

## Acceptance Checklist

```text
home-dev-* deploys to s5-dev / campus-dev
home-v* deploys to s1-prod/s2-prod/s3-prod / campus-prod
gw has one active runner: home-gw-runner
s4-db remains the database VM
s6-monitoring remains the monitoring VM
docs describe the home VM lab
```
