Place active DEV secret env files here for manual `kubectl apply -k deploy/dev`
runs:

- `db-secrets.env`
- `auth-secrets.env`

These files must stay out of git.

The self-hosted DEV deploy workflow normally stages them from:

- `/home/nexoc/campus-secrets/dev/db-secrets.env`
- `/home/nexoc/campus-secrets/dev/auth-secrets.env`
