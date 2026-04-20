# ingress-nginx

This directory is now legacy/reference material.

`ingress-nginx` is no longer the active DEV entry path for Campus++.

## Current Status

Active DEV entry layer today:

- Envoy Gateway on `192.168.56.40:31080`

This directory remains in the repository because:

- older manifests and rollout notes referenced ingress-nginx
- it may still be useful for comparison or a fallback path
- PROD may still reuse parts of the baseline later

## What Is Here

Current files:

- `values-dev.yaml`
- `values-prod.yaml`

These values are not the active DEV source of truth anymore.

## If You Use It

Treat this folder as:

- historical context
- migration reference
- optional future fallback

Do not treat it as the canonical current DEV path.
