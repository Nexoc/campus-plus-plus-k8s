# Campus++ Final Platform Status

## Executive Summary

Campus++ is now documented as a home Kubernetes lab on one physical PC with VM
clones. The active target is home-only.

The infrastructure roles stay:

```text
gw             192.168.56.10  gateway / runner / ansible / edge
s4-db          192.168.56.20  PostgreSQL
s6-monitoring  192.168.56.30  Prometheus + Grafana
s5-dev         192.168.56.40  dev k3s
s1-prod        192.168.56.51  prod k3s node 1
s2-prod        192.168.56.52  prod k3s node 2
s3-prod        192.168.56.53  prod k3s node 3
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
-> external HTTP 301 / HTTPS 200 verified
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
-> external HTTP 301 / HTTPS 200 verified
```

Hostnames:

```text
home dev      home-campus-dev.davl.at
home prod     home-campus-prod.davl.at
home grafana  home-grafana.davl.at
```

## Public Edge Status

DNS for `home-campus-dev.davl.at` and `home-campus-prod.davl.at` points to the
public VPS:

```text
130.185.118.138
```

Verified external access:

```text
http://home-campus-dev.davl.at    -> 301
https://home-campus-dev.davl.at   -> 200
http://home-campus-prod.davl.at   -> 301
https://home-campus-prod.davl.at  -> 200
```

VPS routing state:

```text
nginx config: /etc/nginx/sites-available/home-campus-routing.conf
nginx backup: /root/nginx-backups/nginx-before-home-campus-2026-05-24-132601.tar.gz
certificate: /etc/letsencrypt/live/home-campus/fullchain.pem
certificate key path: /etc/letsencrypt/live/home-campus/privkey.pem
certificate names: home-campus-dev.davl.at, home-campus-prod.davl.at
certificate method: certbot certonly --webroot
```

The certificate was not obtained with `certbot --nginx`.

Traffic path:

```text
internet -> DNS -> VPS nginx HTTPS -> WireGuard -> home VM network
-> k3s NodePort 30080 -> Envoy Gateway -> Campus++ app
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

Monitoring visual verification completed on 2026-05-24.

Grafana dashboards are visible and backed by live Prometheus data:

- Prometheus on `s6-monitoring` is healthy
- Grafana on `s6-monitoring` is healthy
- `Campus VM Overview` displays node-exporter metrics for all 7 home-lab VMs
- `Campus PostgreSQL Overview` displays postgres-exporter metrics for `s4-db`
- `Campus Kubernetes Overview` displays kube-state-metrics data for dev and prod
- Prometheus targets are up for VM, PostgreSQL, and Kubernetes metrics

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
- public DNS and VPS edge configuration

## Remaining Optional Improvements

- Grafana external access is not exposed yet
- Prometheus should not be public
- security hardening is intentionally postponed as the final step
- possible later: Grafana protected access, rate limits, basic auth for dev, default deny server, fail2ban, firewall review
- add RBAC-limited kubeconfigs for deployment runners
- add Alertmanager and Prometheus alert rules
- add Loki or Grafana Alloy log collection
- add backup and restore drills for PostgreSQL and k3s state
