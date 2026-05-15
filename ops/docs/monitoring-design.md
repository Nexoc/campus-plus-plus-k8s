# Monitoring Design

This document defines the monitoring phase for the Campus++ Kubernetes migration project.

Current baseline:

```text
dev CD: ready
prod CD: ready
portable prod DB endpoint: ready
Ansible ops/check layer: ready
next phase: monitoring on s6-monitoring
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
  -> cluster agents/exporters
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

Prometheus, Grafana, Alertmanager, and Loki are not application release artifacts. They should not be deployed through the Campus++ app CD workflow.

## Monitoring Topology

Target architecture:

```text
local pc
  -> gw
  -> s6-monitoring
      -> Prometheus
      -> Grafana
      -> Alertmanager
      -> optional Loki

s6-monitoring
  -> scrape node-exporter on all VMs
  -> scrape postgres exporter on s4-db
  -> scrape or query dev k3s monitoring add-ons
  -> scrape or query prod k3s monitoring add-ons
  -> run HTTP smoke checks against Envoy NodePort endpoints
```

`gw` remains the control host. It runs Ansible, `kubectl`, and `helm`, but it does not become a monitoring workload node.

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

Initial VM-level components:

```text
s6-monitoring:
  prometheus
  grafana
  alertmanager

all VMs:
  node-exporter

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

Logging components, deferred until after metrics:

```text
s6-monitoring:
  loki

VMs and clusters:
  promtail or Grafana Alloy
```

## Network And Ports

Recommended service ports:

```text
Prometheus:       9090
Grafana:          3000
Alertmanager:     9093
Loki:             3100
node-exporter:    9100
postgres exporter:9187
Envoy NodePort:   30080
PostgreSQL:       5432
```

Access model:

```text
s6-monitoring -> node-exporter:9100 on all VMs
s6-monitoring -> postgres exporter:9187 on s4-db
s6-monitoring -> Envoy NodePort 30080 on s5-dev and prod nodes
admin/gw      -> Grafana on s6-monitoring
admin/gw      -> Prometheus on s6-monitoring
```

Firewall principle:

```text
exporter ports should be reachable only from s6-monitoring
Grafana should be reachable only from trusted admin paths
Prometheus and Alertmanager should not be publicly exposed
PostgreSQL 5432 stays restricted to approved app clients
```

## Kubernetes Metrics Design

Because Prometheus is planned as a central service on `s6-monitoring`, Kubernetes metrics need an explicit bridge between the central VM and each cluster.

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

Recommended first implementation:

```text
Phase 1:
  host metrics on all VMs
  PostgreSQL exporter on s4-db
  HTTP smoke checks for dev/prod Envoy routes

Phase 2:
  kube-state-metrics in dev/prod clusters through Helm
  choose restricted NodePort or agent/federation path

Phase 3:
  logs with Loki and promtail/Grafana Alloy
```

This avoids pretending that a central Prometheus can automatically scrape in-cluster `ClusterIP` services from outside the cluster.

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

The exact runtime secret contract should be documented before implementing exporters that need credentials.

## Success Criteria

Phase 1 success:

```text
s6-monitoring is reachable from gw
Prometheus is running on s6-monitoring
Grafana is running on s6-monitoring
node-exporter is running on all VMs
Prometheus target status is up for all node-exporter targets
postgres exporter is running on s4-db
Prometheus target status is up for postgres exporter
HTTP smoke checks for dev and prod routes are visible in Prometheus
no monitoring secret values are committed or printed
```

Phase 2 success:

```text
kube-state-metrics is installed in dev cluster
kube-state-metrics is installed in prod cluster
central monitoring can read selected dev/prod cluster metrics
dashboards show workload health for campus-dev and campus-prod
Gateway API and Envoy status checks are represented
```

Phase 3 success:

```text
logs from selected services are collected
Grafana can query logs by environment and workload
log agents do not expose credentials in repo
```

## Implementation Phases

Phase 0: design and inventory

```text
document monitoring architecture
confirm ports and access paths
confirm host-level versus cluster-level boundaries
confirm secret file contract
```

Phase 1: central VM and host exporters

```text
bootstrap s6-monitoring
install Prometheus and Grafana on s6-monitoring
install node-exporter on all VMs
install postgres exporter on s4-db
configure firewall rules for exporter ports
add Ansible checks for monitoring services and targets
```

Phase 2: Kubernetes add-ons

```text
install kube-state-metrics in dev cluster through Helm from gw
install kube-state-metrics in prod cluster through Helm from gw
choose scrape exposure pattern for central Prometheus
add kubectl verification checks
```

Phase 3: dashboards and alerts

```text
add Grafana dashboards
add Prometheus alert rules
add Alertmanager routes
document alert severity and expected response
```

Phase 4: logs

```text
install Loki on s6-monitoring
install promtail or Grafana Alloy on selected hosts/clusters
add log dashboards
document retention policy
```

## First Playbooks

Initial preflight file:

```text
ops/playbooks/check-monitoring.yml
ops/playbooks/bootstrap-monitoring.yml
```

Suggested next files:

```text
ops/playbooks/install-node-exporter.yml
ops/playbooks/install-prometheus-grafana.yml
ops/playbooks/install-postgres-exporter.yml
```

Suggested future docs:

```text
ops/templates/prometheus.yml.example
ops/templates/grafana.env.example
ops/templates/postgres-exporter.env.example
```

No monitoring installation should start until the phase 1 runtime secret and port contract is confirmed.
