# PROD Secrets

This directory is for local, ignored secret env files used by the PROD overlay.

Expected local files:

- `db-secrets.env`
- `auth-secrets.env`

Create them from the canonical templates:

- `deploy/templates/secrets/db-secrets.env.example`
- `deploy/templates/secrets/auth-secrets.env.example`

Example:

```powershell
Copy-Item deploy\templates\secrets\db-secrets.env.example deploy\app\overlays\prod\secrets\db-secrets.env
Copy-Item deploy\templates\secrets\auth-secrets.env.example deploy\app\overlays\prod\secrets\auth-secrets.env
```

These local files must not be committed to git.
