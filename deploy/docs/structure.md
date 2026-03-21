# Deployment Structure

This document describes how the deployment layer of Campus++ is organized.

The deployment layer is separated from the application source code so that Kubernetes and infrastructure artifacts can evolve without forcing a repository reorganization of the services themselves.

## High-Level Layout

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
````

## `app/`

This directory contains Kubernetes manifests for the application itself.

It is intentionally separate from infrastructure configuration.

### `app/base/`

This directory contains the common Kubernetes resources shared by all environments.

Typical contents:

* Namespace
* Deployments
* Services
* Ingress
* Importer Job
* common metadata and labels

Rules:

* keep files environment-agnostic where possible
* do not place dev/prod-specific values directly in base
* use base as the reusable foundation for all overlays

### `app/overlays/dev/`

This directory contains the development overlay.

Typical contents:

* image overrides for dev
* replica overrides for dev
* ingress overrides for dev
* config generation inputs for dev
* secret generation inputs for dev

Rules:

* only place development-specific changes here
* do not duplicate the whole base
* do not place production settings here

### `app/overlays/prod/`

This directory contains the production overlay.

Typical contents:

* image overrides for prod
* replica overrides for prod
* ingress overrides for prod
* config generation inputs for prod
* secret generation inputs for prod

Rules:

* only place production-specific changes here
* keep production isolated from development
* avoid hidden coupling between overlays

## `templates/`

This directory contains example input files and reference templates for configuration and secrets.

These are not meant to hold live environment values.

### `templates/config/`

Non-secret example configuration files.

Purpose:

* document expected config inputs
* support ConfigMap generation
* make env requirements explicit

Examples:

* profile selection
* database host/port/name
* ingress host
* service-specific non-secret settings

### `templates/secrets/`

Secret example files.

Purpose:

* document required secret keys
* support Secret generation workflows
* keep real secret values out of git

Rules:

* examples only
* never commit real credentials or production values

## `infra/`

This directory contains infrastructure-related deployment files.

This is separate from app manifests on purpose.

### `infra/ingress-nginx/`

This directory is intended for ingress-nginx Helm values and related notes.

Purpose:

* keep controller configuration outside app manifests
* keep Helm values isolated from Kustomize application resources
* support future environment-specific infra tuning

## `docs/`

This directory contains short operational documentation for the deployment layer.

Typical contents:

* structure overview
* naming convention
* environment separation notes
* rollout notes

## Separation Principle

The repository follows this rule:

* application code stays in service directories
* deployment artifacts stay in `deploy/`

That means:

* no movement of backend/frontend/auth/importer/nginx source files
* no business-logic changes caused by Kubernetes layout
* no mixing of infra Helm values with app manifests

## Outcome

This structure prepares the repository for:

* Kustomize-based application deployment
* separate dev/prod overlays
* explicit config and secret handling
* future infrastructure growth without chaotic reorganization
