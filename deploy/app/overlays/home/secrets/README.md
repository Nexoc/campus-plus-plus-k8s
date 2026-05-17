# HOME Secrets Fallback

This directory is an ignored local fallback for manual `deploy/app/overlays/home`
rendering.

During automated deploys, the home `gw` control runner reads secrets from:

- `/home/nexoc/campus-secrets/home/`

and stages them into a temporary overlay copy outside the repo checkout.

Expected files:

- `db-secrets.env`
- `auth-secrets.env`

Current assumptions for the `home` overlay:

- it mirrors the lab namespace and service layout
- it uses the home DEV hostname from `httproute-patch.yaml`
- config defaults were copied from `dev` and should be reviewed before the
  first real `home-dev-*` release if the home environment differs
