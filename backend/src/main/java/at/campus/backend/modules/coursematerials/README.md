# Module: coursematerials

## Current Role

Handles course material upload, listing, download, update, and deletion.

This module stores metadata in PostgreSQL and file bytes behind a storage
abstraction. The active implementation stores bytes on disk under the
configured root path, which defaults to `/data/course-materials`.

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
- DEV Kubernetes now mounts `/data/course-materials` from a PVC, so pod
  restarts no longer wipe uploaded files
- the current backend storage implementation is still filesystem-based; a later
  object-storage backend can be added behind the same contract
