# HOME Secrets

This directory is used by the active `deploy/app/overlays/home` flow.

During automated deploys, the self-hosted home runner stages secrets here from:

- `/home/nexoc/campus-secrets/home/`

Expected files:

- `db-secrets.env`
- `auth-secrets.env`

Current assumptions for the `home` overlay:

- it mirrors the lab namespace and service layout
- it uses the placeholder hostname from `httproute-patch.yaml`
- config defaults were copied from `dev` and should be reviewed before the
  first real `home-*` release if the home environment differs
