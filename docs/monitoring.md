# Monitoring

Monitoring is a dedicated portfolio feature of the Campus++ home lab.

## Components

- Prometheus runs on `s6-monitoring`.
- Grafana runs on `s6-monitoring`.
- node-exporter runs on all lab VMs.
- postgres-exporter monitors PostgreSQL on `s4-db`.
- kube-state-metrics runs for dev and prod Kubernetes clusters.

## Dashboards

Grafana dashboards:

- Campus VM Overview
- Campus PostgreSQL Overview
- Campus Kubernetes Overview

Grafana external URL:

```text
https://home-grafana.davl.at
```

Access is protected. Credentials are not stored in this repository.

## Exposure Model

- Grafana is the only external monitoring entry point.
- Grafana access is protected.
- Prometheus is intentionally not exposed publicly.
- node-exporter endpoints are not exposed publicly.
- postgres-exporter is not exposed publicly.

## Verified Signals

- Prometheus is healthy on `s6-monitoring`.
- Grafana is healthy on `s6-monitoring`.
- node-exporter targets are up on all lab VMs.
- postgres-exporter is up for `s4-db`.
- kube-state-metrics is up for dev and prod.
- Grafana dashboards display VM, PostgreSQL, and Kubernetes metrics.

## Screenshot Placeholders

Screenshots will be added later:

- `docs/assets/grafana-vm-overview.png`
- `docs/assets/grafana-kubernetes-overview.png`
- `docs/assets/grafana-postgresql-overview.png`
