# Campus++ Kubernetes/DevOps Portfolio Status

Verified on 2026-05-24.

## Target

Campus++ now targets a home-only Kubernetes lab. The old university/uni target
is removed from the active model.

```text
home lab on one physical PC with VM clones
```

## Home Lab Infrastructure

```text
gw             192.168.56.10  gateway / runner / ansible / edge
s4-db          192.168.56.20  PostgreSQL
s6-monitoring  192.168.56.30  Prometheus / Grafana
s5-dev         192.168.56.40  single-node dev k3s
s1-prod        192.168.56.51  prod k3s node 1
s2-prod        192.168.56.52  prod k3s node 2
s3-prod        192.168.56.53  prod k3s node 3
```

The repository keeps stable logical hostnames in manifests and workflows.
Environment-specific addresses belong in runtime inventory, DNS, and operator
documentation.

## Verified Application Environments

Home dev:

```text
namespace: campus-dev
cluster: s5-dev single-node k3s
nodeport: 30080
route host: home-campus-dev.davl.at
http://home-campus-dev.davl.at  -> 301
https://home-campus-dev.davl.at -> 200
```

Home prod:

```text
namespace: campus-prod
cluster: s1-prod/s2-prod/s3-prod k3s HA
nodeport: 30080
route host: home-campus-prod.davl.at
http://home-campus-prod.davl.at  -> 301
https://home-campus-prod.davl.at -> 200
```

## Public Edge Routing

```text
public VPS IP: 130.185.118.138
DNS:
  home-campus-dev.davl.at  -> VPS
  home-campus-prod.davl.at -> VPS
VPS nginx config:
  /etc/nginx/sites-available/home-campus-routing.conf
nginx backup:
  /root/nginx-backups/nginx-before-home-campus-2026-05-24-132601.tar.gz
Let's Encrypt certificate:
  /etc/letsencrypt/live/home-campus/fullchain.pem
  /etc/letsencrypt/live/home-campus/privkey.pem
certificate names:
  home-campus-dev.davl.at
  home-campus-prod.davl.at
certificate method:
  certbot certonly --webroot
```

The certificate was not obtained with `certbot --nginx`.

Traffic path:

```text
internet
-> DNS
-> VPS nginx HTTPS
-> WireGuard
-> home VM network
-> k3s NodePort 30080
-> Envoy Gateway
-> Campus++ app
```

## Monitoring

Monitoring baseline is visually verified:

```text
Prometheus on s6-monitoring: healthy
Grafana on s6-monitoring: healthy
node exporters: up on all 7 VMs
postgres exporter: up on s4-db
kube-state-metrics: up for dev and prod
```

Grafana dashboards visible:

```text
Campus VM Overview
Campus PostgreSQL Overview
Campus Kubernetes Overview
```

## Remaining Work

```text
Grafana external access is not exposed yet.
Prometheus should not be public.
Security hardening is intentionally postponed as the final step.
```

Possible later improvements:

```text
Grafana protected access
rate limits
basic auth for dev
default deny server
fail2ban
firewall review
RBAC-limited deployment kubeconfigs
Alertmanager and alert rules
Loki or Grafana Alloy log collection
```
