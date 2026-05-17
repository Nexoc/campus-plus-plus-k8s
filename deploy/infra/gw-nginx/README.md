# gw-nginx

This directory contains the nginx reverse proxy baseline for the `gw` host.

The `gw` host is the external lab entry point and forwards HTTP traffic to the
DEV k3s node through Envoy Gateway.

## Current Lab Path

```text
client -> gw:80 -> s5-dev:30080 -> Envoy Gateway -> campus-nginx -> app
```

## Active Config

Use `campus-dev.conf` on `gw` as:

```text
/etc/nginx/sites-enabled/campus-dev
```

The config intentionally sets:

```nginx
proxy_set_header Host campus-dev.10-123-127-29.sslip.io;
```

This keeps Envoy `HTTPRoute` matching stable even when an operator opens the
gateway by raw IP during lab testing.

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
curl -I -H 'Host: campus-dev.10-123-127-29.sslip.io' http://s5-dev:30080/
```

Expected result:

- both commands return `200 OK`
- the direct `s5-dev:30080` check confirms Envoy/Gateway API routing
- the `gw` check confirms the `gw` reverse proxy

## Future Public Host

When a stable public hostname is finalized, replace `server_name _` with that
hostname and update the DEV `HTTPRoute` hostname patch in:

```text
deploy/app/overlays/dev/httproute-patch.yaml
```
