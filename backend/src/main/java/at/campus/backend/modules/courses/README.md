# Module: courses

## Current Role

Serves the course catalog and admin course management endpoints.

In the active deployment, public requests come through:

`Envoy Gateway -> campus-nginx -> backend -> courses module`

## Public API

- `GET /api/public/courses`
- `GET /api/public/courses/{id}`

The public list endpoint supports filtering and pagination in the controller.

## Admin API

- `POST /api/courses`
- `PUT /api/courses/{id}`
- `DELETE /api/courses/{id}`

## Notes

- this module owns course-facing backend APIs
- study program relations are handled together with course data
- auth is enforced upstream and the backend trusts forwarded identity headers
