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
```

Implemented monitoring core:

```text
s6-monitoring bootstrap: ready
node-exporter on all 7 lab VMs: ready
Prometheus on s6-monitoring: ready
Grafana on s6-monitoring: ready
Grafana datasource Campus Prometheus: ready
Grafana dashboard Campus VM Overview: ready
check-monitoring-stack.yml: ready
```

Not implemented yet:

```text
postgres exporter for s4-db
kube-state-metrics for dev/prod clusters
Prometheus alert rules
Alertmanager
Loki / log collection
```

## Role Separation

The monitoring phase keeps the existing operational boundaries:

```text
Ansible
  -> bootstrap/check/configure servers
  -> install monitoring agents/exporters on VMs
  -> prepare s6-monitoring as the central monitoring host
  -> verify ports and system services

Helm
  -> install Kubernetes monitoring add-ons inside dev/prod clusters
  -> kube-state-metrics
  -> optional cluster agents/exporters
  -> optional log agents such as promtail or Grafana Alloy

GitHub Actions
  -> application CI/CD only
  -> dev-* and v* release pipelines
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
```

Planned topology extensions:

```text
s6-monitoring
  -> scrape postgres exporter on s4-db
  -> scrape or query dev k3s monitoring add-ons
  -> scrape or query prod k3s monitoring add-ons
  -> run HTTP smoke checks against Envoy NodePort endpoints
  -> Alertmanager
  -> optional Loki
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
Kubernetes add-ons inside dev/prod clusters: Helm
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

Next VM-level component:

```text
s4-db:
  postgres exporter
```

Kubernetes components, installed later through Helm:

```text
dev cluster:
  kube-state-metrics
  optional cluster metrics agent
  optional log agent

prod cluster:
  kube-state-metrics
  optional cluster metrics agent
  optional log agent
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
Envoy NodePort:   30080
PostgreSQL:       5432
```

Planned monitoring ports:

```text
Alertmanager:     9093
Loki:             3100
postgres exporter:9187
```

Access model:

```text
s6-monitoring -> node-exporter:9100 on all VMs
admin/gw      -> Grafana on s6-monitoring
admin/gw      -> Prometheus on s6-monitoring
```

Planned access model:

```text
s6-monitoring -> postgres exporter:9187 on s4-db
s6-monitoring -> Envoy NodePort 30080 on s5-dev and prod nodes
```

Firewall principle:

```text
exporter ports should be reachable only from s6-monitoring
Grafana should be reachable only from trusted admin paths
Prometheus and Alertmanager should not be publicly exposed
PostgreSQL 5432 stays restricted to approved app clients
```

## Kubernetes Metrics Design

Because Prometheus is a central service on `s6-monitoring`, Kubernetes metrics
need an explicit bridge between the central VM and each cluster.

Acceptable options:

```text
Option A:
  kube-state-metrics inside each cluster
  expose scrape endpoint only to s6-monitoring through a restricted NodePort

Option B:
  lightweight Prometheus/agent inside each cluster
  remote-write or federate selected metrics to s6-monitoring

Option C:
  start with blackbox and API-level checks from s6-monitoring
  add deep cluster metrics later
```

Recommended next implementation:

```text
1. add PostgreSQL exporter on s4-db
2. add database dashboard panels
3. add kube-state-metrics in dev/prod clusters through Helm
4. choose restricted NodePort or agent/federation path for cluster metrics
5. add alerting and logs
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
dashboard file: /var/lib/grafana/dashboards/campus-vm-overview.json
initial dashboard: Campus VM Overview
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
check-monitoring-stack.yml passes
no monitoring secret values are committed or printed
```

Next database monitoring success criteria:

```text
postgres exporter is running on s4-db
Prometheus target status is up for postgres exporter
database dashboard panels show PostgreSQL uptime and activity
no database passwords are committed or printed
```

Future cluster metrics success criteria:

```text
kube-state-metrics is installed in dev cluster
kube-state-metrics is installed in prod cluster
central monitoring can read selected dev/prod cluster metrics
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
  added check-monitoring-stack.yml
```

Next:

```text
Phase 1b:
  render postgres exporter runtime env on s4-db
  install postgres exporter on s4-db
  add database dashboard panels
  add exporter-specific checks
```

Later:

```text
Phase 2:
  install kube-state-metrics in dev cluster through Helm from gw
  install kube-state-metrics in prod cluster through Helm from gw
  choose scrape exposure pattern for central Prometheus
  add kubectl verification checks

Phase 3:
  add Grafana dashboards for Kubernetes and Gateway API
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
```

Suggested next playbook:

```text
ops/playbooks/install-postgres-exporter.yml
```

Suggested future templates:

```text
ops/templates/postgres-exporter.env.example
ops/templates/alertmanager.env.example
```
