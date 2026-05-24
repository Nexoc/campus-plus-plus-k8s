# Home Lab Architecture

This repository targets a home Kubernetes lab running on one physical PC with
VM clones.

## Active Target

```text
home lab on one physical PC
```

`uni` is removed as an active target. The server roles are not removed and are
not renamed.

## VM Roles

```text
gw          -> gateway / runner / ansible / edge
s4-db       -> PostgreSQL
s5-dev      -> dev k3s
s6-monitoring -> Prometheus + Grafana
s1-prod     -> prod k3s node 1
s2-prod     -> prod k3s node 2
s3-prod     -> prod k3s node 3
```

`s4-db`, `s5-dev`, `s6-monitoring`, `s1-prod`, `s2-prod`, and `s3-prod` are
home-lab VM roles.

## Release Channels

```text
home-dev-* -> s5-dev / campus-dev
home-v*    -> s1-prod/s2-prod/s3-prod / campus-prod
```

## Hostnames

```text
home dev      home-campus-dev.davl.at
home prod     home-campus-prod.davl.at
home grafana  home-grafana.davl.at
```

## Runtime Files

On `gw`:

```text
/home/nexoc/.kube/dev.yaml
/home/nexoc/.kube/prod.yaml
/home/nexoc/campus-secrets/home/*
/home/nexoc/campus-secrets/prod/*
```

Active inventory:

```text
ops/inventory/home.local.ini
```

Tracked example:

```text
ops/inventory/home.example.ini
```
