# gw-nginx

This directory contains the nginx reverse proxy baseline for the `gw` host.

The `gw` host is the home lab entry point and forwards HTTP traffic to the dev
k3s node through Envoy Gateway.

## Current Home Lab Path

```text
client -> gw:80 -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> app
```

## Active Config

Use `campus-dev.conf` on `gw` as:

```text
/etc/nginx/sites-enabled/campus-dev
```

The config should preserve the home dev Host header:

```nginx
proxy_set_header Host home-campus-dev.davl.at;
```

This keeps Envoy `HTTPRoute` matching stable even when an operator opens the
gateway by raw IP during home lab testing.

The `gw` host must be able to resolve `s5-dev`. Keep the concrete IP mapping in
DNS or `/etc/hosts`, not in this repo-owned nginx config.

## Apply On `gw`

```bash
# server: gw
sudo nginx -t
sudo systemctl reload nginx
```

## Verify From `gw`

```bash
# server: gw
curl -I http://gw/
curl -I -H 'Host: home-campus-dev.davl.at' http://s5-dev:30080/
```

Expected result:

- both commands return a successful HTTP response
- the direct `s5-dev:30080` check confirms Envoy/Gateway API routing
- the `gw` check confirms the reverse proxy

## Public Host

The active home dev public hostname is:

```text
home-campus-dev.davl.at
```
