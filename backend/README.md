# Campus++ Backend

Backend service of the Campus++ application.

This service contains the core business logic and is deployed behind the internal NGINX gateway.
Authentication is handled externally by the auth service and NGINX via forwarded trusted headers.

## Responsibilities

- provide business logic for application features
- expose public and protected API endpoints
- use PostgreSQL as persistence layer
- run Flyway migrations for the backend schema
- trust forwarded identity headers from NGINX
- not parse JWT tokens directly

## Runtime

The backend is normally started through the root `docker-compose.yml` as part of the full stack.

Internal container port:
- `8080`

## Required Environment Variables

- `SPRING_PROFILES_ACTIVE`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

## Profiles

Supported Spring profiles:
- `dev`
- `test`
- `prod`

Profiles are injected via environment variables and are not hardcoded in the runtime model.

## Local Build

```bash
cd backend
mvn clean package
```

## Local Run

```bash
cd backend
mvn spring-boot:run
```

## Docker

The backend image is built from:

```text
backend/Dockerfile
```

The container exposes:

* `8080`

## Health Check

The container health check uses:

```text
/actuator/health
```

## Notes

* backend is not meant to be exposed directly to the public internet
* authentication and request validation are enforced upstream
* the canonical runtime reference is the root `docker-compose.yml`