# Module: reports

## Current Role

Handles moderation reports for backend content.

Users can submit reports, and moderator/admin endpoints are exposed for
reviewing and resolving them.

## Authenticated User API

- `POST /api/reports`

## Moderation API

- `GET /api/moderation/reports`
- `GET /api/moderation/reports/count/pending`
- `GET /api/moderation/reports/{id}`
- `POST /api/moderation/reports/{reportId}/resolve`
- `PATCH /api/moderation/reports/{id}`
- `DELETE /api/moderation/reports/{id}`

## Notes

- reports can target review, post, or thread content
- moderation endpoints are separate from the user submission endpoint
- current auth flow is upstream-first: `campus-nginx` validates identity and
  the backend consumes trusted headers
