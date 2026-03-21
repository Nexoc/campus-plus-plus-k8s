# Deployment Layer

This directory contains deployment artifacts for Campus++.

Its purpose is to keep Kubernetes, Kustomize, infrastructure values, and deployment-related templates separate from the application source code.

The application code remains unchanged in:
- `frontend/`
- `auth/`
- `backend/`
- `importer/`
- `nginx/`

## Structure

```text
deploy/
├── app/
│   ├── base/
│   └── overlays/
│       ├── dev/
│       └── prod/
├── templates/
│   ├── config/
│   └── secrets/
├── infra/
│   └── ingress-nginx/
└── docs/