# Architecture

## Target

Campus++ targets a home-only Kubernetes lab on one physical PC with VM roles.
Only the home lab target is active. Older non-home targets are intentionally
absent from the current architecture.

The repository documents the architecture, deployment model, and operational
checks. It does not store VPS certificates, private keys, htpasswd files, or
secret values.

## Server Roles

- `gw`: control host, GitHub Actions runner host, Ansible entry point
- `s4-db`: internal PostgreSQL host
- `s5-dev`: single-node dev k3s cluster
- `s1-prod`, `s2-prod`, `s3-prod`: prod k3s HA cluster
- `s6-monitoring`: Prometheus and Grafana host

## Environments

Dev:

- cluster: single-node k3s on `s5-dev`
- namespace: `campus-dev`
- public host: `home-campus-dev.davl.at`
- edge port: k3s NodePort `30080`

Prod:

- cluster: k3s HA on `s1-prod`, `s2-prod`, `s3-prod`
- namespace: `campus-prod`
- public host: `home-campus-prod.davl.at`
- edge port: k3s NodePort `30080`

Database:

- PostgreSQL runs on `s4-db`.
- PostgreSQL is internal only.
- Application config keeps `DB_HOST=s4-db`.
- Kubernetes gets the stable `s4-db` name through Service and EndpointSlice
  aliases in both app namespaces.

Monitoring:

- Prometheus runs on `s6-monitoring`.
- Grafana runs on `s6-monitoring`.
- Grafana is the only external monitoring entry point.

## Public Edge

The public edge is VPS Nginx, not `gw` Nginx. The `gw` VM remains a control and
home lab entry host, but it is not the public HTTPS edge.

The public edge Nginx configuration lives on the VPS as runtime infrastructure
and is not stored in this repository.

Runtime-only VPS paths:

```text
/etc/nginx/sites-available/home-campus-routing.conf
/etc/nginx/sites-available/home-grafana-routing.conf
/etc/nginx/.htpasswd-home-grafana
/etc/letsencrypt/live/home-campus/
/etc/letsencrypt/live/home-grafana/
```

## Traffic Path

```text
internet
  -> davl.at DNS
  -> VPS Nginx / HTTPS
  -> WireGuard
  -> home lab
  -> k3s NodePort / Envoy Gateway
  -> Campus++ app
```

## Diagram

```text
                           public HTTPS
                               |
                               v
                      +------------------+
                      |   VPS Nginx      |
                      |  Let's Encrypt   |
                      +------------------+
                               |
                            WireGuard
                               |
                               v
+---------------------------------------------------------------+
|                         home lab                              |
|                                                               |
|  +------+       +----------------+       +----------------+    |
|  |  gw  |       |     s5-dev     |       | s1/s2/s3-prod  |    |
|  | CI   |       |  k3s dev       |       | k3s HA prod    |    |
|  | ops  |       |  campus-dev    |       | campus-prod    |    |
|  +------+       +----------------+       +----------------+    |
|                         |                         |             |
|                         v                         v             |
|                   Envoy Gateway             Envoy Gateway       |
|                         |                         |             |
|                         +-----------+-------------+             |
|                                     |                           |
|                                     v                           |
|                                  Campus++                       |
|                                     |                           |
|                                     v                           |
|                                  s4-db                          |
|                                                               |
|  +----------------+                                           |
|  | s6-monitoring  |  Prometheus + Grafana                     |
|  +----------------+                                           |
+---------------------------------------------------------------+
```
