# DEV Secrets Fallback

This directory is an ignored local fallback for legacy/manual
`deploy/app/overlays/dev` rendering.

Active home dev releases use `deploy/app/overlays/home`, not this legacy
`dev` overlay.

If this legacy/manual `dev` overlay is rendered through `apply-overlay.sh` with
`CAMPUS_SECRETS_ROOT` set, secrets are staged from:

- `/home/nexoc/campus-secrets/dev/`

and stages them into a temporary overlay copy outside the repo checkout.

Expected files:

- `db-secrets.env`
- `auth-secrets.env`
