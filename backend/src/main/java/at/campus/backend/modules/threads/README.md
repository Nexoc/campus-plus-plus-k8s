# Module: threads

## Current Role

Handles course discussion threads.

Threads are public for reading and authenticated for creation or mutation.

## Public API

- `GET /api/public/courses/{courseId}/threads`
- `GET /api/public/threads/{threadId}`

## Authenticated API

- `POST /api/courses/{courseId}/threads`
- `PUT /api/threads/{threadId}`
- `DELETE /api/threads/{threadId}`

## Notes

- threads belong to courses
- posts are managed in the separate `posts` module
- reports can reference thread content for moderation
