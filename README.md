# Campus++

[![CI Pipeline](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Nexoc/campus-plus-plus-k8s/actions/workflows/ci.yml)

Campus++ is a containerized full-stack application for Hochschule Campus Wien.

It consists of:
- frontend
- auth service
- backend
- importer
- NGINX gateway
- PostgreSQL

## Documentation

- [SRS](docs/SRS.md)
- [Requirements](docs/requirements.md)

## Quick Start

### Prerequisites
- Docker
- Docker Compose v2
- free port `80`

### Configuration
The project uses one canonical `docker-compose.yml`.

Configure the required environment variables, for example:
- `BACKEND_PROFILE`
- `AUTH_PROFILE`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION`
- `HOST`

`HOST` is currently used during frontend build in some local/WSL setups.

### Start
```bash
docker compose up -d --build
````

Open:

* `http://localhost`

### Stop

```bash
docker compose down -v
```

## Architecture

```text
Client
  ↓
NGINX Gateway
  ├── Frontend
  ├── Auth Service
  └── Backend API
       ↓
    PostgreSQL
```

## Services

### Frontend

* Vue 3 SPA
* served as static files
* sends JWT in `Authorization: Bearer <token>`

### NGINX

* single entry point
* serves frontend
* routes requests
* validates protected requests via `auth_request`
* forwards trusted identity headers to backend

### Auth Service

* Spring Boot
* login / registration
* password hashing
* JWT issuing and validation
* Flyway migrations

### Backend

* Spring Boot
* business logic only
* protected behind NGINX
* trusts forwarded headers

### Importer

* one-shot import service for initial course data

### PostgreSQL

* shared relational database
* used by auth and backend
* schema managed by Flyway

## Profiles

Spring profiles:

* `dev`
* `test`
* `prod`

Profiles are provided via environment variables, not hardcoded in the application runtime model.

## CI

GitHub Actions pipeline includes:

* auth unit tests + coverage
* backend build
* Docker Compose smoke test
* NGINX config validation

## Project Structure

```text
campus-plus-plus-k8s/
├── auth/
├── backend/
├── frontend/
├── importer/
├── nginx/
├── docs/
├── docker-compose.yml
├── set-host-env.sh
└── README.md
```

## Notes

* backend does not parse JWT directly
* NGINX is the central security gate
* importer is import-only, not scraping
* scraper repo:

  * [https://github.com/loonaarc/campuswiki_coursescraper](https://github.com/loonaarc/campuswiki_coursescraper)



