# PROD Secrets

Real PROD secret values are staged from the deployment host when
`CAMPUS_SECRETS_ROOT` is set.

Expected staged files:

- `db-secrets.env`
- `auth-secrets.env`

The external PostgreSQL endpoint is not staged here. It is read from:

```text
/home/nexoc/campus-secrets/prod/db-endpoint.env
```

`apply-overlay.sh` uses that host-local runtime config to render `service/s4-db`
and `endpointslice/s4-db` for the `campus-prod` namespace. Do not commit real
secret values or environment-specific endpoint addresses.
