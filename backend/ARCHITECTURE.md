# Backend Architecture — Campus++

## Purpose

This document describes the current backend architecture as it exists in the
repository and in the active DEV deployment.

It is not a greenfield design note anymore. It reflects the implemented
service, package structure, and runtime boundaries.

## Runtime Position

The backend is not exposed directly to the public internet.

Current request path:

`Internet -> davl.at -> DEV 192.168.56.40:31080 -> Envoy Gateway -> campus-nginx -> backend`

Trust boundary:

- public TLS and public hostname terminate on `davl.at`
- Envoy Gateway is the Kubernetes entry layer
- `campus-nginx` is the internal application gateway
- backend consumes trusted upstream headers and does not act as the public edge

## Service Context

Campus++ currently runs with these main services:

- `frontend` — Vue SPA
- `auth` — Spring Boot authentication service
- `backend` — Spring Boot domain/data service
- `campus-nginx` — internal app gateway and auth boundary
- `campus-importer` — one-shot data import job
- PostgreSQL on `192.168.56.20`

The backend connects to PostgreSQL directly and runs Flyway migrations for the
`app` schema during startup.

## Security Model

The backend does not perform primary authentication itself.

Current model:

- `auth` handles login, registration, token issuance, and token validation
- `campus-nginx` enforces access using upstream auth checks
- backend receives trusted identity data through headers and maps it into
  `UserContext`

This keeps auth logic centralized and avoids duplicating JWT handling across
domain modules.

## Code Structure

The backend is organized around domain modules instead of a single global
controller/service/repository split.

Top-level areas:

- `modules/`
- `config/`
- `security/`
- `common/`

Typical module layout:

```text
<module>/
├── api/
├── service/
├── repository/
├── model/
└── README.md
```

Responsibilities:

- `api/` exposes HTTP endpoints
- `service/` holds business rules and authorization checks
- `repository/` performs persistence access
- `model/` contains domain models and DTOs

## Current Domain Modules

Implemented modules present in the codebase:

- `studyprograms`
- `courses`
- `reviews`
- `favourites`
- `threads`
- `posts`
- `comments`
- `reports`
- `reactions`
- `watch`
- `coursematerials`

Special case:

- `coursesuggestions` currently has a database table but no active backend
  implementation module

## Persistence Style

The backend currently uses:

- Flyway migrations as the schema contract
- PostgreSQL as the primary data store
- repository-based persistence access
- explicit SQL and JDBC-centric repositories for much of the data layer

The project is not a pure JPA-first design. Schema ownership lives in the
migrations.

## Cross-Cutting Concerns

Shared logic outside modules includes:

- `config/` for app, web, JSON, and JDBC setup
- `security/` for `UserContext` extraction and request-scoped identity handling
- `common/` for exception handling, utilities, and debug helpers

## Current Operational Notes

- active Spring profile in DEV is `dev`
- backend listens on container port `8080`
- DEV deploy runs in namespace `campus-dev`
- current active deployment path is `deploy/dev/`
- public traffic reaches backend only through `campus-nginx`

## Known Gaps

- the single-node DEV cluster is operationally unstable at times
- some newer domain areas exist in code but are not yet documented in deeper
  functional detail outside their module READMEs
- `coursesuggestions` remains only partially realized
