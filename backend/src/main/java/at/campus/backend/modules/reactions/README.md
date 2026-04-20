# Module: reactions

## Current Role

Handles simple reactions for posts and reviews.

## Public API

- `GET /api/public/posts/{postId}/reactions`
- `GET /api/public/reviews/{reviewId}/reactions`

## Authenticated API

- `POST /api/posts/{postId}/reactions`
- `DELETE /api/posts/{postId}/reactions`
- `POST /api/reviews/{reviewId}/reactions`
- `DELETE /api/reviews/{reviewId}/reactions`

## Notes

- reactions are stored in `app.reactions`
- the module supports multiple target types through a target-type model
- public endpoints expose counts/state for rendering in the UI
