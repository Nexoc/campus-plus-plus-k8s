# DEV Secrets Fallback

This directory is an ignored local fallback for legacy/manual
`deploy/app/overlays/dev` rendering.

Active home dev releases use `deploy/app/overlays/home`.

During automated deploys, the self-hosted runner reads secrets from:

- `/home/nexoc/campus-secrets/dev/`

and stages them into a temporary overlay copy outside the repo checkout.

Expected files:

- `db-secrets.env`
- `auth-secrets.env`
