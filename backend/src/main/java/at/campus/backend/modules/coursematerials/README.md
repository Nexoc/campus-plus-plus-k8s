# Module: coursematerials

## Current Role

Handles course material upload, listing, download, update, and deletion.

This module stores metadata in PostgreSQL and file bytes on disk under
`/data/course-materials`.

## API

Course-scoped endpoints:

- `POST /api/courses/{courseId}/materials`
- `GET /api/courses/{courseId}/materials`

Material management endpoints:

- `GET /api/materials/{id}/download`
- `PUT /api/materials/{id}`
- `DELETE /api/materials/{id}`

## Notes

- upload/download is not public-edge storage; it is handled by the backend
- metadata lives in `app.course_materials`
- current implementation depends on backend-local filesystem storage
