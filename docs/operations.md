# Operations

This runbook is for an already built Campus++ home lab. It contains health
checks, normal deployment flow, runtime file checks, and short recovery steps.

## 1. Quick Health Check

```bash
# server: local pc
curl -I https://home-campus-dev.davl.at
curl -I https://home-campus-prod.davl.at
curl -I https://home-grafana.davl.at
```

```bash
# server: gw
cd /home/<user>/<repo-dir>
KUBECONFIG=/home/<user>/.kube/dev.yaml kubectl get pods -n campus-dev -o wide
KUBECONFIG=/home/<user>/.kube/prod.yaml kubectl get pods -n campus-prod -o wide
ansible all -i ops/inventory/lab.local.ini -m ping
```

## 2. Bootstrap Checklist

- `gw` can SSH to all lab roles.
- `gw` has the repository checkout.
- `gw` has the verified runtime inventory at `ops/inventory/lab.local.ini`.
- `gw` has dev and prod kubeconfigs.
- Runtime secret files exist under `/home/<user>/campus-secrets`.
- `home-gw-runner` is online.
- Dev and prod clusters can pull GHCR images.
- VPS Nginx routes public HTTPS traffic through WireGuard into the home lab.
- Monitoring services are active on `s6-monitoring`.

## 3. Runtime Evidence

Tag-driven GitHub Actions release workflow:

![GitHub Actions release workflow](assets/github-actions-release.png)

Production pods running in `campus-prod`:

![Campus++ production pods running on k3s](assets/kubernetes-prod-runtime.png)

## 4. Runtime Inventory

Current verified runtime inventory on `gw`:

```text
ops/inventory/lab.local.ini
```

Tracked example inventory:

```text
ops/inventory/home.example.ini
```

Do not blindly create or switch to `home.local.ini`. The currently verified
runtime inventory on `gw` is `lab.local.ini`.

## 5. Runtime Files / Secrets

Required home dev files:

```text
/home/<user>/campus-secrets/home/db-secrets.env
/home/<user>/campus-secrets/home/auth-secrets.env
/home/<user>/campus-secrets/home/db-endpoint.env
```

Required home prod files:

```text
/home/<user>/campus-secrets/prod/db-secrets.env
/home/<user>/campus-secrets/prod/auth-secrets.env
/home/<user>/campus-secrets/prod/db-endpoint.env
```

`db-endpoint.env` structure:

```text
DB_ENDPOINT_ADDRESS=<db-ipv4>
DB_ENDPOINT_PORT=5432
```

Do not print, paste, or commit secret values.

## 6. Kubeconfig Checks

Expected kubeconfig paths:

```text
/home/<user>/.kube/dev.yaml
/home/<user>/.kube/prod.yaml
```

```bash
# server: gw
KUBECONFIG=/home/<user>/.kube/dev.yaml kubectl get nodes -o wide
KUBECONFIG=/home/<user>/.kube/prod.yaml kubectl get nodes -o wide
```

## 7. Normal Deployment Flow

- Dev changes go through `home-dev-*` tags.
- Prod changes go through `home-v*` tags.
- Prod uses GitHub environment approval.
- Do not retag old tags.
- Create a new tag for every new deployment.
- Manual production apply is not the default path.

Example dev tag:

```bash
# server: local pc
TAG="home-dev-<release-id>"
git tag "$TAG"
git push origin "$TAG"
```

Example prod tag:

```bash
# server: local pc
TAG="home-v<version>"
git tag "$TAG"
git push origin "$TAG"
```

Production warning: do not apply production manually unless it is explicitly
intended. Production changes should normally go through the `home-v*` tag
workflow and approval path.

## 8. Known Safe Checks

These commands are read-only.

```bash
# server: gw
KUBECONFIG=/home/<user>/.kube/dev.yaml kubectl get pods -n campus-dev -o wide
KUBECONFIG=/home/<user>/.kube/dev.yaml kubectl get svc,endpointslice -n campus-dev
KUBECONFIG=/home/<user>/.kube/dev.yaml kubectl get gateway,httproute -n campus-dev
```

```bash
# server: gw
KUBECONFIG=/home/<user>/.kube/prod.yaml kubectl get pods -n campus-prod -o wide
KUBECONFIG=/home/<user>/.kube/prod.yaml kubectl get svc,endpointslice -n campus-prod
KUBECONFIG=/home/<user>/.kube/prod.yaml kubectl get gateway,httproute -n campus-prod
```

```bash
# server: local pc
curl -I https://home-campus-dev.davl.at
curl -I https://home-campus-prod.davl.at
```

```bash
# server: gw
ansible all -i ops/inventory/lab.local.ini -m ping
```

```bash
# server: s6-monitoring
curl -fsS "http://localhost:9090/api/v1/targets?state=active" | grep -E "node-exporter|postgres|kube-state"
```

```bash
# server: gw
systemctl status <github-runner-service> --no-pager
journalctl -u <github-runner-service> -n 80 --no-pager
```

The runner service name depends on the GitHub repository and runner name.

## 9. Commands That Modify State

Treat these as state-changing operations:

- creating `home-dev-*` or `home-v*` Git tags
- pushing release tags that trigger GitHub Actions deployments
- `kubectl apply`
- `helm upgrade`
- `ansible-playbook` install or deploy playbooks
- `systemctl reload nginx`
- editing runtime files
- changing firewall rules
- changing Grafana or Nginx authentication

## 10. Dev Verification

```bash
# server: gw
KUBECONFIG=/home/<user>/.kube/dev.yaml kubectl get pods -n campus-dev -o wide
KUBECONFIG=/home/<user>/.kube/dev.yaml kubectl get svc,endpointslice -n campus-dev | grep s4-db
KUBECONFIG=/home/<user>/.kube/dev.yaml kubectl get gateway,httproute -n campus-dev
```

```bash
# server: local pc
curl -I http://home-campus-dev.davl.at
curl -I https://home-campus-dev.davl.at
```

Expected:

- HTTP returns `301`.
- HTTPS returns `200`.

## 11. Prod Verification

```bash
# server: gw
KUBECONFIG=/home/<user>/.kube/prod.yaml kubectl get pods -n campus-prod -o wide
KUBECONFIG=/home/<user>/.kube/prod.yaml kubectl get svc,endpointslice -n campus-prod | grep s4-db
KUBECONFIG=/home/<user>/.kube/prod.yaml kubectl get gateway,httproute -n campus-prod
```

```bash
# server: local pc
curl -I http://home-campus-prod.davl.at
curl -I https://home-campus-prod.davl.at
```

Expected:

- HTTP returns `301`.
- HTTPS returns `200`.

## 12. External HTTPS Verification

```bash
# server: local pc
curl -I http://home-campus-dev.davl.at
curl -I https://home-campus-dev.davl.at
curl -I http://home-campus-prod.davl.at
curl -I https://home-campus-prod.davl.at
curl -I https://home-grafana.davl.at
curl -I -u <basic-auth-user> https://home-grafana.davl.at
```

Expected:

- `home-campus-dev` HTTP returns `301`.
- `home-campus-dev` HTTPS returns `200`.
- `home-campus-prod` HTTP returns `301`.
- `home-campus-prod` HTTPS returns `200`.
- `home-grafana` HTTPS without auth returns `401`.
- `home-grafana` with basic auth returns a Grafana login redirect.

Runtime VPS paths may be checked without printing file contents:

```text
/etc/nginx/sites-available/home-campus-routing.conf
/etc/nginx/sites-available/home-grafana-routing.conf
/etc/nginx/.htpasswd-home-grafana
/etc/letsencrypt/live/home-campus/
/etc/letsencrypt/live/home-grafana/
```

## 13. Runner Checks

```bash
# server: gw
systemctl status <github-runner-service> --no-pager
journalctl -u <github-runner-service> -n 80 --no-pager
```

The active deployment runner is `home-gw-runner`. The system service name is
runtime-specific and depends on the GitHub repository and runner name.

## 14. Monitoring Checks

```bash
# server: s6-monitoring
systemctl status prometheus --no-pager
systemctl status grafana-server --no-pager
curl -fsS "http://localhost:9090/-/healthy"
curl -fsS "http://localhost:9090/api/v1/targets?state=active" | grep -E "node-exporter|postgres|kube-state"
```

```bash
# server: local pc
curl -I https://home-grafana.davl.at
curl -I -u <basic-auth-user> https://home-grafana.davl.at
```

Expected:

- Prometheus is healthy from `s6-monitoring`.
- Grafana is reachable through protected external access.
- Node exporter targets are up for all lab VMs.
- PostgreSQL exporter is up for `s4-db`.
- kube-state-metrics targets are up for dev and prod.

## 15. Short Rollback / Recovery

- Check the last successful workflow and release tag.
- Do not retag old tags.
- Create a new corrective tag.
- Check runner status and recent runner logs.
- Verify dev and prod pods.
- Verify external HTTPS.
- Verify monitoring targets and Grafana dashboards.
