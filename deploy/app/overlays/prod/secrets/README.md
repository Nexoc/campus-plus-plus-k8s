# PROD Secrets Fallback

This directory is an ignored local fallback for manual `deploy/app/overlays/prod`
rendering.

During automated deploys, real PROD secret values are read from the deployment
host when `CAMPUS_SECRETS_ROOT` is set, then staged into a temporary overlay copy
outside the repo checkout.

Expected app secret files:

- `db-secrets.env`
- `auth-secrets.env`

The external PostgreSQL endpoint is not staged here. It is read from:

```text
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

`apply-overlay.sh` uses that host-local runtime config to render `service/s4-db`
and `endpointslice/s4-db` for the `campus-prod` namespace. Do not commit real
secret values or environment-specific endpoint addresses.
