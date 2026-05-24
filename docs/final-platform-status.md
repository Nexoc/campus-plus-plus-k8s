# Campus++ Final Platform Status

## Executive Summary

Campus++ is now documented as a home Kubernetes lab on one physical PC with VM
clones. The active target is home-only.

The infrastructure roles stay:

```text
gw          -> gateway / runner / ansible / edge
s4-db       -> PostgreSQL
s5-dev      -> dev k3s
s6-monitoring -> Prometheus + Grafana
s1-prod     -> prod k3s node 1
s2-prod     -> prod k3s node 2
s3-prod     -> prod k3s node 3
```

`uni` is removed as an active target. `prod` means the home production k3s
cluster.

Current release model:

```text
home-dev-* -> home dev -> s5-dev -> campus-dev
home-v*    -> home prod -> s1-prod/s2-prod/s3-prod -> campus-prod
```

## Final CD Status

As of 2026-05-24, the home-only workflow/runtime refactor is in place.

The first real home dev CD reached `campus-dev`, but old test tags failed
because `s4-db` did not resolve inside the namespace. The repository now renders
`Service/s4-db` and `EndpointSlice/s4-db` for both `home` and `prod` from the
runtime-only `db-endpoint.env` file.

Do not reuse these failed local test tags:

```text
home-dev-test-20260524-1211
home-dev-test-20260524-1241
```

Next validation should use a fresh `home-dev-test-YYYYMMDD-HHMM` tag after
creating `/home/nexoc/campus-secrets/home/db-endpoint.env` on `gw`.

## Infrastructure Roles

`gw`

- home control host
- GitHub runner host: `home-gw-runner`
- SSH/Ansible/kubectl/Helm entry point
- home edge host
- location of dev/prod kubeconfigs and runtime secret roots

`s4-db`

- PostgreSQL VM
- database endpoint for home dev and home prod workloads
- host for the PostgreSQL exporter

`s5-dev`

- single-node dev k3s cluster
- owner of namespace `campus-dev`
- target for `home-dev-*` releases

`s6-monitoring`

- central monitoring VM
- Prometheus host
- Grafana host
- scrape target collector for VMs, PostgreSQL, and k3s metrics

`s1-prod`, `s2-prod`, `s3-prod`

- home production k3s HA cluster nodes
- target for `home-v*` releases
- owner of namespace `campus-prod`
- NodePort entry targets for the production Envoy Gateway path

## CI/CD Status

Home dev delivery:

```text
home-dev-* tag
-> GitHub Actions
-> home-gw-runner
-> /home/nexoc/.kube/dev.yaml
-> s5-dev k3s
-> campus-dev
-> Envoy Gateway NodePort 30080
-> Host: home-campus-dev.davl.at
```

Home prod delivery:

```text
home-v* tag
-> GitHub Actions
-> GitHub environment: home-production
-> manual approval
-> home-gw-runner
-> /home/nexoc/.kube/prod.yaml
-> s1-prod/s2-prod/s3-prod k3s HA
-> campus-prod
-> Envoy Gateway NodePort 30080
-> Host: home-campus-prod.davl.at
```

Hostnames:

```text
home dev      home-campus-dev.davl.at
home prod     home-campus-prod.davl.at
home grafana  home-grafana.davl.at
```

## Kubernetes Status

Home dev cluster:

- single-node k3s on `s5-dev`
- namespace `campus-dev`
- Envoy Gateway / Gateway API entry
- NodePort `30080`

Home prod cluster:

- k3s HA on `s1-prod`, `s2-prod`, and `s3-prod`
- namespace `campus-prod`
- Envoy Gateway / Gateway API entry
- NodePort `30080`

Application runtime:

- `frontend` runs in Kubernetes
- `auth` runs in Kubernetes
- `backend` runs in Kubernetes
- `campus-nginx` runs in Kubernetes
- `campus-importer` runs as a Kubernetes job

## Database Status

PostgreSQL stays outside Kubernetes on `s4-db`.

Stable application contract:

```text
DB_HOST=s4-db
```

For home dev and home prod, `s4-db` is also a Kubernetes DNS alias inside
`campus-dev` and `campus-prod`. The real endpoint is read from:

```text
/home/nexoc/campus-secrets/home/db-endpoint.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

The deployment script generates:

- `Service/s4-db`
- `EndpointSlice/s4-db`

## Monitoring Status

Monitoring automation is available for `s6-monitoring`. Current home-lab
verification should be rerun after the home-only refactor.

Components:

- node-exporter on all VMs
- Prometheus on `s6-monitoring`
- Grafana on `s6-monitoring`
- postgres exporter on `s4-db`
- kube-state-metrics in dev/prod k3s clusters

Expected Grafana hostname:

```text
home-grafana.davl.at
```

## Operations And Checks

Tracked inventory example:

```text
ops/inventory/home.example.ini
```

Ignored runtime inventory:

```text
ops/inventory/home.local.ini
```

Important playbooks:

- `ops/playbooks/check-connectivity.yml`
- `ops/playbooks/bootstrap-gw.yml`
- `ops/playbooks/bootstrap-common.yml`
- `ops/playbooks/check-prod-cluster.yml`
- `ops/playbooks/check-db-access.yml`
- `ops/playbooks/verify-prod-release.yml`
- `ops/playbooks/check-monitoring.yml`
- `ops/playbooks/bootstrap-monitoring.yml`
- `ops/playbooks/install-node-exporter.yml`
- `ops/playbooks/install-prometheus.yml`
- `ops/playbooks/install-grafana.yml`
- `ops/playbooks/check-monitoring-stack.yml`

Home-lab checks:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/check-prod-cluster.yml
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/verify-prod-release.yml
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/check-monitoring-stack.yml
```

## Portability Model

The portable contract is based on logical hostnames and runtime files:

```text
gw
s4-db
s5-dev
s6-monitoring
s1-prod
s2-prod
s3-prod
```

Environment-specific values belong in:

- ignored Ansible inventory files
- host-local runtime env files
- host-local credential files
- kubeconfig files on `gw`
- GitHub runner registrations and GitHub environment settings

## Remaining Optional Improvements

- verify home dev end to end with a fresh `home-dev-test-*` tag
- verify home prod end to end with a fresh `home-v*` tag
- verify home monitoring end to end
- add RBAC-limited kubeconfigs for deployment runners
- add Alertmanager and Prometheus alert rules
- add Loki or Grafana Alloy log collection
- add backup and restore drills for PostgreSQL and k3s state
