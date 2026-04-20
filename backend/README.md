# Campus++ Backend

Spring Boot backend service for Campus++.

## Current Runtime Role

In the active DEV deployment, the backend:

- runs in Kubernetes in namespace `campus-dev`
- is reached only through `campus-nginx`
- connects to PostgreSQL on `192.168.56.20`
- runs Flyway migrations for the app schema

It is not intended to be exposed directly to the public internet.

## Responsibilities

- application business logic
- public and protected REST endpoints
- PostgreSQL persistence
- Flyway schema migration
- trust upstream identity headers instead of parsing JWT directly

## Port

- internal container port: `8080`

## Required Environment Variables

- `SPRING_PROFILES_ACTIVE`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

## Profiles

- `dev`
- `test`
- `prod`

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

The backend image is built from `backend/Dockerfile`.

Active Kubernetes image name:

- `ghcr.io/nexoc/campus-backend`

## Health Check

Health endpoint:

- `/actuator/health`

## Notes

- the backend trusts `campus-nginx` as the upstream security boundary
- current DEV rollout uses `deploy/dev/`, not the older overlay path
