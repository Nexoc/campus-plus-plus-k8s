# Home Lab Architecture

This repository targets a home Kubernetes lab running on one physical PC with
VM clones.

## Active Target

```text
home lab on one physical PC
```

`uni` is removed as an active target. The server roles are not removed and are
not renamed.

## VM Roles

```text
gw             192.168.56.10  gateway / runner / ansible / edge
s4-db          192.168.56.20  PostgreSQL
s6-monitoring  192.168.56.30  Prometheus + Grafana
s5-dev         192.168.56.40  dev k3s
s1-prod        192.168.56.51  prod k3s node 1
s2-prod        192.168.56.52  prod k3s node 2
s3-prod        192.168.56.53  prod k3s node 3
```

`s4-db`, `s5-dev`, `s6-monitoring`, `s1-prod`, `s2-prod`, and `s3-prod` are
home-lab VM roles.

## Release Channels

```text
home-dev-* -> s5-dev / campus-dev
home-v*    -> s1-prod/s2-prod/s3-prod / campus-prod
```

## Hostnames

```text
home dev      home-campus-dev.davl.at
home prod     home-campus-prod.davl.at
home grafana  home-grafana.davl.at
```

External application access is verified:

```text
http://home-campus-dev.davl.at    -> 301
https://home-campus-dev.davl.at   -> 200
http://home-campus-prod.davl.at   -> 301
https://home-campus-prod.davl.at  -> 200
```

## Public Edge

DNS for `home-campus-dev.davl.at` and `home-campus-prod.davl.at` points to the
public VPS `130.185.118.138`.

```text
internet -> DNS -> VPS nginx HTTPS -> WireGuard -> home VM network
-> k3s NodePort 30080 -> Envoy Gateway -> Campus++ app
```

VPS routing is documented as runtime/operator state, not as tracked nginx
configuration:

```text
/etc/nginx/sites-available/home-campus-routing.conf
/root/nginx-backups/nginx-before-home-campus-2026-05-24-132601.tar.gz
/etc/letsencrypt/live/home-campus/fullchain.pem
/etc/letsencrypt/live/home-campus/privkey.pem
/etc/letsencrypt/live/home-grafana/fullchain.pem
/etc/letsencrypt/live/home-grafana/privkey.pem
```

Grafana external access is protected:

```text
https://home-grafana.davl.at
-> VPS nginx HTTPS
-> nginx basic auth
-> WireGuard
-> s6-monitoring:3000
-> Grafana login
-> viewer user
```

Prometheus, exporters, and PostgreSQL are not public.

## Database Alias

Application config keeps the stable database host:

```text
DB_HOST=s4-db
```

For home dev and home prod, `deploy/scripts/apply-overlay.sh` renders
`service/s4-db` and `endpointslice/s4-db` from the runtime-only
`db-endpoint.env` file for that environment.

## Runtime Files

On `gw`:

```text
/home/nexoc/.kube/dev.yaml
/home/nexoc/.kube/prod.yaml
/home/nexoc/campus-secrets/home/db-secrets.env
/home/nexoc/campus-secrets/home/auth-secrets.env
/home/nexoc/campus-secrets/home/db-endpoint.env
/home/nexoc/campus-secrets/prod/db-secrets.env
/home/nexoc/campus-secrets/prod/auth-secrets.env
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

Active inventory:

```text
ops/inventory/home.local.ini
```

Tracked example:

```text
ops/inventory/home.example.ini
```
