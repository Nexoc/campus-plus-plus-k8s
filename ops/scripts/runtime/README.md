# Runtime Automation Wrappers

These scripts are thin wrappers for the university/lab bootstrap flow.

They are intended to be executed from `gw` after the repository is cloned there.
They do not store secrets, do not register GitHub runners, and do not install or
reinstall k3s. k3s cluster installation remains a runtime-only step outside the
repo-owned ops layer.

Default runtime values:

```text
ANSIBLE_INVENTORY=ops/inventory/uni.local.ini
KUBECONFIG=/home/nexoc/.kube/prod.yaml
CAMPUS_SECRETS_ROOT=/home/nexoc/campus-secrets
PROD_NAMESPACE=campus-prod
EXPECTED_NODEPORT=30080
EXPECTED_HOST=campus-prod.davl.at
```

Recommended order:

```bash
# server: gw
cd /home/nexoc/campus-plus-plus-k8s

bash ops/scripts/runtime/00-preflight.sh
bash ops/scripts/runtime/01-check-runtime-files.sh
bash ops/scripts/runtime/02-install-envoy-prod.sh
TAG=vX.Y.Z bash ops/scripts/runtime/03-render-prod.sh
TAG=vX.Y.Z CONFIRM_PROD_APPLY=apply-prod bash ops/scripts/runtime/04-apply-prod.sh
TAG=vX.Y.Z bash ops/scripts/runtime/05-verify-prod.sh
bash ops/scripts/runtime/06-install-monitoring.sh
```

For normal production releases, prefer the GitHub Actions `v*` workflow with
the `production` environment approval. `04-apply-prod.sh` is for controlled
bootstrap or recovery situations where a manual apply is intentionally chosen.
