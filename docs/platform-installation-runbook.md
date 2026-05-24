# Campus++ Home Lab Installation Runbook

This runbook describes the order for bringing up Campus++ on the active target:

```text
home lab on one physical PC with VM clones
```

It separates repository-owned automation from runtime-only setup. The repo does
not store real secrets, runner tokens, kubeconfigs, passwords, or real VM
addresses.

## Scope

Repository-owned automation covers:

- application Kubernetes manifests and overlays
- Envoy Gateway install/upgrade wrapper for prod
- generated prod `s4-db` Kubernetes Service and EndpointSlice
- Ansible host checks and bootstrap tasks
- database access checks
- monitoring installation and verification
- dev and prod release verification

Runtime-only setup covers:

- real inventory addresses
- real secret files
- GitHub runner registration token
- GitHub repository/environment secrets
- kubeconfig files
- initial k3s cluster installation
- public DNS/TLS edge configuration

## Target End State

```text
home-dev-* tag -> home-gw-runner -> dev kubeconfig -> s5-dev -> campus-dev -> Envoy NodePort 30080
home-v* tag -> home-production approval -> home-gw-runner -> prod kubeconfig -> campus-prod -> Envoy NodePort 30080
s4-db -> external PostgreSQL through stable runtime alias
s6-monitoring -> Prometheus, Grafana, exporters, dashboards
```

Canonical hostnames:

```text
home dev      home-campus-dev.davl.at
home prod     home-campus-prod.davl.at
home grafana  home-grafana.davl.at
```

Stable VM roles:

```text
gw
s4-db
s5-dev
s6-monitoring
s1-prod
s2-prod
s3-prod
```

## Runtime Automation Wrappers

The executable source of truth for repeatable prod bootstrap/recovery and
monitoring automation is:

```text
ops/scripts/runtime/
```

Home wrapper defaults should be:

```text
ANSIBLE_INVENTORY=ops/inventory/home.local.ini
KUBECONFIG=/home/nexoc/.kube/prod.yaml
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets
PROD_NAMESPACE=campus-prod
EXPECTED_NODEPORT=30080
EXPECTED_HOST=home-campus-prod.davl.at
```

Wrapper preconditions:

- repo exists on `gw`
- `ops/inventory/home.local.ini` exists
- `gw` has ansible, kubectl, helm, envsubst, curl
- prod k3s exists if running prod/envoy/deploy checks
- `/home/nexoc/.kube/prod.yaml` exists if running prod/envoy/deploy checks
- runtime files exist before rendering/applying prod

Recommended wrapper order:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

bash ops/scripts/runtime/00-preflight.sh
bash ops/scripts/runtime/01-check-runtime-files.sh
bash ops/scripts/runtime/02-install-envoy-prod.sh
TAG=home-vX.Y.Z bash ops/scripts/runtime/03-render-prod.sh
TAG=home-vX.Y.Z CONFIRM_PROD_APPLY=apply-prod bash ops/scripts/runtime/04-apply-prod.sh
TAG=home-vX.Y.Z bash ops/scripts/runtime/05-verify-prod.sh
bash ops/scripts/runtime/06-install-monitoring.sh
```

Wrappers intentionally do not:

- install or reinstall k3s
- create real secrets
- register GitHub runners
- run normal dev application deploys
- approve GitHub production deployments
- choose a release tag
- run destructive cleanup on prod nodes

For normal production releases, prefer the GitHub Actions `home-v*` workflow
with `home-production` approval. `04-apply-prod.sh` is only for controlled
bootstrap or recovery situations.

## Access Model

All server work starts from `gw`.

From a local machine:

```bash
# server: local pc
ssh nexoc@<gw_address>
```

From `gw`, reach the logical hosts:

```bash
# server: gw
ssh nexoc@s4-db
ssh nexoc@s5-dev
ssh nexoc@s6-monitoring
ssh nexoc@s1-prod
ssh nexoc@s2-prod
ssh nexoc@s3-prod
```

All Ansible, kubectl, and Helm commands in this runbook are executed from `gw`
unless a step explicitly says otherwise.

## Phase 1 - Prepare gw Control Host

`gw` must have:

- git
- curl
- ca-certificates
- ansible / ansible-playbook
- kubectl
- helm
- envsubst
- bash, sed, awk, grep

Verify tools:

```bash
# server: gw
command -v git
command -v ansible
command -v ansible-playbook
command -v kubectl
command -v helm
command -v envsubst

git --version
ansible --version
ansible-playbook --version
kubectl version --client
helm version
```

## Phase 2 - Create Home Inventory

Tracked example:

```text
ops/inventory/home.example.ini
```

Runtime inventory:

```text
ops/inventory/home.local.ini
```

Create it on `gw`:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
cp ops/inventory/home.example.ini ops/inventory/home.local.ini
nano ops/inventory/home.local.ini
chmod 600 ops/inventory/home.local.ini
```

Required groups:

```text
[gw]
[db]
[dev]
[monitoring]
[prod]
[k3s_prod:children]
```

Check reachability:

```bash
# server: gw
ansible all -i ops/inventory/home.local.ini -m ping
ansible-playbook -i ops/inventory/home.local.ini ops/playbooks/check-connectivity.yml
```

## Phase 3 - Kubeconfigs

Expected files on `gw`:

```text
/home/nexoc/.kube/dev.yaml
/home/nexoc/.kube/prod.yaml
```

Verify:

```bash
# server: gw
ls -la /home/nexoc/.kube
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get nodes -o wide
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get nodes -o wide
```

## Phase 4 - Runtime Secrets

Home dev:

```text
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
```

Home prod:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

Create directories:

```bash
# server: gw
mkdir -p /home/nexoc/campus-secrets/home /home/nexoc/campus-secrets/prod
chmod 700 /home/nexoc/campus-secrets /home/nexoc/campus-secrets/home /home/nexoc/campus-secrets/prod
chmod 600 /home/nexoc/campus-secrets/home/*.env /home/nexoc/campus-secrets/prod/*.env
```

Do not print secret contents.

## Phase 5 - Runner

On `gw`, keep one active GitHub runner:

```text
runner name: home-gw-runner
labels: home, gw, deploy
```

GitHub Actions also matches the standard self-hosted labels:

```text
self-hosted, Linux, X64, home, gw, deploy
```

The runner must be able to read:

```text
/home/nexoc/.kube/dev.yaml
/home/nexoc/.kube/prod.yaml
/home/nexoc/campus-secrets/home/*
/home/nexoc/campus-secrets/prod/*
```

## Phase 6 - Dev Release

Create a test dev tag:

```bash
# server: local pc
git tag home-dev-test-YYYYMMDD-HHMMSS
git push origin home-dev-test-YYYYMMDD-HHMMSS
```

Expected:

```text
home-dev-* -> home-gw-runner -> /home/nexoc/.kube/dev.yaml -> s5-dev -> campus-dev
```

Manual verification from `gw`:

```bash
# server: gw
curl -I -H "Host: home-campus-dev.davl.at" http://s5-dev:30080/
```

## Phase 7 - Prod Render Gate

Render without applying:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
CAMPUS_HTTPROUTE_HOSTNAME=home-campus-prod.davl.at \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag home-v-render-test \
  --render-only \
  --manifest-out /tmp/campus-prod-rendered.yaml
```

Check without printing secrets:

```bash
# server: gw
grep -nE "namespace: campus-prod|home-campus-prod.davl.at|nodePort: 30080|imagePullSecrets|ghcr-pull|kind: Service|kind: EndpointSlice|name: s4-db" /tmp/campus-prod-rendered.yaml -A3 -B3
```

## Phase 8 - Prod Release

Create a prod tag only after runner, secrets, kubeconfig, GHCR credentials, and
`home-production` approval are ready:

```bash
# server: local pc
git tag home-vX.Y.Z
git push origin home-vX.Y.Z
```

Expected:

```text
home-v* -> home-production approval -> home-gw-runner -> /home/nexoc/.kube/prod.yaml -> campus-prod
```

Manual smoke checks from `gw`:

```bash
# server: gw
curl -I --max-time 10 -H "Host: home-campus-prod.davl.at" http://s1-prod:30080 || true
curl -I --max-time 10 -H "Host: home-campus-prod.davl.at" http://s2-prod:30080 || true
curl -I --max-time 10 -H "Host: home-campus-prod.davl.at" http://s3-prod:30080 || true
```

## Phase 9 - Database

`s4-db` stays the PostgreSQL VM.

Dev:

```text
DB_HOST=s4-db
```

Prod:

```text
DB_HOST=s4-db
real endpoint comes from /home/nexoc/campus-secrets/prod/db-endpoint.env
```

If the prod cluster reaches `s4-db` through generated Service/EndpointSlice,
keep that model.

## Phase 10 - Monitoring

`s6-monitoring` stays the central monitoring VM.

Expected Grafana hostname:

```text
home-grafana.davl.at
```

Commands:

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

Monitoring baseline has been visually verified in Grafana:

```text
VM metrics are displayed for all 7 lab VMs.
PostgreSQL metrics are displayed for s4-db.
Kubernetes metrics are displayed for dev and prod through kube-state-metrics.
Prometheus targets are up.
```

## Acceptance Criteria

```text
home-dev-* deploys to s5-dev / campus-dev
home-v* deploys to s1-prod/s2-prod/s3-prod / campus-prod
gw has one active runner: home-gw-runner
s4-db remains the database VM
s6-monitoring remains the monitoring VM
README and docs describe the home VM lab
```
