# ingress-nginx

This directory is legacy/reference material.

`ingress-nginx` is not part of the active Campus++ non-prod release flow.

## Current Status

Current non-prod entry layer:

- Envoy Gateway on NodePort `30080`

This folder remains in the repository only because:

- older rollout history referenced ingress-nginx
- it may still be useful as migration context
- future PROD work may choose to compare against it

## What Is Here

Current files:

- `values-dev.yaml`
- `values-prod.yaml`

Treat these values as historical baselines, not the source of truth for active
lab or home releases.
