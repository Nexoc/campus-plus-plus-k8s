# Campus++ Frontend

Vue 3 SPA for Campus++.

## Current Runtime Role

In the active DEV deployment, the frontend is:

- built into a static image
- served inside Kubernetes
- reached through `campus-nginx`
- exposed externally through Envoy Gateway and `campus.davl.at`

The frontend does not talk to raw backend service URLs. It uses same-origin
paths such as:

- `/auth/*`
- `/api/*`

## Features

- Vue 3 + Vite
- TypeScript
- modular domain structure
- same-origin API access through the gateway
- stateless client-side auth handling

## Project Structure

- `src/`
- `public/`
- `index.html`
- `nginx.conf`
- `Dockerfile`

## Development

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

## Deployment Notes

- production-style traffic goes through `campus-nginx`
- frontend itself is not the public edge service
- the active Kubernetes image is `ghcr.io/nexoc/campus-frontend`
