# Monitoring Runtime Model

This document fixes the runtime choice for the first monitoring implementation.

Decision:

```text
s6-monitoring runs the central monitoring stack as systemd-managed services.
node-exporter runs as a systemd service on every VM.
postgres exporter runs as a systemd service on s4-db.
Kubernetes add-ons are installed later through Helm from gw.
```

## Chosen Model

Use native Linux services for the central VM layer:

```text
s6-monitoring:
  prometheus.service
  grafana-server.service
  alertmanager.service
  loki.service later

all VMs:
  node-exporter.service

s4-db:
  postgres-exporter.service
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

Docker Compose is valid, but it would introduce another runtime dependency on `s6-monitoring`.

Current `s6-monitoring` preflight shows:

```text
docker: not installed
podman: not installed
systemd: available
```

Using systemd services keeps the first monitoring phase closer to the current host baseline and makes idempotent Ansible checks straightforward.

## Why Not Helm For Central Monitoring

Helm is for components inside Kubernetes clusters.

The central monitoring stack is intentionally outside the dev/prod clusters:

```text
s6-monitoring = central monitoring VM
prod workloads = s1-prod/s2-prod/s3-prod k3s cluster
dev workloads = s5-dev k3s cluster
```

Installing the central Prometheus/Grafana stack through Helm would require putting it inside a cluster, which changes the architecture.

## Runtime Paths

Central monitoring paths on `s6-monitoring`:

```text
/etc/campus-monitoring
/opt/campus-monitoring
/var/lib/campus-monitoring
/var/log/campus-monitoring
/home/nexoc/campus-secrets/monitoring
```

Planned config files:

```text
/etc/campus-monitoring/prometheus/prometheus.yml
/etc/campus-monitoring/alertmanager/alertmanager.yml
/etc/campus-monitoring/loki/loki.yml
/etc/grafana/grafana.ini
```

Planned data directories:

```text
/var/lib/campus-monitoring/prometheus
/var/lib/campus-monitoring/alertmanager
/var/lib/campus-monitoring/loki
/var/lib/grafana
```

## Runtime Secrets

No real secrets are committed.

Runtime-only files:

```text
/home/nexoc/campus-secrets/monitoring/grafana.env
/home/nexoc/campus-secrets/monitoring/postgres-exporter.env
/home/nexoc/campus-secrets/monitoring/alertmanager.env
```

Example templates live in:

```text
ops/templates/grafana.env.example
ops/templates/postgres-exporter.env.example
ops/templates/alertmanager.env.example
```

## First Install Order

Recommended order:

```text
1. check-monitoring.yml
2. bootstrap-monitoring.yml
3. install-node-exporter.yml
4. check node-exporter targets from s6-monitoring
5. install-prometheus.yml
6. configure Prometheus scrape targets
7. install-grafana.yml
8. install-postgres-exporter.yml
9. check-monitoring-stack.yml
```

This order gives useful host metrics before adding cluster metrics or logs.

## Success Criteria

Systemd services:

```text
prometheus.service active on s6-monitoring
grafana-server.service active on s6-monitoring
node-exporter.service active on all VMs
postgres-exporter.service active on s4-db
```

Ports:

```text
s6-monitoring:9090 Prometheus
s6-monitoring:3000 Grafana
all VMs:9100 node-exporter
s4-db:9187 postgres exporter
```

Access:

```text
gw can reach Prometheus and Grafana on s6-monitoring
s6-monitoring can scrape node-exporter on all VMs
s6-monitoring can scrape postgres exporter on s4-db
```

Security:

```text
exporter ports are not public
secrets stay under /home/nexoc/campus-secrets/monitoring
no monitoring passwords or tokens are printed by playbooks
```

