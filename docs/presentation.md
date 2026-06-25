# Presentation Slides

Screenshot-based presentation summary for Campus++.

## 1. Overview

![Campus++ Home Lab overview](assets/1.png)

## 2. Infrastructure

![Infrastructure: one PC, clear server roles](assets/2.png)

## 3. Kubernetes Environments

![Kubernetes environments: dev and production](assets/3.png)

## 4. Delivery And External HTTPS Access

![Delivery and external HTTPS access](assets/4.png)

## 5. Monitoring, Security, And Final Result

![Monitoring, security, and final result](assets/5.png)

## Runtime Evidence

![GitHub Actions release workflow](assets/github-actions-release.png)

![Campus++ production pods running on k3s](assets/kubernetes-prod-runtime.png)

![Campus VM Overview dashboard](assets/grafana-vm-overview.png)

![Campus Kubernetes Overview dashboard](assets/grafana-kubernetes-overview.png)

![Campus PostgreSQL Overview dashboard](assets/grafana-postgresql-overview.png)


## Requirements Coverage

### MUST

The project provides a working home-lab DevOps platform.

Implemented:

- separated dev and production environments
- k3s-based Kubernetes deployments
- external PostgreSQL integration
- GitHub Actions CI/CD
- public HTTPS access for dev and prod
- Prometheus and Grafana monitoring

### SHOULD

The platform is structured, understandable, and operable.

Implemented:

- clear VM and server roles
- Ansible-based infrastructure management
- separated Kubernetes namespaces and overlays
- multi-node production cluster
- protected external Grafana access
- private Prometheus and exporter access

### COULD

Additional production-style features are included.

Implemented:

- dedicated external Grafana domain
- custom HTTPS routing for Grafana
- dashboards for VMs, Kubernetes, and PostgreSQL
- firewall restrictions for monitoring endpoints
- documented and verified baseline state
