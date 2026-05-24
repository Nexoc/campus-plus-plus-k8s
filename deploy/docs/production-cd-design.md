# Home Production CD Design

This document defines the home production release model for Campus++.

Production delivery is separated from dev delivery by tag pattern, kubeconfig
path, namespace, GitHub environment approval, and verification checks.

Ansible playbooks in `ops/` can prepare or verify infrastructure, but `home-v*`
tags are the normal release mechanism for Campus++ production application
images.

## Current Baseline

Home dev release path:

```text
home-dev-* tag -> GitHub Actions -> home-gw-runner -> s5-dev k3s -> campus-dev -> Envoy NodePort 30080
```

Home production target:

```text
s1-prod / s2-prod / s3-prod -> k3s HA cluster
```

Production kubeconfig on `gw`:

```text
/home/nexoc/.kube/prod.yaml
```

Implementation status:

```text
prod overlay: deploy/app/overlays/prod
prod namespace: campus-prod
prod gateway nodeport: 30080
verification playbook: ops/playbooks/verify-prod-release.yml
```

## Release Channels

Home dev:

```text
home-dev-* tag
-> runner labels: self-hosted, Linux, X64, home, gw, deploy
-> kubeconfig: /home/nexoc/.kube/dev.yaml
-> target: s5-dev k3s
-> namespace: campus-dev
-> hostname: home-campus-dev.davl.at
```

Home production:

```text
home-v* tag
-> GitHub environment: home-production
-> manual approval required
-> runner labels: self-hosted, Linux, X64, home, gw, deploy
-> kubeconfig: /home/nexoc/.kube/prod.yaml
-> target: home prod k3s HA cluster
-> namespace: campus-prod
-> nodes: s1-prod, s2-prod, s3-prod
-> hostname: home-campus-prod.davl.at
```

The same home `gw` control runner can deploy dev and prod. The safety boundary
is the tag pattern, GitHub `home-production` approval, explicit kubeconfig path,
namespace checks, render/apply validation, and smoke checks.

## Production Traffic Path

Production traffic is separate from the CD control path:

```text
client -> gw edge -> prod nodes NodePort 30080 -> Envoy Gateway -> campus-prod
```

Production hostname:

```text
home-campus-prod.davl.at
```

The `gw` edge can load balance across production nodes:

```text
gw -> s1-prod:30080
gw -> s2-prod:30080
gw -> s3-prod:30080
```

## GitHub Controls

Production deployment should be guarded by:

- GitHub Environment `home-production`
- required reviewers on `home-production`
- protected tag rules for `home-v*`
- production host secrets under `/home/nexoc/campus-secrets/prod`
- an RBAC-limited production kubeconfig when available

The environment approval protects the deploy job even if a `home-v*` tag is
created accidentally.

## Production Control Host

The `gw` host is the production deployment control point.

Required files and tools on `gw`:

```text
/home/nexoc/.kube/prod.yaml
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
kubectl
git
bash
sed
awk
grep
envsubst
```

Example preflight:

```bash
# server: gw
hostname
ls -l /home/nexoc/.kube/prod.yaml
kubectl --kubeconfig /home/nexoc/.kube/prod.yaml get nodes -o wide
git --version
kubectl version --client
```

## External Database Alias

Application config keeps a stable database host:

```text
DB_HOST=s4-db
```

In production, `s4-db` is a Kubernetes DNS alias inside `campus-prod`.
`deploy/scripts/apply-overlay.sh` generates:

- `service/s4-db`
- `endpointslice/s4-db`

The actual PostgreSQL endpoint comes from:

```text
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

Expected keys:

```text
DB_ENDPOINT_ADDRESS=<home-lab-db-address>
DB_ENDPOINT_PORT=5432
```

This file is not committed.

## Render-Only Gate

Before running a production workflow, render the production overlay from `gw`
without applying it:

```bash
# server: gw
cd ~/campus-plus-plus-k8s

CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
CAMPUS_HTTPROUTE_HOSTNAME=home-campus-prod.davl.at \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag home-v-render-test \
  --render-only \
  --manifest-out /tmp/campus-prod-rendered.yaml
```

Expected rendered properties:

```text
namespace: campus-prod
hostname: home-campus-prod.davl.at
nodePort: 30080
imagePullSecrets: ghcr-pull
images: ghcr.io/nexoc/...:home-v-render-test
service/s4-db
endpointslice/s4-db
```

Verify without printing secret values:

```bash
# server: gw
grep -nE "namespace: campus-prod|home-campus-prod.davl.at|nodePort: 30080|image: ghcr.io/nexoc|imagePullSecrets|ghcr-pull|kind: Service|kind: EndpointSlice|name: s4-db" /tmp/campus-prod-rendered.yaml -A3 -B3
```

## Production Workflow Shape

Target workflow:

```text
.github/workflows/deploy-home-prod.yml
```

Trigger:

```text
home-v*
```

Deployment job:

```text
environment: home-production
runner: home gw control runner
runner labels: self-hosted, Linux, X64, home, gw, deploy
KUBECONFIG: /home/nexoc/.kube/prod.yaml
CAMPUS_SECRETS_ROOT: /home/nexoc/campus-secrets
overlay: deploy/app/overlays/prod
namespace: campus-prod
expected NodePort: 30080
expected hostname: home-campus-prod.davl.at
```

Production release tags should only be created after the runner, secrets,
environment approval, tag protection, and render-only gate are in place.
