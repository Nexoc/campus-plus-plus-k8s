# HOME Secrets Fallback

This directory is an ignored local fallback for manual `deploy/app/overlays/home`
rendering.

During automated deploys, the home `gw` control runner reads secrets from:

- `/home/nexoc/campus-secrets/home/`

and stages them into a temporary overlay copy outside the repo checkout.

Expected files:

- `db-secrets.env`
- `auth-secrets.env`

The external PostgreSQL endpoint is not staged here. `apply-overlay.sh` reads it
from the host-local secrets root to render `service/s4-db` and
`endpointslice/s4-db` in `campus-dev`:

```text
/home/nexoc/campus-secrets/home/db-endpoint.env
```

Current assumptions for the `home` overlay:

- it mirrors the lab namespace and service layout
- it uses the home DEV hostname from `httproute-patch.yaml`
- config defaults were copied from `dev` and should be reviewed before the
  first real `home-dev-*` release if the home environment differs
