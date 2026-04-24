# DEV Secrets

This directory is used by the active `deploy/app/overlays/dev` flow.

During automated deploys, the self-hosted runner stages secrets here from:

- `/home/nexoc/campus-secrets/dev/`

Expected files:

- `db-secrets.env`
- `auth-secrets.env`
