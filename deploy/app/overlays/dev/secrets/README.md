# DEV Secrets

This directory is for local, ignored secret env files used by the DEV overlay.

Expected local files:

- `db-secrets.env`
- `auth-secrets.env`

Create them from the canonical templates:

- `deploy/templates/secrets/db-secrets.env.example`
- `deploy/templates/secrets/auth-secrets.env.example`

Example:

```bash
cp deploy/templates/secrets/db-secrets.env.example deploy/app/overlays/dev/secrets/db-secrets.env
cp deploy/templates/secrets/auth-secrets.env.example deploy/app/overlays/dev/secrets/auth-secrets.env
```

These local files must not be committed to git.
