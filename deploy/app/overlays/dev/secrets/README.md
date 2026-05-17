# DEV Secrets Fallback

This directory is an ignored local fallback for manual `deploy/app/overlays/dev`
rendering.

During automated deploys, the self-hosted runner reads secrets from:

- `/home/nexoc/campus-secrets/dev/`

and stages them into a temporary overlay copy outside the repo checkout.

Expected files:

- `db-secrets.env`
- `auth-secrets.env`
