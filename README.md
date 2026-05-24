# Campus++

Campus++ is a Kubernetes / DevOps portfolio project for a full-stack campus
application running in a home lab.

The repository focuses on the operational platform around the app: k3s
clusters, GitHub Actions delivery, external HTTPS routing, database access,
monitoring, and security boundaries.

## Live URLs

- Dev: <https://home-campus-dev.davl.at>
- Prod: <https://home-campus-prod.davl.at>
- Grafana: <https://home-grafana.davl.at> with protected access

## Stack

- Kubernetes / k3s
- Envoy Gateway / Gateway API
- GitHub Actions
- Docker / GHCR
- Ansible
- PostgreSQL
- Prometheus / Grafana
- Nginx
- WireGuard
- Let's Encrypt

## Architecture Summary

The active target is a home-only lab on one physical PC with VM roles:

- `gw`: control host, runner host, Ansible entry point
- `s5-dev`: dev k3s cluster
- `s1-prod`, `s2-prod`, `s3-prod`: prod k3s HA cluster
- `s4-db`: internal PostgreSQL host
- `s6-monitoring`: Prometheus and Grafana host

Public traffic enters through VPS Nginx over HTTPS, crosses WireGuard into the
home lab, reaches k3s NodePort `30080`, then Envoy Gateway routes to the app.

## Verified Status

- Dev deployment is reachable through HTTPS.
- Prod deployment is reachable through HTTPS.
- Prod k3s HA is running.
- The `s4-db` Kubernetes Service and EndpointSlice aliases exist in dev and prod.
- CI/CD for `home-dev-*` and `home-v*` release tags works.
- Monitoring is running with VM, PostgreSQL, and Kubernetes dashboards.
- Grafana is externally reachable only behind Nginx basic auth and Grafana login.
- Prometheus, exporters, PostgreSQL, and the Kubernetes API are not public.

## CI/CD Summary

- `home-dev-*` tags deploy to the dev k3s cluster.
- `home-v*` tags deploy to the prod k3s HA cluster.
- Deployments run through `home-gw-runner`.

## Screenshots

Screenshots will be added later:

- `docs/assets/app-home-preview.png`
- `docs/assets/grafana-vm-overview.png`
- `docs/assets/grafana-kubernetes-overview.png`
- `docs/assets/grafana-postgresql-overview.png`

## Documentation

- [Architecture](docs/architecture.md)
- [Operations](docs/operations.md)
- [Monitoring](docs/monitoring.md)
- [Security](docs/security.md)
