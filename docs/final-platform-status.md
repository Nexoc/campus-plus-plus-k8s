# Campus++ Final Platform Status

## Executive Summary

Campus++ is currently implemented as a production-like Kubernetes and DevOps
platform in the lab environment. The project includes application delivery,
cluster ingress, external database integration, host automation, and central
monitoring.

The technical baseline is complete:

- DEV releases are delivered from `uni-dev-*` and `home-dev-*` tags.
- PROD releases are delivered from `uni-v*` and `home-v*` tags with GitHub
  environment approval.
- DEV and PROD Kubernetes runtimes are active.
- The external PostgreSQL dependency is reachable through a stable Kubernetes
  alias.
- Ansible operations and verification playbooks are available.
- Central monitoring is installed and verified.

## Infrastructure Roles

`gw`

- control host for production operations
- entry point for server access
- production deployment runner host
- location of the production kubeconfig
- host that runs Ansible, kubectl, and Helm operations

`s4-db`

- external PostgreSQL VM
- database endpoint for DEV and PROD application workloads
- host for `prometheus-postgres-exporter`

`s5-dev`

- DEV k3s cluster
- DEV GitHub Actions runner host
- target for `uni-dev-*` releases
- owner of the `campus-dev` runtime

`s6-monitoring`

- central monitoring VM
- runs Prometheus
- runs Grafana
- scrapes VM, database, and Kubernetes metrics

`s1-prod`, `s2-prod`, `s3-prod`

- PROD k3s HA cluster nodes
- control-plane and etcd members
- target nodes for `campus-prod` workloads
- NodePort entry targets for the production Envoy Gateway path

## CI/CD Status

DEV delivery:

```text
uni-dev-* tag
-> GitHub Actions
-> s5-campus-dev runner
-> s5-dev k3s
-> campus-dev
-> Envoy Gateway NodePort 30080
```

PROD delivery:

```text
uni-v* tag
-> GitHub Actions
-> GitHub environment: production
-> manual approval
-> gw-campus-prod runner
-> prod k3s HA cluster
-> campus-prod
-> Envoy Gateway NodePort 30080
```

The tag-based model separates release intent from branch pushes:

- `main` runs validation CI.
- `uni-dev-*` releases to university DEV.
- `home-dev-*` releases to home DEV.
- `uni-v*` releases to university PROD after approval.
- `home-v*` releases to home PROD after approval.

Production hostnames:

- university DEV: `campus-dev.10-123-127-29.sslip.io`
- university PROD: `campus-prod.10-123-127-29.sslip.io`
- university Grafana: `grafana.10-123-127-29.sslip.io`
- home DEV: `home-campus-dev.davl.at`
- home PROD: `home-campus-prod.davl.at`
- home Grafana: `home-grafana.davl.at`

## Kubernetes Status

DEV cluster:

- single-node k3s on `s5-dev`
- namespace `campus-dev`
- Envoy Gateway / Gateway API active
- application exposed through NodePort `30080`

PROD cluster:

- k3s HA cluster on `s1-prod`, `s2-prod`, and `s3-prod`
- namespace `campus-prod`
- Envoy Gateway / Gateway API active
- application exposed through NodePort `30080`
- documented release baseline: `v0.1.1`

Application runtime:

- `frontend` runs in Kubernetes
- `auth` runs in Kubernetes
- `backend` runs in Kubernetes
- `campus-nginx` runs in Kubernetes
- `campus-importer` completes as a Kubernetes job

## Database Status

PostgreSQL runs outside Kubernetes on `s4-db`.

Application configuration keeps the database host stable:

```text
DB_HOST=s4-db
```

In PROD, `s4-db` is also a Kubernetes DNS alias inside `campus-prod`. The real
database endpoint is environment-specific runtime configuration, not a tracked
manifest value.

Runtime-only PROD database endpoint contract:

```text
/home/nexoc/campus-secrets/prod/db-endpoint.env
DB_ENDPOINT_ADDRESS=...
DB_ENDPOINT_PORT=...
```

The deployment script generates:

- `Service/s4-db`
- `EndpointSlice/s4-db`

This keeps the Kubernetes contract portable while allowing the lab and a future
environment to use different database endpoint addresses.

## Monitoring Status

Central monitoring is implemented on `s6-monitoring`.

Components:

- node-exporter on all 7 VMs
- Prometheus on `s6-monitoring`
- Grafana on `s6-monitoring`
- postgres exporter on `s4-db`
- kube-state-metrics in DEV and PROD k3s clusters

Expected Prometheus targets:

```text
7 node-exporter targets
1 Prometheus self-target
1 postgres-exporter target
2 kube-state-metrics targets
11 total targets
```

Grafana dashboards:

- Campus VM Overview
- Campus PostgreSQL Overview
- Campus Kubernetes Overview

The central monitoring health check is green through
`ops/playbooks/check-monitoring-stack.yml`.

## Operations And Checks

Ansible inventory is runtime-specific. Tracked examples define the contract,
while local inventories provide real environment addresses:

```text
ops/inventory/lab.example.ini
ops/inventory/university.example.ini
ops/inventory/*.local.ini
```

Important playbooks:

- `ops/playbooks/check-connectivity.yml`
- `ops/playbooks/bootstrap-gw.yml`
- `ops/playbooks/bootstrap-common.yml`
- `ops/playbooks/check-prod-cluster.yml`
- `ops/playbooks/check-db-access.yml`
- `ops/playbooks/verify-prod-release.yml`
- `ops/playbooks/check-monitoring-stack.yml`
- `ops/playbooks/install-node-exporter.yml`
- `ops/playbooks/install-prometheus.yml`
- `ops/playbooks/install-grafana.yml`
- `ops/playbooks/render-postgres-exporter-env.yml`
- `ops/playbooks/install-postgres-exporter.yml`
- `ops/playbooks/install-kube-state-metrics.yml`

Final platform health checks:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-prod-cluster.yml
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/verify-prod-release.yml
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-monitoring-stack.yml
```

## Portability Model

The portable contract is based on logical hostnames and runtime files:

- `gw`
- `s4-db`
- `s5-dev`
- `s6-monitoring`
- `s1-prod`
- `s2-prod`
- `s3-prod`

Tracked Kubernetes manifests and GitHub workflows do not depend on lab-specific
addresses as architecture contracts.

Environment-specific values belong in:

- ignored Ansible inventory files
- host-local runtime env files
- host-local credential files
- kubeconfig files on the appropriate control host

The same repository should move to another infrastructure by changing runtime
inputs, not by rewriting the deployment model.

## University Migration Readiness

For a future university deployment, these values are expected to change:

- inventory host addresses
- scrape addresses
- database endpoint address and port
- runtime credential files
- edge DNS/TLS configuration
- kubeconfig files
- GitHub runner registrations

These should not change:

- logical hostnames
- Kubernetes overlay structure
- `uni-dev-*`, `home-dev-*`, `uni-v*`, and `home-v*` release model
- stable application database host `s4-db`
- runtime database endpoint contract
- monitoring component roles
- Ansible playbook entry points

## Remaining Optional Improvements

The current baseline is complete, but the platform can be improved with:

- Alertmanager and Prometheus alert rules
- Loki or Grafana Alloy log collection
- RBAC-limited kubeconfigs for deployment runners
- backup and restore drills for PostgreSQL and cluster state
- final screenshots and architecture diagrams for portfolio presentation
