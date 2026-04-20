# Module: comments

## Current Role

Handles comments for discussion posts.

In the current DEV runtime, this module is part of the `backend` service in
`campus-dev` and is reached only through `campus-nginx`.

## Public API

- `GET /api/public/posts/{postId}/comments`
- `GET /api/public/comments/{commentId}`

## Authenticated API

- `POST /api/posts/{postId}/comments`
- `PUT /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`

## Ownership Rules

- authenticated users can create comments
- authors can update or delete their own comments
- moderation rules are enforced in the service layer

## Notes

- comments are attached to posts, not directly to threads or courses
- backend authentication context is supplied by the upstream gateway
