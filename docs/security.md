# Security

This document describes implemented security boundaries.

## Public Edge

- The public edge is VPS Nginx.
- HTTPS is handled with Let's Encrypt certificates.
- Traffic reaches the home lab through WireGuard.
- The home VM network is not directly public.

Runtime-only VPS paths:

```text
/etc/nginx/sites-available/home-campus-routing.conf
/etc/nginx/sites-available/home-grafana-routing.conf
/etc/nginx/.htpasswd-home-grafana
```

Certificate paths:

```text
/etc/letsencrypt/live/home-campus/
/etc/letsencrypt/live/home-grafana/
```

## Private Services

- The Kubernetes API is not public.
- PostgreSQL is not public.
- Prometheus is not public.
- node exporters are not public.
- postgres-exporter is not public.
- Prometheus and exporters remain private.

## Grafana Protection

Grafana is protected by two layers:

1. Nginx basic auth
2. Grafana viewer/read-only user

Grafana is exposed externally only through:

```text
https://home-grafana.davl.at
```

No usernames, passwords, htpasswd hashes, tokens, or private keys are stored in
this repository.

## Firewall Boundary

The `s6-monitoring` firewall allows Grafana TCP/3000 only from:

- the VPS WireGuard source address used by public Nginx routing
- the `gw` control host for private operational checks

All other direct access to Grafana TCP/3000 is dropped. Prometheus and exporters
remain private.

Runtime firewall path:

```text
/etc/iptables/rules.v4
```
