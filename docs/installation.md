# Installation Plan

This document describes the theoretical installation flow for Campus++: what
must be prepared first, which runtime files are required, and the order in
which the environment should be brought up.

The repository intentionally does not store real IP addresses, passwords,
tokens, kubeconfig files, TLS certificates, or htpasswd files. These values must
exist only on runtime hosts.

## 1. Installation Options

There are two separate scenarios:

- local startup with Docker Compose for checking the application on a developer
  machine;
- home-lab installation on Kubernetes/k3s with dev and prod environments,
  external HTTPS, PostgreSQL on a separate VM, and a monitoring stack.

Local Docker Compose does not replace the production-like installation. It is
intended for quick build and service connectivity checks.

## 2. Prerequisites

Local machine:

- Git;
- Docker and Docker Compose;
- curl;
- optionally JDK 21, Maven, and Node.js 20 if services are built outside
  Docker.

Home lab:

- `gw`: control host, GitHub Actions runner host, Ansible entry point;
- `s5-dev`: single-node k3s cluster for dev;
- `s1-prod`, `s2-prod`, `s3-prod`: k3s HA cluster for prod;
- `s4-db`: internal PostgreSQL host;
- `s6-monitoring`: Prometheus and Grafana host;
- VPS with Nginx, Let's Encrypt, and WireGuard connectivity to the home lab;
- DNS records for `home-campus-dev.davl.at`,
  `home-campus-prod.davl.at`, and `home-grafana.davl.at`.

The following tools must be installed on `gw`:

- `git`;
- `ansible` and `ansible-playbook`;
- `kubectl`;
- `helm`;
- `envsubst`;
- `curl`;
- SSH access to all lab VMs.

## 3. Runtime Naming Assumptions

This repository is currently wired for the existing home-lab runtime identity:

- Linux runtime user: `nexoc`;
- runtime base path: `/home/nexoc`;
- repository checkout on `gw`: `/home/nexoc/campus-plus-plus-k8s`;
- kubeconfig paths:
  - `/home/nexoc/.kube/dev.yaml`;
  - `/home/nexoc/.kube/prod.yaml`;
- secrets root: `/home/nexoc/campus-secrets`;
- GHCR owner and image prefix: `ghcr.io/nexoc/*`;
- self-hosted runner labels: `self-hosted`, `Linux`, `X64`, `home`, `gw`,
  `deploy`;
- production GitHub environment: `home-production`.

If the project is installed under a different Linux user, GitHub owner, runner
label set, or GitHub environment name, update the workflows, Kustomize image
names, runtime scripts, playbooks, and this installation plan before deploying.

## 4. Local Startup

1. Clone the repository.

```bash
git clone <repo-url>
cd campus-plus-plus-k8s
```

2. Create the local `.env.dev` file.

Before starting Docker Compose, `.env.dev` must exist and contain the required
database and application environment variables for local startup.

3. Start the local stack.

```bash
docker compose --env-file .env.dev up -d --build
```

4. Check container status.

```bash
docker compose ps
curl -I http://localhost/
```

5. Stop the local stack.

```bash
docker compose down
```

To remove local PostgreSQL data completely:

```bash
docker compose down -v
```

## 5. Home Lab Preparation

1. Create VMs with the expected roles:

```text
gw
s4-db
s5-dev
s1-prod
s2-prod
s3-prod
s6-monitoring
```

2. Configure network connectivity:

- `gw` must be able to SSH into every VM;
- k3s nodes must be able to reach `s4-db:5432`;
- the VPS must reach the home lab through WireGuard;
- external traffic must reach k3s NodePort `30080`;
- Grafana must be reachable externally only through the protected VPS Nginx
  route to `s6-monitoring:3000`;
- the `s6-monitoring` firewall must allow Grafana TCP/3000 only from the VPS
  WireGuard source address and from `gw` for operational checks.

3. Install k3s:

- dev: single-node cluster on `s5-dev`;
- prod: HA cluster on `s1-prod`, `s2-prod`, `s3-prod`.

4. Place kubeconfig files on `gw`:

```text
/home/nexoc/.kube/dev.yaml
/home/nexoc/.kube/prod.yaml
```

5. Verify cluster access:

```bash
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get nodes -o wide
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get nodes -o wide
```

## 6. Inventory and Preflight on gw

1. Clone the repository on `gw`.

```bash
git clone <repo-url> /home/nexoc/campus-plus-plus-k8s
cd /home/nexoc/campus-plus-plus-k8s
```

Runtime scripts and playbooks are expected to be executed from this checkout.
Several production-oriented playbooks currently use this runtime path.

2. Create the runtime inventory from the example.

```bash
cp ops/inventory/home.example.ini ops/inventory/lab.local.ini
```

3. Fill in the real addresses and SSH user in
   `ops/inventory/lab.local.ini`.

The `lab.local.ini` file must not be committed.

4. Run preflight.

```bash
bash ops/scripts/runtime/00-preflight.sh
```

Expected result:

- all required commands are available on `gw`;
- Ansible can read the inventory;
- `ansible all -m ping` succeeds;
- the connectivity playbook succeeds;
- kubeconfig is available for the clusters being installed.

The deployment workflows run smoke checks with plain `curl`, so the VM names
used by the workflows must resolve on `gw` and on the self-hosted runner host.
Ansible inventory aliases are not enough because `curl` does not read
`ansible_host`.

Required names:

```text
s5-dev
s1-prod
s2-prod
s3-prod
```

Use local DNS or `/etc/hosts` so these names resolve to the corresponding k3s
node addresses. Verify resolution on `gw`:

```bash
getent hosts s5-dev
getent hosts s1-prod
getent hosts s2-prod
getent hosts s3-prod
```

## 7. Runtime Secrets

All secrets are stored outside the repository. The default base path is:

```text
/home/nexoc/campus-secrets
```

The active home dev release workflow requires:

```text
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
/home/nexoc/campus-secrets/home/db-endpoint.env
```

Prod requires:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

`db-secrets.env`:

```text
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
```

`auth-secrets.env`:

```text
JWT_SECRET=<base64-or-random-secret>
JWT_EXPIRATION=<expiration-minutes>
```

`db-endpoint.env`:

```text
DB_ENDPOINT_ADDRESS=<db-ipv4>
DB_ENDPOINT_PORT=5432
```

Check prod runtime files:

```bash
bash ops/scripts/runtime/01-check-runtime-files.sh
```

For dev/home, an equivalent check runs in the GitHub Actions workflow before
deployment.

## 8. PostgreSQL on s4-db

PostgreSQL must be running on `s4-db`:

- database name: `campus`;
- username and password must match `db-secrets.env`;
- TCP/5432 access must be open only to the required lab hosts;
- backend and auth run Flyway migrations on startup;
- importer loads initial data as a separate Kubernetes Job.

PostgreSQL itself is installed manually on `s4-db`. After PostgreSQL is
running and `ops/inventory/lab.local.ini` is filled, configure access from the
dev and prod k3s nodes:

```bash
ansible-playbook -i ops/inventory/lab.local.ini \
  ops/playbooks/configure-s4-db-access.yml
```

Run this before the first dev or prod application deployment. The playbook
updates host firewall rules and `pg_hba.conf` so the k3s nodes can connect to
PostgreSQL without opening the database publicly.

Kubernetes does not run PostgreSQL. The clusters only create the `s4-db`
Service and EndpointSlice alias that points to the external PostgreSQL endpoint
from `db-endpoint.env`.

After the first application deployment has created the Kubernetes-side `s4-db`
alias, verify database access end to end:

```bash
ansible-playbook -i ops/inventory/lab.local.ini \
  ops/playbooks/check-db-access.yml
```

Do not treat `check-db-access.yml` as a pre-deployment check. It also verifies
the Kubernetes `s4-db` Service, EndpointSlice, and in-cluster DNS resolution, so
it is expected to fail before the app overlay has been applied at least once.

## 9. Envoy Gateway

Envoy Gateway is required in dev and prod clusters before deploying the
application.

For dev, install Envoy Gateway before the first dev release using the Helm
workflow in `deploy/infra/envoy-gateway/README.md` with
`deploy/infra/envoy-gateway/values-dev.yaml`. The dev release workflow assumes
the controller already exists.

For prod, use the wrapper:

```bash
bash ops/scripts/runtime/02-install-envoy-prod.sh
```

Checks:

```bash
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get gatewayclass
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get all -n envoy-gateway-system -o wide
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get gatewayclass
KUBECONFIG=/home/nexoc/.kube/prod.yaml kubectl get all -n envoy-gateway-system -o wide
```

Active app entrypoint:

```text
NodePort 30080
GatewayClass campus-envoy
Gateway campus
HTTPRoute campus
```

## 10. Application Storage

The backend mounts `/data/course-materials` from a Kubernetes `emptyDir`
volume. This storage is temporary and belongs to the current backend pod.

If the backend pod is deleted, rescheduled, restarted on another node, or
recreated during deployment, files stored in `/data/course-materials` can be
lost. Do not treat uploaded course materials as durable production data until a
PersistentVolumeClaim, object storage, or another persistent storage backend is
added.

## 11. GitHub Actions and GHCR

The release flow is based on GitHub tags and GHCR:

- `home-dev-*` triggers a dev release;
- `home-v*` triggers a prod release;
- images are published to `ghcr.io/nexoc/*`;
- the deploy job runs on the self-hosted runner `home-gw-runner`.

GitHub must have:

- self-hosted runner with labels `home`, `gw`, `deploy`, `Linux`, `X64`;
- package write/read permissions for workflows;
- production environment `home-production` with an approval gate;
- preferably `GHCR_PULL_USERNAME` and `GHCR_PULL_TOKEN` for persistent image
  pulls.

If `GHCR_PULL_TOKEN` is not configured, the workflow temporarily uses
`GITHUB_TOKEN`, but that pull secret may fail on future pod restarts.

## 12. Dev Deployment

Dev deployment is triggered with a tag:

```bash
TAG="home-dev-<release-id>"
git tag "$TAG"
git push origin "$TAG"
```

The workflow must:

- build and publish frontend, auth, backend, nginx, and importer images;
- verify kubeconfig `/home/nexoc/.kube/dev.yaml`;
- verify secrets in `/home/nexoc/campus-secrets/home`;
- create namespace `campus-dev`;
- create `ghcr-pull`;
- render the `home` overlay;
- apply manifests;
- check NodePort `30080` and hostname `home-campus-dev.davl.at`.

Manual verification on `gw`:

```bash
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get pods -n campus-dev -o wide
KUBECONFIG=/home/nexoc/.kube/dev.yaml kubectl get gateway,httproute -n campus-dev
curl -I https://home-campus-dev.davl.at
```

## 13. Prod Deployment

Prod deployment is triggered with a tag:

```bash
TAG="home-v<version>"
git tag "$TAG"
git push origin "$TAG"
```

The workflow must:

- build and publish images with an immutable tag;
- wait for approval in GitHub environment `home-production`;
- verify kubeconfig `/home/nexoc/.kube/prod.yaml`;
- verify secrets in `/home/nexoc/campus-secrets/prod`;
- create namespace `campus-prod`;
- create `ghcr-pull`;
- render the `prod` overlay;
- apply manifests;
- check all prod nodes through NodePort `30080` and hostname
  `home-campus-prod.davl.at`.

Manual prod apply is not the normal path. If it is explicitly needed, the
wrapper requires confirmation:

```bash
TAG="home-v<version>" CONFIRM_PROD_APPLY=apply-prod \
  bash ops/scripts/runtime/04-apply-prod.sh
```

Prod verification:

```bash
TAG="home-v<version>" bash ops/scripts/runtime/05-verify-prod.sh
ansible-playbook -i ops/inventory/lab.local.ini \
  ops/playbooks/check-db-access.yml
curl -I https://home-campus-prod.davl.at
```

## 14. Public HTTPS Edge

The public edge is VPS Nginx, not `gw`.

Runtime-only files on the VPS:

```text
/etc/nginx/sites-available/home-campus-routing.conf
/etc/nginx/sites-available/home-grafana-routing.conf
/etc/nginx/.htpasswd-home-grafana
/etc/letsencrypt/live/home-campus/
/etc/letsencrypt/live/home-grafana/
```

Expected traffic path:

```text
internet
  -> DNS
  -> VPS Nginx / HTTPS
  -> WireGuard
  -> home lab
  -> k3s NodePort 30080
  -> Envoy Gateway
  -> campus-nginx
  -> frontend/auth/backend
```

Checks:

```bash
curl -I http://home-campus-dev.davl.at
curl -I https://home-campus-dev.davl.at
curl -I http://home-campus-prod.davl.at
curl -I https://home-campus-prod.davl.at
```

Expected result:

- HTTP returns `301`;
- HTTPS returns `200`.

## 15. Monitoring

Monitoring runs on `s6-monitoring` and uses:

- Prometheus;
- Grafana;
- node-exporter on lab VMs;
- postgres-exporter for `s4-db`;
- kube-state-metrics for dev and prod.

Before installation, `s6-monitoring` must satisfy the monitoring preflight:

- at least 2 vCPU;
- at least 3500 MB RAM;
- at least 25 GB root disk;
- free TCP ports `9090`, `3000`, `9093`, and `3100`;
- systemd available.

The manually provided monitoring runtime file is:

```text
/home/nexoc/campus-secrets/monitoring/grafana.env
```

Use `ops/templates/grafana.env.example` as the format reference.

`postgres-exporter.env` is generated by
`ops/playbooks/render-postgres-exporter-env.yml` during
`06-install-monitoring.sh`. Do not create it manually unless you intentionally
override the generated file.

The generator requires the prod database runtime inputs on `gw`:

```text
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

It also reads the prod backend config from the repository checkout.

Installation:

```bash
bash ops/scripts/runtime/06-install-monitoring.sh
```

`06-install-monitoring.sh` is intended for initial monitoring installation. For
an already installed monitoring stack, use monitoring health checks instead of
rerunning the full install script.

Checks:

```bash
curl -fsS "http://localhost:9090/-/healthy"
curl -I https://home-grafana.davl.at
curl -I -u <basic-auth-user> https://home-grafana.davl.at
```

Expected result:

- Prometheus is reachable only inside the monitoring VM;
- Grafana is externally reachable only through Nginx basic auth and Grafana
  login;
- direct Grafana TCP/3000 access is allowed only from the VPS WireGuard source
  address and from `gw`;
- exporters are not exposed to the internet.

## 16. Final Checklist

The installation is ready when:

- `ansible all -i ops/inventory/lab.local.ini -m ping` succeeds;
- dev and prod kubeconfig files work from `gw`;
- PostgreSQL host access has been configured with
  `ops/playbooks/configure-s4-db-access.yml`;
- dev pods are ready in namespace `campus-dev`;
- prod pods are ready in namespace `campus-prod`;
- the `s4-db` Service and EndpointSlice exist in both app namespaces;
- `ops/playbooks/check-db-access.yml` succeeds after application deployment;
- course-material uploads are understood to be temporary until persistent
  storage is added;
- `home-campus-dev.davl.at` returns HTTPS `200`;
- `home-campus-prod.davl.at` returns HTTPS `200`;
- `home-grafana.davl.at` without basic auth returns `401`;
- Grafana with basic auth opens the login page;
- Prometheus targets show node-exporter, postgres-exporter, and
  kube-state-metrics;
- new releases go through `home-dev-*` and `home-v*` tags without retagging old
  releases.

## 17. What Must Not Be Committed

Do not commit:

- `ops/inventory/*.local.ini`;
- `deploy/app/overlays/*/secrets/*.env`;
- real files from `/home/nexoc/campus-secrets`;
- kubeconfig files;
- GitHub runner tokens;
- GHCR tokens;
- VPS Nginx configs with private details;
- TLS private keys;
- htpasswd files;
- real private lab IP addresses unless they are intended to be public.
