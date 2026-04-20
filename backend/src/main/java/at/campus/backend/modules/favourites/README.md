# Module: favourites

## Current Role

Manages user favourites for both courses and study programs.

This module is authenticated-only. User identity is taken from the backend
user context, not from request payloads.

## API

Course favourites:

- `GET /api/favourites`
- `POST /api/favourites`
- `DELETE /api/favourites/{courseId}`

Study program favourites:

- `GET /api/favourites/study-programs`
- `POST /api/favourites/study-programs`
- `DELETE /api/favourites/study-programs/{studyProgramId}`

## Ownership Rules

- users manage only their own favourites
- course and study program IDs are passed in requests
- current user ID comes from upstream-authenticated context

## Notes

- this module does not expose public read-only endpoints
- it depends on existing course and study program records
