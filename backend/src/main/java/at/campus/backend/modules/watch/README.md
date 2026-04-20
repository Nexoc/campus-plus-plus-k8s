# Module: watch

## Current Role

Handles watch subscriptions and watch status for supported targets.

## Public API

- `GET /api/public/watch/status`

## Authenticated API

- `POST /api/watch`
- `DELETE /api/watch`
- `GET /api/watch`
- `GET /api/watch/status`

## Notes

- watch state is stored in `app.watch_subscriptions`
- notification delivery is still backend-internal and not documented as a
  public external integration yet
- target typing is used so the same module can support multiple watched object
  kinds
