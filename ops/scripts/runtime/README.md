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
EXPECTED_HOST=campus-prod.10-123-127-29.sslip.io
```

For home PROD manual wrapper runs, override `EXPECTED_HOST`:

```bash
# server: gw
TAG=home-vX.Y.Z EXPECTED_HOST=home-campus-prod.davl.at bash ops/scripts/runtime/03-render-prod.sh
```

Recommended order:

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

For normal production releases, prefer the GitHub Actions `uni-v*` or
`home-v*` workflows with environment approval. `04-apply-prod.sh` is for
controlled bootstrap or recovery situations where a manual apply is
intentionally chosen.
