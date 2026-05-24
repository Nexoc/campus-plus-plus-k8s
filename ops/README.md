# Campus++ Ops Automation

This directory contains the Ansible-based operations layer for the Campus++
home Kubernetes lab.

The deployment model:

```text
local pc -> gw -> ansible/ssh/kubectl/helm -> all servers/prod cluster
```

`gw` is the control host. It runs Ansible, `kubectl`, `helm`, and the
`home-gw-runner`. It is not an application workload node. Production workloads
run on `s1-prod`, `s2-prod`, and `s3-prod`.

## Current Ops Status

Available automation:

- connectivity checks across all home-lab hosts
- prod cluster verification from `gw`
- prod database access checks
- Envoy Gateway install/upgrade wrapper for prod
- prod release smoke verification
- `s6-monitoring` bootstrap
- node-exporter install on all VMs
- Prometheus install on `s6-monitoring`
- Grafana install on `s6-monitoring`
- PostgreSQL exporter automation for `s4-db`
- kube-state-metrics manifests and install playbook for dev/prod clusters

Current home-lab monitoring verification should be rerun after the home-only
refactor.

## Inventory Contract

Logical hostnames:

```text
gw
s4-db
s5-dev
s6-monitoring
s1-prod
s2-prod
s3-prod
```

Tracked inventory example:

```text
ops/inventory/home.example.ini
```

Untracked runtime inventory:

```text
ops/inventory/home.local.ini
```

Real IP addresses belong only in `*.local.ini` files on the deployment host.
Local inventory files are ignored by git.

See `ops/inventory/README.md` for the local inventory workflow.

## Secrets And Runtime Files

For the full project runtime file checklist, see `docs/runtime-inputs.md`.

Real secrets are not stored in this repository.

Home dev runtime files:

```text
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
/home/nexoc/campus-secrets/home/db-endpoint.env
```

Home prod runtime files:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

`DB_HOST` remains:

```text
s4-db
```

Inside `campus-dev` and `campus-prod`, `s4-db` is a Kubernetes DNS alias. The
real external database address comes from:

```text
/home/nexoc/campus-secrets/home/db-endpoint.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

## Playbooks

`check-connectivity.yml`

Checks Ansible reachability, hostnames, OS release, and interface addresses on
all inventory hosts. It makes no changes.

`bootstrap-gw.yml`

Prepares `gw` as the control host.

`bootstrap-common.yml`

Installs safe base Linux packages on managed hosts excluding `gw`.

`check-prod-cluster.yml`

Verifies the home prod k3s HA cluster through `/home/nexoc/.kube/prod.yaml`.

`check-db-access.yml`

Verifies PostgreSQL reachability from `s5-dev` and prod nodes, and checks the
Kubernetes `s4-db` alias from `gw`.

`configure-s4-db-access.yml`

Idempotently configures `s4-db` host-level access for PostgreSQL clients.

`install-envoy-prod.yml`

Installs or upgrades Envoy Gateway in the prod cluster using Helm and the prod
kubeconfig.

`verify-prod-release.yml`

Runs repository verification and HTTP smoke checks through `s1-prod:30080`,
`s2-prod:30080`, and `s3-prod:30080` with `Host: home-campus-prod.davl.at`.

Monitoring playbooks:

- `check-monitoring.yml`
- `bootstrap-monitoring.yml`
- `install-node-exporter.yml`
- `install-prometheus.yml`
- `install-grafana.yml`
- `check-monitoring-stack.yml`
- `render-postgres-exporter-env.yml`
- `install-postgres-exporter.yml`
- `install-kube-state-metrics.yml`

## Common Commands

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible all -i ops/inventory/home.local.ini -m ping
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/check-connectivity.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/bootstrap-gw.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/check-prod-cluster.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/check-db-access.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/install-envoy-prod.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/verify-prod-release.yml
```

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/check-monitoring.yml
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/bootstrap-monitoring.yml
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/install-node-exporter.yml
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/install-prometheus.yml
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/install-grafana.yml
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/check-monitoring-stack.yml
```

## Safety Rules

- Do not store `DB_PASSWORD`, `JWT_SECRET`, `GHCR_PULL_TOKEN`, GitHub tokens, or real `.env` secrets in this repository.
- Do not use Ansible to reinstall k3s in this ops layer.
- Do not use Ansible to redeploy the Campus++ app; GitHub Actions remains the release path.
- Keep `gw` as the control host only.
- Keep the stable logical inventory contract for the home lab.
