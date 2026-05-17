# Production CD Design

This document defines the production release model for Campus++.

Production delivery is deliberately separated from DEV delivery. The DEV
runner must not hold production cluster credentials.

Production delivery is also separate from host operations and monitoring.
Ansible playbooks in `ops/` can verify or prepare infrastructure, but `v*`
tags remain the release mechanism for Campus++ application images.

## Current Baseline

DEV tag-based CD is already active:

```text
dev-* tag -> GitHub Actions -> s5-campus-dev runner -> dev k3s -> campus-dev -> Envoy NodePort 30080
```

The production Kubernetes target:

```text
s1-prod / s2-prod / s3-prod -> k3s HA cluster
```

The production kubeconfig is managed from `gw`:

```text
/home/nexoc/.kube/prod.yaml
```

Current implementation status:

```text
prod cd: implemented
latest successful prod release baseline: v0.1.1
prod k3s ha: ready
prod namespace: campus-prod
prod gateway nodeport: 30080
verification playbook: ops/playbooks/verify-prod-release.yml
```

## Release Channels

DEV releases:

```text
dev-* tag
-> runner: s5-campus-dev
-> target: s5-dev k3s
-> namespace: campus-dev
```

Production releases:

```text
v* tag
-> GitHub environment: production
-> manual approval required
-> runner: gw-campus-prod
-> target: prod k3s HA cluster
-> namespace: campus-prod
-> workloads: s1-prod, s2-prod, s3-prod
```

The production runner uses these labels:

```text
self-hosted, linux, x64, prod, gw
```

## Production Traffic Path

Production traffic is separate from the CD control path:

```text
client -> gw edge -> prod nodes NodePort 30080 -> Envoy Gateway -> campus-prod
```

The production application hostname is:

```text
campus-prod.davl.at
```

The `gw` edge can later load balance across all production nodes:

```text
gw -> s1-prod:30080
gw -> s2-prod:30080
gw -> s3-prod:30080
```

## GitHub Controls

Production deployment should be guarded by:

- a GitHub Environment named `production`
- required reviewers on the `production` environment
- protected tag rules for `v*`
- separate production host secrets under `/home/nexoc/campus-secrets/prod`
- eventually, an RBAC-limited production kubeconfig instead of an admin kubeconfig

The environment approval protects the deployment job even if a `v*` tag is
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

Use explicit kubeconfig paths in automation. Do not depend on shell aliases in
GitHub Actions jobs.

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

In production, `s4-db` is a Kubernetes DNS alias inside `campus-prod`, not a
committed infrastructure address. During prod render/apply,
`deploy/scripts/apply-overlay.sh` generates:

- `service/s4-db`
- `endpointslice/s4-db`

The actual external PostgreSQL endpoint comes from host-local runtime config on
`gw`:

```text
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

Expected keys:

```text
DB_ENDPOINT_ADDRESS=<environment-specific IPv4 address>
DB_ENDPOINT_PORT=5432
```

This file is not committed. A university deployment keeps the same workflow and
overlay contract, but provides its own `db-endpoint.env` on the deployment host.
PROD render-only fails before Kustomize if this file is missing or if either
`DB_ENDPOINT_ADDRESS` or `DB_ENDPOINT_PORT` is absent.

## Render-Only Gate

Before running a production workflow, render the production overlay from `gw`
without applying it:

```bash
# server: gw
cd ~/campus-plus-plus-k8s

CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
KUBECONFIG=/home/nexoc/.kube/prod.yaml \
bash deploy/scripts/apply-overlay.sh \
  --environment prod \
  --image-tag v-render-test \
  --render-only \
  --manifest-out /tmp/campus-prod-rendered.yaml
```

Expected rendered properties:

```text
namespace: campus-prod
hostname: campus-prod.davl.at
nodePort: 30080
imagePullSecrets: ghcr-pull
images: ghcr.io/nexoc/...:v-render-test
service/s4-db
endpointslice/s4-db
```

Verify without printing secret values:

```bash
# server: gw
grep -nE "namespace: campus-prod|campus-prod.davl.at|nodePort: 30080|image: ghcr.io/nexoc|imagePullSecrets|ghcr-pull|kind: Service|kind: EndpointSlice|name: s4-db" /tmp/campus-prod-rendered.yaml -A3 -B3
git status --short
```

## Production Workflow Shape

The production workflow lives in:

```text
.github/workflows/deploy-prod.yml
```

Trigger:

```text
v*
```

Deployment job:

```text
environment: production
runner: gw-campus-prod
KUBECONFIG: /home/nexoc/.kube/prod.yaml
CAMPUS_SECRETS_ROOT: /home/nexoc/campus-secrets
overlay: deploy/app/overlays/prod
namespace: campus-prod
expected NodePort: 30080
```

The workflow does not carry the external database address. It only sets
`CAMPUS_SECRETS_ROOT`, and `apply-overlay.sh` reads the environment-specific
endpoint from the deployment host.

Production release tags should only be created after the runner, secrets,
environment approval, tag protection, and render-only gate are in place.
