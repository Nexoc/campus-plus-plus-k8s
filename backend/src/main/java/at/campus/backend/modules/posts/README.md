# Module: posts

## Current Role

Handles posts inside discussion threads.

Posts are a child resource of threads for creation and listing, and a direct
resource for lookup and mutation.

## Public API

- `GET /api/public/threads/{threadId}/posts`
- `GET /api/public/posts/{postId}`

## Authenticated API

- `POST /api/threads/{threadId}/posts`
- `PUT /api/posts/{postId}`
- `DELETE /api/posts/{postId}`

## Ownership Rules

- authenticated users can create posts in threads
- authors can edit or delete their own posts
- moderation logic is enforced downstream of the controller

## Related Modules

- `threads`
- `comments`
- `reports`
