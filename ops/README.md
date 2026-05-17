# Campus++ Ops Automation

This directory contains the Ansible-based operations layer for the Campus++ Kubernetes migration project.

The deployment model stays the same:

```text
local pc -> gw -> ansible/ssh/kubectl/helm -> all servers/prod cluster
```

`gw` is the control host. It runs Ansible, `kubectl`, and `helm`, but it is not a workload node. Production workloads run only on the prod k3s HA nodes: `s1-prod`, `s2-prod`, and `s3-prod`.

## Current Ops Status

Completed:

- connectivity checks across all lab hosts
- PROD cluster verification from `gw`
- PROD database access checks
- Envoy Gateway install/upgrade wrapper for PROD
- PROD release smoke verification
- `s6-monitoring` bootstrap
- node-exporter on all 7 lab VMs
- Prometheus on `s6-monitoring`
- Grafana on `s6-monitoring`
- Grafana datasource `Campus Prometheus` with UID `campus-prometheus`
- Grafana dashboards `Campus VM Overview`, `Campus PostgreSQL Overview`, and `Campus Kubernetes Overview`
- central monitoring stack verification
- PostgreSQL exporter automation for `s4-db`
- kube-state-metrics manifests and install playbook for dev/prod clusters

Next monitoring work:

- alerting and logs

## Inventory Contract

The logical hostnames stay stable across environments:

```text
gw
s4-db
s5-dev
s6-monitoring
s1-prod
s2-prod
s3-prod
```

Tracked inventory examples:

```text
ops/inventory/lab.example.ini
ops/inventory/university.example.ini
```

Untracked runtime inventories:

```text
ops/inventory/lab.local.ini
ops/inventory/university.local.ini
```

Real IP addresses belong only in `*.local.ini` files on the deployment host.
The local inventory files are ignored by git. Only logical hostnames and
example placeholders are committed.

Only host addresses should change between lab and university environments.
Kubernetes manifests and GitHub workflows should continue to use the same
deployment contract.

See `ops/inventory/README.md` for the local inventory workflow.

## Secrets And Runtime Files

For the full project runtime file checklist, see `docs/runtime-inputs.md`.

Real secrets are not stored in this repository.

Production runtime files live on `gw` under:

```text
/home/nexoc/campus-secrets/prod
```

Expected files:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

`DB_HOST` remains:

```text
s4-db
```

Inside `campus-prod`, `s4-db` is a Kubernetes DNS alias. The real external database address is environment-specific and comes from:

```text
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

Example format:

```text
DB_ENDPOINT_ADDRESS=<DB_VM_IP_OR_ROUTABLE_ADDRESS>
DB_ENDPOINT_PORT=5432
```

The lab value and future university value are runtime configuration, not committed Kubernetes manifests.

## Playbooks

`check-connectivity.yml`

Checks Ansible reachability, hostnames, OS release, and interface addresses on all inventory hosts. It makes no changes.

`bootstrap-gw.yml`

Prepares `gw` as the control host. It installs base tools, checks `kubectl`, checks `helm`, checks `/home/nexoc/.kube/prod.yaml`, and reports whether required runtime files exist without printing values.

`bootstrap-common.yml`

Installs safe base Linux packages on managed hosts excluding `gw`. It does not reboot and does not change network configuration.

`check-prod-cluster.yml`

Runs on `gw` and verifies the prod k3s HA cluster through `/home/nexoc/.kube/prod.yaml`: prod nodes, namespace, deployments, importer job completion when the Job still exists, Gateway API resources, Envoy NodePort, and `s4-db` Service/EndpointSlice.

`check-db-access.yml`

Verifies PostgreSQL reachability from `s5-dev` and prod nodes, checks PostgreSQL service/listener/firewall/`pg_hba.conf` on `s4-db`, and checks the Kubernetes `s4-db` alias from `gw`.

`configure-s4-db-access.yml`

Idempotently configures `s4-db` host-level access for PostgreSQL clients. By default, allowed clients are derived from the `dev` and `prod` inventory host addresses. It adds allow rules before the PostgreSQL drop rule, keeps the default drop rule for port `5432`, updates `pg_hba.conf`, and reloads PostgreSQL. It does not open PostgreSQL to `0.0.0.0/0`.

`install-envoy-prod.yml`

Runs from `gw` and installs or upgrades Envoy Gateway in the prod cluster using Helm and the prod kubeconfig. It does not install Envoy Gateway as a local service on `gw`.

`verify-prod-release.yml`

Runs the existing repository verification script and performs HTTP smoke checks through `s1-prod:30080`, `s2-prod:30080`, and `s3-prod:30080` with `Host: campus-prod.davl.at`.

`check-monitoring.yml`

Runs preflight checks on `s6-monitoring` before monitoring installation. It verifies basic VM capacity, systemd availability, network interfaces, and whether planned monitoring ports are free. It makes no changes.

`bootstrap-monitoring.yml`

Prepares `s6-monitoring` as the central monitoring VM. It installs safe base packages and creates monitoring/runtime directories, but does not install Prometheus, Grafana, Alertmanager, Loki, or exporters.

`install-node-exporter.yml`

Installs `prometheus-node-exporter` on every VM through the Debian package, enables the systemd service, allows loopback access, restricts external port `9100` to `s6-monitoring`, checks the local metrics endpoint, and verifies from `s6-monitoring` that each exporter is reachable.

`install-prometheus.yml`

Installs Prometheus on `s6-monitoring` through the Debian package, renders node-exporter, postgres-exporter, and kube-state-metrics scrape targets from inventory, restricts external port `9090` to `gw`, and verifies readiness from both `s6-monitoring` and `gw`. It is not the full monitoring health gate; strict target health checks live in `check-monitoring-stack.yml`.

`install-grafana.yml`

Installs Grafana on `s6-monitoring` through the official Grafana APT repository, provisions the local Prometheus datasource and the Campus++ dashboards, restricts external port `3000` to `gw`, and verifies Grafana health from both `s6-monitoring` and `gw`.

`check-monitoring-stack.yml`

Verifies the central monitoring stack without requiring Grafana credentials. It checks Prometheus readiness and targets, node-exporter target health, postgres-exporter target health, kube-state-metrics dev/prod target health, Grafana health, the Grafana Prometheus datasource provisioning file, and the provisioned dashboard files.

The monitoring stack has 9 targets after PostgreSQL exporter is installed:
7 node-exporter targets, 1 Prometheus self-target, and 1 postgres-exporter
target. After kube-state-metrics is installed in dev/prod and Prometheus is
re-rendered, the expected target count is 11.

`render-postgres-exporter-env.yml`

Runs from `gw`, reads existing PROD database runtime inputs without printing
secret values, and creates `/home/nexoc/campus-secrets/monitoring/postgres-exporter.env`
on `s4-db` for the PostgreSQL exporter service. The exporter database host and
port prefer `/home/nexoc/campus-secrets/prod/db-endpoint.env`, then tracked
PROD config, with `s4-db` as the stable hostname fallback.

`install-postgres-exporter.yml`

Installs `prometheus-postgres-exporter` on `s4-db`, loads the runtime DSN from
`/home/nexoc/campus-secrets/monitoring/postgres-exporter.env`, restricts port
`9187` to loopback and `s6-monitoring`, and verifies that `s6-monitoring` can
reach the exporter metrics endpoint.

`install-kube-state-metrics.yml`

Installs kube-state-metrics in the dev and prod k3s clusters from the tracked
Kustomize manifests under `deploy/monitoring/kube-state-metrics`. Dev exposes a
restricted NodePort on `30091` from `s5-dev`. Prod exposes a restricted NodePort
on `30092` from the prod nodes and uses the first prod host as the default
Prometheus scrape target unless inventory overrides it. Firewall rules allow
loopback and `s6-monitoring` only.

## Design Docs

- [Monitoring Design](docs/monitoring-design.md)
- [Monitoring Runtime Model](docs/monitoring-runtime.md)

## Common Commands

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible all -i ops/inventory/lab.local.ini -m ping
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-connectivity.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/bootstrap-gw.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/bootstrap-common.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-prod-cluster.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-prod-cluster.yml -e expected_tag=v0.1.1
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-db-access.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/configure-s4-db-access.yml
```

Run `configure-s4-db-access.yml` only when host-level PostgreSQL access needs to be repaired or prepared for a new environment.

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/install-envoy-prod.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/verify-prod-release.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-monitoring.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/bootstrap-monitoring.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/install-node-exporter.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/install-prometheus.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/install-grafana.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-monitoring-stack.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/render-postgres-exporter-env.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/install-postgres-exporter.yml
```

Install kube-state-metrics before enabling its Prometheus scrape jobs:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/install-kube-state-metrics.yml
```

After adding or changing postgres exporter or kube-state-metrics scrape config,
re-render Prometheus before running the full stack check:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/install-prometheus.yml
ansible-playbook -i ops/inventory/lab.local.ini ops/playbooks/check-monitoring-stack.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash ops/scripts/ansible-ping-all.sh
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash ops/scripts/verify-prod-from-gw.sh
```

## Safety Rules

- Do not store `DB_PASSWORD`, `JWT_SECRET`, `GHCR_PULL_TOKEN`, GitHub tokens, or real `.env` secrets in this repository.
- Do not use Ansible to reinstall k3s in this ops layer.
- Do not use Ansible to redeploy the Campus++ app; GitHub Actions remains the release path.
- Keep `gw` as the control host only.
- Keep the same logical inventory contract for lab and university environments.
