# Naming Convention

This document defines the current and recommended naming rules for Campus++
across namespaces, workloads, images, and entry hostnames.

The goal is to keep names stable, environment-aware, and easy to operate even
without a public production domain.

## Guiding Rules

- keep application workload names stable across environments
- encode environment differences in namespaces and entry hostnames, not by
  renaming every workload
- prefer `GW` as the public entry point instead of exposing cluster node IPs to
  users
- keep Kubernetes service names internal and simple
- keep image names aligned with service names

## Kubernetes Resource Naming

Current app resource naming in the repo:

- namespace base: `campus`
- DEV namespace: `campus-dev`
- PROD namespace: `campus-prod`
- frontend deployment/service: `frontend`
- auth deployment/service: `auth`
- backend deployment/service: `backend`
- internal gateway deployment/service: `campus-nginx`
- importer job: `campus-importer`
- shared ingress resource: `campus`

This is a good baseline and should stay stable.

## Image Naming

Container image names should match the workload names:

- `ghcr.io/nexoc/campus-frontend`
- `ghcr.io/nexoc/campus-auth`
- `ghcr.io/nexoc/campus-backend`
- `ghcr.io/nexoc/campus-nginx`
- `ghcr.io/nexoc/campus-importer`

Rules:

- keep one image name per component
- differentiate deployments by tag, not by inventing new image names
- prefer immutable deployment tags for actual rollout decisions

## Hostname Strategy

### Current Confirmed DEV Host

The current DEV overlay uses:

- `campus-dev.192-168-50-5.sslip.io`

This is acceptable as a bootstrap or lab hostname because it resolves directly
to the DEV node IP.

### Recommended Target Strategy

For a maintainable setup, user-facing entry names should terminate on `GW`.

Preferred direction:

- DEV entry hostname points to `GW`
- PROD entry hostname points to `GW`
- `GW` proxies to the appropriate cluster ingress entrypoint

Example pattern:

- `campus-dev.<internal-zone>`
- `campus-prod.<internal-zone>`

If no internal DNS is available yet, use one of these temporary options:

- `/etc/hosts` entries on the client machines
- local LAN DNS if available
- `sslip.io` only as a temporary DEV fallback

## Naming Rules By Layer

At the edge:

- users should access `GW` hostnames, not raw node IPs

In Kubernetes:

- Ingress hostnames should represent the environment entrypoint
- service names should stay environment-neutral
- namespaces should carry the environment separation

In CI and registry:

- images keep stable component names
- tags carry build identity such as `sha-<shortsha>`
- overlays should pin immutable tags for real deployments
- moving tags like `dev-latest` are acceptable as convenience pointers, but
  should not be deployment truth

## Current Repo Alignment

The repo currently reflects:

- stable workload names
- environment-separated namespaces
- DEV hostname defined in the DEV ingress patch
- GHCR image names aligned with the component model

The repo does not yet fully reflect:

- the final `GW` hostname policy
- the final PROD hostname
- the exact internal DNS or hosts-file strategy used by clients

Those items should be documented once the `GW` naming choice is finalized.
