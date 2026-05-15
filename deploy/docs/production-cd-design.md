# Production CD Design

This document defines the intended production release model for Campus++.

Production delivery is deliberately separated from DEV delivery. The DEV
runner must not hold production cluster credentials.

## Current Baseline

DEV tag-based CD is already active:

```text
dev-* tag -> GitHub Actions -> s5-campus-dev runner -> dev k3s -> campus-dev -> Envoy NodePort 30080
```

The production Kubernetes target already exists:

```text
s1-prod / s2-prod / s3-prod -> k3s HA cluster
```

The production kubeconfig is managed from `gw`:

```text
/home/nexoc/.kube/prod.yaml
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

The production runner should use these labels:

```text
self-hosted, linux, x64, prod, gw
```

## Production Traffic Path

Production traffic is separate from the CD control path:

```text
client -> gw edge -> prod nodes NodePort 30080 -> Envoy Gateway -> campus-prod
```

The intended production application hostname is:

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

## Render-Only Gate

Before adding or running a production workflow, render the production overlay
from `gw` without applying it:

```bash
# server: gw
cd ~/campus-plus-plus-k8s

CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets \
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
```

Verify without printing secret values:

```bash
# server: gw
grep -nE "namespace: campus-prod|campus-prod.davl.at|nodePort: 30080|image: ghcr.io/nexoc|imagePullSecrets|ghcr-pull" /tmp/campus-prod-rendered.yaml -A3 -B3
git status --short
```

## Future Workflow Shape

The future production workflow should live in:

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

The first production release tag should only be created after the runner,
secrets, environment approval, tag protection, and render-only gate are in
place.
