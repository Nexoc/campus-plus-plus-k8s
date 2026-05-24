# Monitoring Design

This document defines the monitoring phase for the Campus++ Kubernetes
migration project.

## Current Baseline

The current platform baseline is:

```text
dev CD: ready
prod CD: ready
portable prod DB endpoint: ready
Ansible ops/check layer: ready
central monitoring core: ready
postgres exporter: ready
kube-state-metrics: ready
monitoring dashboards: ready
monitoring visual verification: complete
protected Grafana external access: ready
```

Implemented monitoring core:

```text
s6-monitoring bootstrap: ready
node-exporter on all 7 lab VMs: ready
Prometheus on s6-monitoring: ready
Grafana on s6-monitoring: ready
Grafana datasource Campus Prometheus: ready
Grafana dashboard Campus VM Overview: ready
Grafana dashboard Campus PostgreSQL Overview: ready
Grafana dashboard Campus Kubernetes Overview: ready
check-monitoring-stack.yml: ready
PostgreSQL exporter automation: ready
kube-state-metrics dev/prod manifests and install playbook: ready
Grafana visual baseline verification: complete
Grafana protected external access through VPS nginx basic auth: complete
```

Not implemented yet:

```text
Prometheus alert rules
Alertmanager
Loki / log collection
```

PostgreSQL exporter is managed by `render-postgres-exporter-env.yml`,
`install-postgres-exporter.yml`, and `install-prometheus.yml`. The exporter DSN
is generated from runtime inputs and is not committed or printed.

## Role Separation

The monitoring phase keeps the existing operational boundaries:

```text
Ansible
  -> bootstrap/check/configure servers
  -> install monitoring agents/exporters on VMs
  -> prepare s6-monitoring as the central monitoring host
  -> verify ports and system services

Kubernetes manifests / Helm
  -> install Kubernetes monitoring add-ons inside dev/prod clusters
  -> kube-state-metrics through tracked Kustomize manifests
  -> future log or agent components such as promtail or Grafana Alloy

GitHub Actions
  -> application CI/CD only
  -> home-dev-* and home-v* release pipelines
  -> not the primary mechanism for configuring monitoring

kubectl
  -> verify dev/prod cluster status
  -> verify pods, services, routes, Gateway API resources, and monitoring add-ons
```

Main rule:

```text
s6-monitoring is a central monitoring VM.
Kubernetes add-ons live inside the dev/prod clusters.
```

Prometheus, Grafana, Alertmanager, and Loki are not application release
artifacts. They should not be deployed through the Campus++ app CD workflow.

## Monitoring Topology

Current implemented topology:

```text
local pc
  -> gw
  -> s6-monitoring
      -> Prometheus
      -> Grafana

s6-monitoring
  -> scrape node-exporter on all VMs
  -> scrape postgres exporter on s4-db
  -> scrape kube-state-metrics on dev through s5-dev:30091
  -> scrape kube-state-metrics on prod through a prod node:30092
```

`gw` remains the control host. It runs Ansible, `kubectl`, and `helm`, but it
does not become a monitoring workload node.

## Monitoring Targets

VM targets:

```text
gw
s4-db
s5-dev
s6-monitoring
s1-prod
s2-prod
s3-prod
```

Cluster targets:

```text
dev k3s cluster on s5-dev
prod k3s HA cluster on s1-prod/s2-prod/s3-prod
campus-dev namespace
campus-prod namespace
Envoy Gateway / Gateway API
```

Application and infrastructure checks:

```text
campus-dev HTTP route
campus-prod HTTP route
Envoy NodePort 30080
PostgreSQL endpoint on s4-db
s4-db Kubernetes DNS alias in campus-prod
GitHub Actions runner services on s5-dev and gw
```

## Components

Chosen runtime model:

```text
central monitoring services on s6-monitoring: systemd-managed services
VM exporters: systemd-managed services
Kubernetes add-ons inside dev/prod clusters: Kustomize manifests or Helm
```

See [Monitoring Runtime Model](monitoring-runtime.md) for the detailed runtime
decision.

Implemented VM-level components:

```text
s6-monitoring:
  prometheus
  grafana

all VMs:
  node-exporter
```

Database VM-level component:

```text
s4-db:
  postgres exporter on port 9187
```

Kubernetes components:

```text
dev cluster:
  kube-state-metrics on NodePort 30091
  future log or agent components

prod cluster:
  kube-state-metrics on NodePort 30092
  future log or agent components
```

Logging and alerting components, deferred until after core metrics:

```text
s6-monitoring:
  alertmanager
  loki

VMs and clusters:
  promtail or Grafana Alloy
```

## Network And Ports

Current implemented service ports:

```text
Prometheus:       9090
Grafana:          3000
node-exporter:    9100
postgres exporter:9187
kube-state dev:   30091
kube-state prod:  30092
Envoy NodePort:   30080
PostgreSQL:       5432
```

Planned monitoring ports:

```text
Alertmanager:     9093
Loki:             3100
```

Access model:

```text
s6-monitoring -> node-exporter:9100 on all VMs
s6-monitoring -> postgres exporter:9187 on s4-db
s6-monitoring -> kube-state-metrics:30091 on s5-dev
s6-monitoring -> kube-state-metrics:30092 on a prod node
admin/gw      -> Grafana on s6-monitoring
admin/gw      -> Prometheus on s6-monitoring
```

Planned access model:

```text
s6-monitoring -> Envoy NodePort 30080 on s5-dev and prod nodes
s6-monitoring -> Alertmanager/Loki later if those services are added
```

Firewall principle:

```text
exporter ports should be reachable only from s6-monitoring
Grafana should be reachable externally only through protected VPS nginx routing
Prometheus and Alertmanager should not be publicly exposed
PostgreSQL 5432 stays restricted to approved app clients
```

## Kubernetes Metrics Design

Because Prometheus is a central service on `s6-monitoring`, Kubernetes metrics
need an explicit bridge between the central VM and each cluster.

Implemented cluster metrics model:

```text
kube-state-metrics runs inside each cluster.
Each cluster exposes the scrape endpoint only to s6-monitoring through a restricted NodePort.
dev uses NodePort 30091.
prod uses NodePort 30092.
```

Current monitoring install sequence:

```text
1. run PostgreSQL exporter env render and install playbooks
2. re-render Prometheus config so it scrapes postgres-exporter
3. run install-kube-state-metrics.yml for dev/prod clusters
4. re-render Prometheus config so it scrapes kube-state-metrics
5. run install-grafana.yml to provision VM, PostgreSQL, and Kubernetes dashboards
6. run check-monitoring-stack.yml
```

This avoids pretending that a central Prometheus can automatically scrape
in-cluster `ClusterIP` services from outside the cluster.

## Grafana Provisioning

Current Grafana provisioning:

```text
datasource name: Campus Prometheus
datasource UID: campus-prometheus
dashboard folder: Campus++
dashboard provider: /etc/grafana/provisioning/dashboards/campus-dashboards.yml
dashboard files:
  /var/lib/grafana/dashboards/campus-vm-overview.json
  /var/lib/grafana/dashboards/campus-postgres-overview.json
  /var/lib/grafana/dashboards/campus-k8s-overview.json
dashboards:
  Campus VM Overview
  Campus PostgreSQL Overview
  Campus Kubernetes Overview
```

The datasource provisioning intentionally deletes and recreates the datasource
by name before applying the UID. This keeps the dashboard stable after the
`campus-prometheus` UID migration.

## Secrets Policy

No real secrets are committed.

Do not store these in git:

```text
DB_PASSWORD
JWT_SECRET
GitHub tokens
GHCR tokens
Grafana admin password
PostgreSQL exporter credentials
Alertmanager notification credentials
```

Runtime-only monitoring files should live on the appropriate host, for example:

```text
/home/nexoc/campus-secrets/monitoring
/home/nexoc/campus-secrets/monitoring/grafana.env
/home/nexoc/campus-secrets/monitoring/postgres-exporter.env
/home/nexoc/campus-secrets/monitoring/alertmanager.env
```

The exact runtime secret contract should be documented before implementing
exporters or alerts that need credentials.

For PostgreSQL exporter, `render-postgres-exporter-env.yml` generates
`postgres-exporter.env` on `s4-db` from existing PROD runtime inputs. The
database endpoint prefers `DB_ENDPOINT_ADDRESS` and `DB_ENDPOINT_PORT` from
`/home/nexoc/campus-secrets/prod/db-endpoint.env`, then falls back to tracked
PROD `DB_HOST` and `DB_PORT`, and finally to `s4-db`.

## Success Criteria

Completed core monitoring success criteria:

```text
s6-monitoring is reachable from gw
Prometheus is running on s6-monitoring
Grafana is running on s6-monitoring
node-exporter is running on all VMs
Prometheus target status is up for all node-exporter targets
Prometheus self-target is up
Grafana can reach Prometheus through the provisioned datasource
Campus VM Overview dashboard displays node-exporter data
Campus PostgreSQL Overview dashboard displays postgres-exporter data
Campus Kubernetes Overview dashboard displays kube-state-metrics data
home-grafana.davl.at reaches Grafana through nginx basic auth and Grafana login
Prometheus, exporters, and PostgreSQL remain private
check-monitoring-stack.yml passes
no monitoring secret values are committed or printed
```

Database monitoring success criteria:

```text
postgres exporter is running on s4-db
Prometheus target status is up for postgres exporter
database dashboard panels show PostgreSQL uptime and activity
no database passwords are committed or printed
```

Cluster metrics success criteria:

```text
kube-state-metrics is installed in dev cluster
kube-state-metrics is installed in prod cluster
central monitoring can scrape dev kube-state-metrics through NodePort 30091
central monitoring can scrape prod kube-state-metrics through NodePort 30092
dashboards show workload health for campus-dev and campus-prod
Gateway API and Envoy status checks are represented
```

Future logs success criteria:

```text
logs from selected services are collected
Grafana can query logs by environment and workload
log agents do not expose credentials in repo
```

## Implementation Phases

Completed:

```text
Phase 0:
  documented monitoring architecture and runtime model

Phase 1a:
  bootstrapped s6-monitoring
  installed node-exporter on all VMs
  installed Prometheus on s6-monitoring
  installed Grafana on s6-monitoring
  provisioned Campus Prometheus datasource
  provisioned Campus VM Overview dashboard
  provisioned Campus PostgreSQL Overview dashboard
  provisioned Campus Kubernetes Overview dashboard
  added check-monitoring-stack.yml
```

Current completed monitoring extension:

```text
Phase 1b:
  render postgres exporter runtime env on s4-db
  install postgres exporter on s4-db
  re-render Prometheus scrape config
  add exporter-specific checks
  install kube-state-metrics in dev/prod clusters through Kustomize manifests
  re-render Prometheus scrape config for kube-state-metrics
  provision PostgreSQL and Kubernetes dashboards
```

Later:

```text
Phase 2:
  add Gateway API / Envoy status checks

Phase 3:
  add Prometheus alert rules
  add Alertmanager routes
  document alert severity and expected response

Phase 4:
  install Loki on s6-monitoring
  install promtail or Grafana Alloy on selected hosts/clusters
  add log dashboards
  document retention policy
```

## Playbooks

Implemented playbooks:

```text
ops/playbooks/check-monitoring.yml
ops/playbooks/bootstrap-monitoring.yml
ops/playbooks/install-node-exporter.yml
ops/playbooks/install-prometheus.yml
ops/playbooks/install-grafana.yml
ops/playbooks/check-monitoring-stack.yml
ops/playbooks/render-postgres-exporter-env.yml
ops/playbooks/install-postgres-exporter.yml
ops/playbooks/install-kube-state-metrics.yml
```

Suggested future templates:

```text
ops/templates/postgres-exporter.env.example
ops/templates/alertmanager.env.example
```
