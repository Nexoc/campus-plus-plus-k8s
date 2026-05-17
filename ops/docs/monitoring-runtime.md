# Monitoring Runtime Model

This document fixes the runtime choice for the first monitoring implementation.

## Decision

Current implemented runtime:

```text
s6-monitoring runs Prometheus and Grafana as systemd-managed services.
node-exporter runs as a systemd service on every VM.
postgres exporter support runs as a systemd service on s4-db after its install playbook is applied.
Kubernetes add-ons are installed later through Helm from gw.
```

Planned runtime extensions:

```text
Alertmanager and Loki run as systemd-managed services on s6-monitoring.
kube-state-metrics runs inside dev/prod clusters through Helm.
```

## Chosen Model

Use native Linux services for the central VM layer:

```text
s6-monitoring:
  prometheus.service
  grafana-server.service
  alertmanager.service later
  loki.service later

all VMs:
  prometheus-node-exporter.service

s4-db:
  prometheus-postgres-exporter.service
```

Use Helm only for Kubernetes-side components:

```text
dev cluster:
  kube-state-metrics
  optional cluster agents

prod cluster:
  kube-state-metrics
  optional cluster agents
```

## Why Not Docker Compose First

Docker Compose is valid, but it would introduce another runtime dependency on
`s6-monitoring`.

The original `s6-monitoring` preflight showed:

```text
docker: not installed
podman: not installed
systemd: available
```

Using systemd services keeps the monitoring phase close to the current host
baseline and makes idempotent Ansible checks straightforward.

## Why Not Helm For Central Monitoring

Helm is for components inside Kubernetes clusters.

The central monitoring stack is intentionally outside the dev/prod clusters:

```text
s6-monitoring = central monitoring VM
prod workloads = s1-prod/s2-prod/s3-prod k3s cluster
dev workloads = s5-dev k3s cluster
```

Installing the central Prometheus/Grafana stack through Helm would require
putting it inside a cluster, which changes the architecture.

## Runtime Paths

Central monitoring paths on `s6-monitoring`:

```text
/etc/campus-monitoring
/opt/campus-monitoring
/var/lib/campus-monitoring
/var/log/campus-monitoring
/home/nexoc/campus-secrets/monitoring
```

Implemented config files:

```text
/etc/campus-monitoring/prometheus/prometheus.yml
/etc/grafana/grafana.ini
/etc/grafana/provisioning/datasources/campus-prometheus.yml
/etc/grafana/provisioning/dashboards/campus-dashboards.yml
/var/lib/grafana/dashboards/campus-vm-overview.json
```

Planned config files:

```text
/etc/campus-monitoring/alertmanager/alertmanager.yml
/etc/campus-monitoring/loki/loki.yml
```

Implemented data directories:

```text
/var/lib/campus-monitoring/prometheus
/var/lib/grafana
/var/lib/grafana/dashboards
```

Planned data directories:

```text
/var/lib/campus-monitoring/alertmanager
/var/lib/campus-monitoring/loki
```

## Runtime Secrets

No real secrets are committed.

Runtime-only files:

```text
/home/nexoc/campus-secrets/monitoring/grafana.env
/home/nexoc/campus-secrets/monitoring/postgres-exporter.env
/home/nexoc/campus-secrets/monitoring/alertmanager.env
```

`postgres-exporter.env` is generated on `s4-db` by
`render-postgres-exporter-env.yml`. Its DSN host and port come from the PROD
runtime endpoint file first, then tracked PROD config, with `s4-db` as the final
host fallback. The generated DSN is not printed by the playbook.

Example templates live in:

```text
ops/templates/grafana.env.example
ops/templates/postgres-exporter.env.example
ops/templates/alertmanager.env.example
```

## Install Order

Completed order:

```text
1. check-monitoring.yml
2. bootstrap-monitoring.yml
3. install-node-exporter.yml
4. install-prometheus.yml
5. install-grafana.yml
6. check-monitoring-stack.yml
```

Next order:

```text
7. render-postgres-exporter-env.yml
8. install-postgres-exporter.yml
9. install-prometheus.yml
10. add database dashboard panels
11. extend check-monitoring-stack.yml or add a database-specific check
```

Future order:

```text
11. install kube-state-metrics in dev/prod clusters
12. add cluster metrics scrape path
13. add alert rules and Alertmanager
14. add Loki and log agents
```

## Current Success Criteria

Systemd services:

```text
prometheus.service active on s6-monitoring
grafana-server.service active on s6-monitoring
prometheus-node-exporter.service active on all VMs
```

Ports:

```text
s6-monitoring:9090 Prometheus
s6-monitoring:3000 Grafana
all VMs:9100 node-exporter
s4-db:9187 postgres exporter after install-postgres-exporter.yml
```

Access:

```text
gw can reach Prometheus and Grafana on s6-monitoring
s6-monitoring can scrape node-exporter on all VMs
```

Prometheus targets:

```text
1 prometheus self-target
7 node-exporter targets
1 postgres-exporter target after install-postgres-exporter.yml and install-prometheus.yml
8 total targets before PostgreSQL exporter
9 total targets after PostgreSQL exporter
```

## Planned Success Criteria

Database metrics:

```text
prometheus-postgres-exporter.service active on s4-db
s4-db:9187 postgres exporter
s6-monitoring can scrape postgres exporter on s4-db
```

Cluster metrics:

```text
kube-state-metrics active in dev cluster
kube-state-metrics active in prod cluster
central Prometheus can read selected cluster metrics
```

## Inventory Scrape Addresses

```text
monitoring_scrape_host defines the address that s6-monitoring uses for each VM.
This is separate from ansible_host because gw may use ansible_connection=local.
Lab IP values belong in ops/inventory/lab.local.ini only.
University deployments should keep the same variable names and replace addresses.
```

## Firewall Contracts

Node-exporter firewall contract:

```text
node-exporter listens on port 9100 on each VM.
iptables allows 9100 from loopback for local health checks.
iptables allows 9100 only from the monitoring_scrape_host of s6-monitoring.
all other TCP traffic to 9100 is dropped.
```

Prometheus firewall contract:

```text
Prometheus listens on port 9090 on s6-monitoring.
iptables allows 9090 from loopback for local health checks.
iptables allows 9090 from the monitoring_scrape_host of gw.
all other TCP traffic to 9090 is dropped.
```

Grafana firewall contract:

```text
Grafana listens on port 3000 on s6-monitoring.
iptables allows 3000 from loopback for local health checks.
iptables allows 3000 from the monitoring_scrape_host of gw.
all other TCP traffic to 3000 is dropped.
```

Postgres exporter firewall contract:

```text
postgres exporter listens on port 9187 on s4-db.
iptables allows 9187 from loopback for local health checks.
iptables allows 9187 only from the monitoring_scrape_host of s6-monitoring.
all other TCP traffic to 9187 is dropped.
```

## Grafana Provisioning

Current Grafana dashboard provisioning:

```text
dashboard provider: /etc/grafana/provisioning/dashboards/campus-dashboards.yml
dashboard files: /var/lib/grafana/dashboards
initial dashboard: Campus VM Overview
datasource name: Campus Prometheus
datasource UID: campus-prometheus
datasource URL: http://localhost:9090
```

The datasource provisioning file includes `deleteDatasources` for
`Campus Prometheus`. This allows Grafana to replace an older datasource created
without the stable UID and keeps the dashboard references working.

## Security

```text
exporter ports are not public
Prometheus and Grafana are reachable only through trusted lab paths
secrets stay under /home/nexoc/campus-secrets/monitoring
no monitoring passwords or tokens are printed by playbooks
```
