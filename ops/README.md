# Campus++ Ops Automation

This directory contains the Ansible-based operations layer for the Campus++ Kubernetes migration project.

The deployment model stays the same:

```text
local pc -> gw -> ansible/ssh/kubectl/helm -> all servers/prod cluster
```

`gw` is the control host. It runs Ansible, `kubectl`, and `helm`, but it is not a workload node. Production workloads run only on the prod k3s HA nodes: `s1-prod`, `s2-prod`, and `s3-prod`.

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

The lab inventory is stored in:

```text
ops/inventory/lab.ini
```

The university template is stored in:

```text
ops/inventory/university.example.ini
```

Only host addresses should change between lab and university environments. Kubernetes manifests and GitHub workflows should continue to use the same deployment contract.

## Secrets And Runtime Files

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

Installs `prometheus-node-exporter` on every VM through the Debian package, enables the systemd service, restricts port `9100` to `s6-monitoring`, checks the local metrics endpoint, and verifies from `s6-monitoring` that each exporter is reachable.

## Design Docs

- [Monitoring Design](docs/monitoring-design.md)
- [Monitoring Runtime Model](docs/monitoring-runtime.md)

## Common Commands

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible all -i ops/inventory/lab.ini -m ping
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/check-connectivity.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/bootstrap-gw.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/bootstrap-common.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/check-prod-cluster.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/check-prod-cluster.yml -e expected_tag=v0.1.1
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/check-db-access.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/configure-s4-db-access.yml
```

Run `configure-s4-db-access.yml` only when host-level PostgreSQL access needs to be repaired or prepared for a new environment.

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/install-envoy-prod.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/verify-prod-release.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/check-monitoring.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/bootstrap-monitoring.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/lab.ini ops/playbooks/install-node-exporter.yml
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
