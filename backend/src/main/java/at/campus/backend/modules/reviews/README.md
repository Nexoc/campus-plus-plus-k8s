# Module: reviews

## Current Role

Manages course reviews, review summaries, and moderation actions on reviews.

## Public API

- `GET /api/public/reviews`
- `GET /api/public/reviews/{id}`
- `GET /api/public/courses/{courseId}/reviews`
- `GET /api/public/courses/{courseId}/reviews/summary`

## Authenticated API

- `POST /api/reviews`
- `PUT /api/reviews/{id}`
- `DELETE /api/reviews/{id}`

## Moderation API

- `GET /api/moderation/reviews`
- `POST /api/moderation/reviews/{id}/flag`
- `POST /api/moderation/reviews/{id}/unflag`
- `DELETE /api/moderation/reviews/{id}`

## Notes

- reviews are attached to courses
- public course pages depend on this module for review lists and summaries
- moderation endpoints are distinct from author-owned review mutation
