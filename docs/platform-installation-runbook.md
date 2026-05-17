# campus++ platform installation runbook

this runbook describes the order for bringing up the campus++ platform on a fresh lab or university-style infrastructure.

it is intentionally explicit about what is managed by this repository and what is a runtime-only responsibility.

## scope

repository-owned automation covers:

* application kubernetes manifests and overlays
* envoy gateway install/upgrade wrapper for prod
* database access checks and safe host-level access configuration
* generated prod `s4-db` kubernetes service and endpointslice
* ansible host checks and bootstrap tasks
* monitoring installation and verification
* dev and prod release verification

runtime-only setup covers:

* real inventory addresses
* real secret files
* github runner registration tokens
* github repository/environment secrets
* kubeconfig files
* initial k3s cluster installation
* public dns/tls edge configuration

this repository does not store real secrets, tokens, passwords, or infrastructure addresses as kubernetes or workflow architecture contracts.

## target end state

expected final platform state:

```text
uni-dev-* tag -> uni gw control runner -> dev kubeconfig -> s5-dev -> campus-dev -> envoy nodeport 30080
uni-v* tag -> github production approval -> uni gw control runner -> prod kubeconfig -> campus-prod -> envoy nodeport 30080
home-dev-* tag -> home gw control runner -> dev kubeconfig -> home s5-dev -> campus-dev -> envoy nodeport 30080
home-v* tag -> home github production approval -> home gw control runner -> prod kubeconfig -> campus-prod -> envoy nodeport 30080
s4-db -> external postgresql through stable runtime alias
s6-monitoring -> prometheus, grafana, exporters, dashboards
```

canonical hostnames:

```text
uni dev:      campus-dev.10-123-127-29.sslip.io
uni prod:     campus-prod.10-123-127-29.sslip.io
uni grafana:  grafana.10-123-127-29.sslip.io
home dev:     home-campus-dev.davl.at
home prod:    home-campus-prod.davl.at
home grafana: home-grafana.davl.at
```

## runtime automation wrappers

The step-by-step phases below explain the full installation context. The
executable source of truth for repeatable PROD bootstrap/recovery and monitoring
automation is:

```text
ops/scripts/runtime/
```

The wrapper defaults are defined in `ops/scripts/runtime/common.sh`:

```text
ANSIBLE_INVENTORY=ops/inventory/uni.local.ini
KUBECONFIG=/home/nexoc/.kube/prod.yaml
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets
PROD_NAMESPACE=campus-prod
EXPECTED_NODEPORT=30080
EXPECTED_HOST=campus-prod.10-123-127-29.sslip.io
```

wrapper preconditions:

* repo exists on `gw`
* `ops/inventory/uni.local.ini` exists
* `gw` has ansible, kubectl, helm, envsubst, curl
* prod k3s exists if running prod/envoy/deploy checks
* `/home/nexoc/.kube/prod.yaml` exists if running prod/envoy/deploy checks
* runtime files exist before rendering/applying prod

recommended wrapper order after the runtime-only prerequisites are done:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

bash ops/scripts/runtime/00-preflight.sh
bash ops/scripts/runtime/01-check-runtime-files.sh
bash ops/scripts/runtime/02-install-envoy-prod.sh
TAG=uni-vX.Y.Z bash ops/scripts/runtime/03-render-prod.sh
TAG=uni-vX.Y.Z CONFIRM_PROD_APPLY=apply-prod bash ops/scripts/runtime/04-apply-prod.sh
TAG=uni-vX.Y.Z bash ops/scripts/runtime/05-verify-prod.sh
bash ops/scripts/runtime/06-install-monitoring.sh
```

wrapper map:

```text
00-preflight.sh              checks gw tools, inventory, ssh/ansible reachability, optional kubeconfig
01-check-runtime-files.sh    checks prod runtime files and kubeconfig without printing values
02-install-envoy-prod.sh     creates campus-prod namespace and installs Envoy Gateway
03-render-prod.sh            renders prod overlay and runs kubectl server dry-run
04-apply-prod.sh             applies prod overlay only with explicit CONFIRM_PROD_APPLY=apply-prod
05-verify-prod.sh            runs prod verification and node smoke checks
06-install-monitoring.sh     installs/reconciles monitoring and runs check-monitoring-stack.yml
```

wrappers intentionally do not:

* install or reinstall k3s
* create real secrets
* register github runners
* run normal DEV application deploys
* approve github production deployments
* choose a release tag for you
* run destructive cleanup on prod nodes

for normal production releases, prefer the github actions `uni-v*` or `home-v*` workflows with environment approval. `04-apply-prod.sh` is only for controlled bootstrap or recovery situations.

to use another inventory or kubeconfig:

```bash
# server: gw
ANSIBLE_INVENTORY=ops/inventory/lab.local.ini \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
bash ops/scripts/runtime/00-preflight.sh
```

## access model

all server work starts from `gw`.

from a local machine:

```bash
# server: local pc
ssh nexoc@<gw_address>
```

from `gw`, reach the logical hosts:

```bash
# server: gw
ssh nexoc@s4-db
ssh nexoc@s5-dev
ssh nexoc@s6-monitoring
ssh nexoc@s1-prod
ssh nexoc@s2-prod
ssh nexoc@s3-prod
```

all ansible, kubectl, and helm commands in this runbook are executed from `gw`, unless a step explicitly says otherwise.

## phase 0: clone repo on gw

```bash
# server: gw
cd /home/nexoc
git clone https://github.com/Nexoc/campus-plus-plus-k8s.git
cd /home/nexoc/campus-plus-plus-k8s
git checkout main
git pull --ff-only
git status --short
```

if the repo already exists:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
git checkout main
git pull --ff-only
git status --short
```

## phase 1: prepare gw control host

`gw` must have the local tools used by the ops layer:

* git
* curl
* ca-certificates
* ansible / ansible-playbook
* kubectl
* helm
* envsubst
* bash, sed, awk, grep

install base packages first:

```bash
# server: gw
sudo apt update
sudo apt install -y git curl ca-certificates ansible-core gettext-base
```

install `kubectl` and `helm` using the method appropriate for the target environment.

after installation, verify the binaries:

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

if `helm` is missing, `ops/playbooks/install-envoy-prod.yml` fails before touching the cluster. fix the `gw` toolchain first, then rerun the playbook.

## phase 2: create runtime inventory

tracked inventory files are examples only:

```text
ops/inventory/lab.example.ini
ops/inventory/university.example.ini
```

create an ignored local inventory for the current environment.

for a university environment:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
cp ops/inventory/university.example.ini ops/inventory/uni.local.ini
chmod 600 ops/inventory/uni.local.ini
nano ops/inventory/uni.local.ini
```

example university inventory:

```ini
[gw]
gw ansible_host=127.0.0.1 ansible_connection=local monitoring_scrape_host=192.168.50.1 ansible_become=false

[db]
s4-db ansible_host=192.168.50.4 monitoring_scrape_host=192.168.50.4

[dev]
s5-dev ansible_host=192.168.50.5 monitoring_scrape_host=192.168.50.5

[monitoring]
s6-monitoring ansible_host=192.168.50.6 monitoring_scrape_host=192.168.50.6

[prod]
s1-prod ansible_host=192.168.50.10 monitoring_scrape_host=192.168.50.10
s2-prod ansible_host=192.168.50.2  monitoring_scrape_host=192.168.50.2
s3-prod ansible_host=192.168.50.3  monitoring_scrape_host=192.168.50.3

[k3s_prod:children]
prod

[all:vars]
ansible_user=nexoc
ansible_become=true
ansible_ssh_common_args='-o StrictHostKeyChecking=accept-new'
ansible_python_interpreter=/usr/bin/python3
```

note: `ansible_become=false` for `gw` is important because repo playbooks use local `kubectl`, `helm`, and kubeconfig files from the `nexoc` user context.

verify inventory parsing and ssh reachability:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-inventory -i ops/inventory/uni.local.ini --graph
ansible all -i ops/inventory/uni.local.ini -m ping
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/check-connectivity.yml
```

wrapper equivalent after `uni.local.ini` exists:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash ops/scripts/runtime/00-preflight.sh
```

## phase 3: prepare passwordless sudo for ansible

ansible needs sudo rights on all managed hosts.

verify:

```bash
# server: gw
for h in s1-prod s2-prod s3-prod s4-db s5-dev s6-monitoring; do
  echo "== $h =="
  ssh nexoc@"$h" 'hostname; sudo -n true && echo "sudo passwordless: yes" || echo "sudo passwordless: no"'
done

sudo -n true && echo "gw sudo passwordless: yes" || echo "gw sudo passwordless: no"
```

if needed, enable passwordless sudo for `nexoc` on a host:

```bash
# server: <target host>
echo "nexoc ALL=(ALL:ALL) NOPASSWD:ALL" | sudo tee /etc/sudoers.d/90-nexoc-nopasswd >/dev/null
sudo chmod 440 /etc/sudoers.d/90-nexoc-nopasswd
sudo visudo -cf /etc/sudoers.d/90-nexoc-nopasswd
```

## phase 4: prepare runtime files

runtime files are not committed. the full contract is documented in:

* `docs/runtime-inputs.md`

create the base directory structure on `gw` for prod:

```bash
# server: gw
mkdir -p /home/nexoc/campus-secrets/prod
chmod 700 /home/nexoc/campus-secrets /home/nexoc/campus-secrets/prod
```

required prod files on `gw`:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

expected keys:

```text
# /home/nexoc/campus-secrets/prod/db-secrets.env
DB_USERNAME=<real value>
DB_PASSWORD=<real value>

# /home/nexoc/campus-secrets/prod/auth-secrets.env
JWT_SECRET=<real value>
JWT_EXPIRATION=86400000

# /home/nexoc/campus-secrets/prod/db-endpoint.env
DB_ENDPOINT_ADDRESS=<real db host or ip>
DB_ENDPOINT_PORT=5432
```

for the university runtime, the db endpoint is usually:

```text
DB_ENDPOINT_ADDRESS=192.168.50.4
DB_ENDPOINT_PORT=5432
```

do not print real secret contents in tickets, chat, logs, or commits.

check only existence, permissions, keys, and value lengths:

```bash
# server: gw
for f in \
  /home/nexoc/campus-secrets/prod/db-secrets.env \
  /home/nexoc/campus-secrets/prod/auth-secrets.env \
  /home/nexoc/campus-secrets/prod/db-endpoint.env
do
  echo "== $f =="
  test -f "$f" && ls -l "$f"
  awk -F= '
    /^[[:space:]]*#/ || /^[[:space:]]*$/ { next }
    { print $1 ": length=" length($2) }
  ' "$f"
done
```

wrapper equivalent after prod runtime files and kubeconfig exist:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash ops/scripts/runtime/01-check-runtime-files.sh
```

## phase 5: prepare k3s clusters

this repository assumes the k3s clusters exist before application deployment.

expected clusters:

```text
s5-dev -> single-node dev k3s
s1-prod/s2-prod/s3-prod -> prod k3s ha cluster
```

if the cluster does not exist yet, install k3s through a runtime-only process outside this repo. do not add this installer to the repo-owned ops layer unless the repository design is intentionally changed.

verify dev:

```bash
# server: gw
ssh nexoc@s5-dev 'kubectl get nodes -o wide'
```

verify prod from a prod node:

```bash
# server: gw
ssh nexoc@s1-prod 'sudo k3s kubectl get nodes -o wide'
```

expected prod result:

```text
s1-prod Ready control-plane,etcd
s2-prod Ready control-plane,etcd
s3-prod Ready control-plane,etcd
```

## phase 6: prepare kubeconfigs on gw

DEV and PROD deployments are controlled from the environment `gw` runner.

DEV operations from this repo use:

```text
/home/nexoc/.kube/dev.yaml
```

PROD operations from this repo use:

```text
/home/nexoc/.kube/prod.yaml
```

after k3s is installed, place the dev and prod kubeconfigs on `gw` and make sure they point at api endpoints reachable from `gw`.

verify:

```bash
# server: gw
test -f /home/nexoc/.kube/dev.yaml
test -f /home/nexoc/.kube/prod.yaml
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get nodes -o wide
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get nodes -o wide
```

important: when running prod verification playbooks, pass `KUBECONFIG` explicitly if the playbook/script does not set it internally:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

KUBECONFIG=/home/nexoc/.kube/prod.yaml \
ansible-playbook \
  -i ops/inventory/uni.local.ini \
  ops/playbooks/verify-prod-release.yml
```

without explicit `KUBECONFIG`, `kubectl` may fall back to `localhost:8080`.

## phase 7: bootstrap hosts with ansible

run common host bootstrap only after connectivity works:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/bootstrap-common.yml
```

check `gw` as the control host:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/bootstrap-gw.yml
```

if `bootstrap-gw.yml` fails on `helm`, install helm on `gw`, then rerun the playbook before continuing.

## phase 8: configure and check database access

the application uses stable database host:

```text
DB_HOST=s4-db
```

prod kubernetes gets the real endpoint from:

```text
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

configure host-level database access if needed:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/configure-s4-db-access.yml
```

verify database access:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/check-db-access.yml
```

do not run application deployment until this check is clean.

## phase 9: install envoy gateway in prod

first verify `helm` and prod kubeconfig:

```bash
# server: gw
helm version
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get nodes -o wide
```

create the prod namespace before installing envoy, because the prod envoy values can reference resources in `campus-prod`:

```bash
# server: gw
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl create namespace campus-prod \
  --dry-run=client -o yaml | \
  KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl apply -f -
```

install or upgrade envoy gateway:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/install-envoy-prod.yml
```

verify cluster-side resources:

```bash
# server: gw
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get pods -n envoy-gateway-system -o wide
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get gatewayclass
```

expected:

```text
gatewayclass.gateway.networking.k8s.io/campus-envoy accepted true
```

wrapper equivalent:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash ops/scripts/runtime/02-install-envoy-prod.sh
```

## phase 10: prepare github controls

in github ui:

* create or verify environment `production`
* enable required reviewers for `production`
* keep `ghcr_pull_username` and `ghcr_pull_token` configured
* register one university `gw` control runner for `uni-dev-*` and `uni-v*`
* register one home `gw` control runner for `home-dev-*` and `home-v*`, if using the home environment

expected runner labels:

```text
uni-gw-runner: self-hosted, linux, x64, uni, gw, deploy
home-gw-runner: self-hosted, linux, x64, home, gw, deploy
```

check runner services without printing tokens:

```bash
# server: gw
systemctl list-units 'actions.runner.*.service' --all --no-pager
```

Runner service names depend on the runner name chosen during registration.

## phase 11: deploy dev

create a `uni-dev-*` tag from a clean `main` checkout:

```bash
# server: local pc
git checkout main
git pull --ff-only
git status --short
git tag uni-dev-test-YYYYMMDD-HHMMSS
git push origin uni-dev-test-YYYYMMDD-HHMMSS
```

then verify from `gw`:

```bash
# server: gw
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get pods -n campus-dev -o wide
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get gateway,httproute,envoyproxy,clienttrafficpolicy -n campus-dev -o wide
```

## phase 12: deploy prod

create a `uni-v*` tag only after:

* prod cluster is ready
* prod kubeconfig works from `gw`
* envoy gateway is installed
* prod runtime files exist
* `production` environment approval is configured
* the university `gw` control runner is online with `uni+gw+deploy` labels

create and push the release tag:

```bash
# server: local pc
git checkout main
git pull --ff-only
git status --short
git tag uni-vX.Y.Z
git push origin uni-vX.Y.Z
```

approve the `production` environment deployment in github ui.

verify from `gw`:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

KUBECONFIG=/home/nexoc/.kube/prod.yaml \
ansible-playbook \
  -i ops/inventory/uni.local.ini \
  ops/playbooks/verify-prod-release.yml
```

manual prod apply is only for controlled lab/bootstrap situations. use the deployment script, not raw `kubectl apply -k`, because prod also renders the external `s4-db` service and endpointslice from runtime config:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag uni-vX.Y.Z
```

before real apply, use render-only and server dry-run:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag uni-vX.Y.Z \
  --render-only \
  --manifest-out /tmp/campus-prod-render.yaml

KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl apply \
  -f /tmp/campus-prod-render.yaml \
  --dry-run=server
```

wrapper equivalents:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

TAG=uni-vX.Y.Z bash ops/scripts/runtime/03-render-prod.sh
TAG=uni-vX.Y.Z CONFIRM_PROD_APPLY=apply-prod bash ops/scripts/runtime/04-apply-prod.sh
```

## phase 13: verify prod deployment

check workloads:

```bash
# server: gw
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get pods -n campus-prod -o wide
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl rollout status deployment/auth -n campus-prod --timeout=3m
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl rollout status deployment/backend -n campus-prod --timeout=3m
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl rollout status deployment/frontend -n campus-prod --timeout=3m
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl rollout status deployment/campus-nginx -n campus-prod --timeout=3m
```

check importer:

```bash
# server: gw
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get job -n campus-prod
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl logs job/campus-importer -n campus-prod --tail=100
```

check gateway and nodeport:

```bash
# server: gw
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get gateway,httproute,clienttrafficpolicy,envoyproxy -n campus-prod
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get svc -A | grep -E "envoy|30080|campus"
```

smoke test through every prod node:

```bash
# server: gw
curl -I --max-time 10 -H "Host: campus-prod.10-123-127-29.sslip.io" http://s1-prod:30080 || true
curl -I --max-time 10 -H "Host: campus-prod.10-123-127-29.sslip.io" http://s2-prod:30080 || true
curl -I --max-time 10 -H "Host: campus-prod.10-123-127-29.sslip.io" http://s3-prod:30080 || true
```

expected result:

```text
HTTP/1.1 200 OK
```

For home PROD, use `Host: home-campus-prod.davl.at` against the home prod
nodes.

run repo verification:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

KUBECONFIG=/home/nexoc/.kube/prod.yaml \
ansible-playbook \
  -i ops/inventory/uni.local.ini \
  ops/playbooks/verify-prod-release.yml

KUBECONFIG=/home/nexoc/.kube/prod.yaml \
ansible-playbook \
  -i ops/inventory/uni.local.ini \
  ops/playbooks/check-prod-cluster.yml
```

if checking a specific release tag:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

KUBECONFIG=/home/nexoc/.kube/prod.yaml \
ansible-playbook \
  -i ops/inventory/uni.local.ini \
  -e expected_tag=uni-vX.Y.Z \
  ops/playbooks/check-prod-cluster.yml
```

wrapper equivalent:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
TAG=uni-vX.Y.Z bash ops/scripts/runtime/05-verify-prod.sh
```

## phase 14: install monitoring

bootstrap monitoring vm:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/check-monitoring.yml
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/bootstrap-monitoring.yml
```

install node-exporter on all vms:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/install-node-exporter.yml
```

install prometheus and grafana:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/install-prometheus.yml
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/install-grafana.yml
```

install database and kubernetes metrics:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/render-postgres-exporter-env.yml
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/install-postgres-exporter.yml
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/install-kube-state-metrics.yml
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/install-prometheus.yml
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/install-grafana.yml
```

final monitoring check:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/check-monitoring-stack.yml
```

wrapper equivalent for the full monitoring sequence:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s
bash ops/scripts/runtime/06-install-monitoring.sh
```

expected prometheus target count:

```text
11
```

## phase 15: final platform verification

run final checks:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

KUBECONFIG=/home/nexoc/.kube/prod.yaml ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/check-prod-cluster.yml
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/check-db-access.yml
KUBECONFIG=/home/nexoc/.kube/prod.yaml ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/verify-prod-release.yml
ansible-playbook -i ops/inventory/uni.local.ini ops/playbooks/check-monitoring-stack.yml
```

the platform is ready when:

* dev deploy succeeds from a `uni-dev-*` or `home-dev-*` tag
* prod deploy succeeds from a `uni-v*` or `home-v*` tag after approval
* prod smoke checks return http 200
* database access checks pass
* monitoring stack check passes
* grafana shows vm, postgresql, and kubernetes dashboards

## known non-critical warnings

### ansible warning: group and host with same name `gw`

```text
[warning]: found both group and host with same name: gw
```

this is currently expected because the inventory keeps logical group `[gw]` and host `gw`.

### image pull secret warning

```text
failedtoretrieveimagepullsecret ghcr-pull
```

if images are public and successfully pulled, this warning does not block deployment. for a clean production state, either create the `ghcr-pull` secret in the namespace or remove the unused imagepullsecret reference from the manifests.

### startup/readiness probe warnings during spring boot startup

short-lived startup/readiness probe failures are expected while java services start. they are not a problem if the deployment rollout completes and pods become `1/1 running`.

## troubleshooting

### missing helm on gw

```text
symptom: install-envoy-prod.yml fails at "which helm"
fix: install helm cli on gw and rerun bootstrap-gw.yml, then install-envoy-prod.yml
```

### missing prod namespace during envoy installation

```text
symptom: helm install fails with namespaces "campus-prod" not found
fix: create campus-prod namespace before running install-envoy-prod.yml
```

### missing prod kubeconfig

```text
symptom: kubectl cannot reach prod from gw
fix: place a valid kubeconfig at /home/nexoc/.kube/prod.yaml and verify kubectl get nodes
```

### kubectl tries localhost:8080

```text
symptom: kubectl error: the connection to the server localhost:8080 was refused
fix: run the command with KUBECONFIG=/home/nexoc/.kube/prod.yaml or update the playbook/script environment
```

### runner offline

```text
symptom: github actions job waits for self-hosted runner
fix: check the matching environment gw runner service and verify labels in github ui
```

### ghcr pull failure

```text
symptom: pods show imagepullbackoff or errimagepull
fix: verify github actions secrets exist and rerun the release so ghcr-pull is recreated, or create the pull secret manually if needed
```

### database alias failure

```text
symptom: application cannot resolve or reach s4-db
fix: verify db-endpoint.env exists, rerun the prod overlay deploy, then run check-db-access.yml
```

### monitoring target down

```text
symptom: check-monitoring-stack.yml reports a down target
fix: verify the service on the target host, then rerun install-prometheus.yml if scrape config changed
```

## verified university baseline example

verified state from the university environment:

```text
prod k3s ha: ready
s1-prod/s2-prod/s3-prod: ready control-plane,etcd
campus-prod workloads: running
campus-importer: completed
s4-db endpointslice: 192.168.50.4:5432
gateway campus: programmed=true
envoy nodeport: 30080
smoke through s1-prod/s2-prod/s3-prod: http 200
verify-prod-release.yml: green
check-prod-cluster.yml: green
```
