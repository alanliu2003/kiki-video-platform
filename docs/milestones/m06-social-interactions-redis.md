# Milestone 6 — Social Interactions & Redis

## Goal

Add the first real Redis use and the core social interaction layer around videos: likes, favorites, follows, comments, replies, public counts, and current-user state. PostgreSQL remains the source of truth. Redis accelerates hot counters.

## Scope

- video likes and favorites
- creator follows
- comments and one-level replies
- interaction counts and current-user flags
- Redis cache-aside counters with write-through updates
- comment create rate limiting (20/minute/user, fail open)
- frontend interaction controls on the video detail page
- backend and frontend tests

## Non-goals

Danmaku, WebSockets, realtime push, notifications, Elasticsearch, recommendations, personalized feed, Spring Cloud Gateway, Eureka, microservice extraction, Kubernetes, Jenkins, CDN, advanced moderation, content reporting, creator analytics, distributed tracing, comment deletion, and infinite threaded trees.

## Architecture

```text
Vue 3
  │
  ▼
Spring Boot API
  │
  ├── Auth/User
  ├── Video/Playback
  ├── Upload
  ├── Media Processing Outbox
  └── Social Interactions
       │
       ├── PostgreSQL — source of truth
       └── Redis — hot counters/cache

RocketMQ
  ↓
media-worker
  ↓
FFmpeg / HLS
  ↓
MinIO
```

Interaction writes commit in PostgreSQL first. Redis is updated after commit. A Redis failure never rolls back a durable like, favorite, follow, or comment.

## Database schema

Flyway `V5__add_social_interactions.sql`:

| Table | Key | Notes |
| --- | --- | --- |
| `video_likes` | `(user_id, video_id)` | one like per user per video |
| `video_favorites` | `(user_id, video_id)` | one favorite per user per video |
| `user_follows` | `(follower_user_id, followed_user_id)` | CHECK prevents self-follow |
| `comments` | `id` | `parent_comment_id` nullable, status `ACTIVE`/`DELETED` |

Indexes: `video_likes(video_id)`, `video_favorites(video_id)`, `user_follows(followed_user_id)`, `comments(video_id, created_at)`, `comments(parent_comment_id, created_at)`.

There is no stored `follower_count` column. Counts are derived with `COUNT(*)` and cached in Redis.

## Like semantics

- Authenticated only
- `INSERT ... ON CONFLICT DO NOTHING`
- Repeated like is idempotent
- Unlike when not liked is a no-op
- Counter increments/decrements only when a row is actually inserted/deleted

## Favorite semantics

Same idempotency model as likes.

## Follow semantics

- One follow relationship per pair
- Self-follow is rejected by the service and a database CHECK
- Repeated follow/unfollow is safe
- Follower count belongs to the followed user

## Comment model

- Authenticated create, public read
- Content is required, trimmed, max 2000 characters
- Replies use `POST /api/videos/{videoId}/comments` with `parentCommentId`
- Parent must exist, be `ACTIVE`, belong to the same video, and be top-level
- UI shows one reply level even though the table has a parent reference
- Top-level comments are newest first; replies are oldest first
- Pagination: `GET /api/videos/{id}/comments?page=0&size=20`, max size 50
- `total` is the number of top-level comments
- Video `commentCount` includes all `ACTIVE` comments (top-level + replies)
- Soft-delete status exists; deletion API is not implemented
- Comment bodies are returned as text; the Vue UI interpolates them as text, not HTML

## Redis key design

Prefix: `kiki:`

| Key | Value | Purpose |
| --- | --- | --- |
| `kiki:video:{videoId}:like-count` | integer string | like counter |
| `kiki:video:{videoId}:favorite-count` | integer string | favorite counter |
| `kiki:video:{videoId}:comment-count` | integer string | active comment counter |
| `kiki:user:{userId}:follower-count` | integer string | follower counter |
| `kiki:ratelimit:comment:{userId}` | integer string | comment rate window |

TTL default: 10 minutes (`REDIS_INTERACTION_TTL`). Values are integers/strings only. JWT, passwords, and comment bodies are never stored in Redis.

## Cache-aside strategy

Read:

1. GET the counter key
2. hit → return
3. miss → `COUNT(*)` in PostgreSQL (video counts use one query for likes, favorites, and comments)
4. SET with TTL
5. return

Normal cache misses are not logged.

## Write/update strategy

1. PostgreSQL write commits
2. If the insert/delete actually changed a row, update Redis after commit
3. Increment/decrement only when the key already exists
4. Decrement never stores a value below 0
5. If the key is missing or the Redis update fails, refresh from PostgreSQL `COUNT(*)` or invalidate

Viewer flags (`likedByCurrentUser`, `favoritedByCurrentUser`, `followedByCurrentUser`) are not cached. They are per-user lookups against PostgreSQL.

## Redis failure behavior

- API startup does not require Redis. Redis is excluded from aggregated Actuator health.
- Reads: Redis error → PostgreSQL fallback
- Writes: PostgreSQL commit succeeds even if Redis update fails
- Comment rate limiting fails open if Redis is unavailable
- The request does not return 500 because a cache is down

## API endpoints

| Method | Path | Auth | Result |
| --- | --- | --- | --- |
| GET | `/api/videos/{id}/interactions` | optional | counts + viewer flags |
| PUT | `/api/videos/{id}/like` | required | updated interaction summary |
| DELETE | `/api/videos/{id}/like` | required | updated interaction summary |
| PUT | `/api/videos/{id}/favorite` | required | updated interaction summary |
| DELETE | `/api/videos/{id}/favorite` | required | updated interaction summary |
| GET | `/api/users/{id}/relationship` | optional | follower count + viewer flag |
| PUT | `/api/users/{id}/follow` | required | updated relationship |
| DELETE | `/api/users/{id}/follow` | required | updated relationship |
| GET | `/api/videos/{id}/comments` | public | paginated top-level comments with replies |
| POST | `/api/videos/{id}/comments` | required | created comment or reply |

Anonymous viewer flags are always `false`. Counts remain public.

Comment create is limited to 20 requests per user per minute when Redis is available. Over limit returns `429 RATE_LIMITED`.

## Frontend behavior

`VideoDetailView` composes:

- `InteractionBar` / `LikeButton` / `FavoriteButton`
- `CreatorCard` / `FollowButton`
- `CommentsSection` / `CommentForm` / `CommentList` / `ReplyForm`

Anonymous users can read counts and comments. Protected actions redirect to `/login?redirect=...`. Like/favorite/follow use optimistic UI and roll back if the request fails. After success, the UI applies the server summary so counters do not drift.

## Tests

Backend: unauthenticated writes rejected, like/favorite/follow idempotency, self-follow rejected, comment create/reply/pagination, anonymous summary flags, Redis cache-aside populate, idempotent counter consistency, no negative counts, Redis-down HTTP path still works (default test Redis port is unreachable).

Frontend: counts render, authenticated like/favorite/follow toggles, unauthenticated like redirects to login, comment list/submit, reply submit, like API failure rollback.

## Manual verification

1. Like a video, refresh, unlike, refresh.
2. Favorite/unfavorite with refresh.
3. Follow another creator, refresh, unfollow.
4. Comment as user A, reply as user B, read anonymously, confirm anonymous post is blocked.
5. Interact, stop Redis, confirm counts still load from PostgreSQL, restart Redis, confirm keys repopulate with TTL.

## Known limitations

- Comment deletion is not implemented
- Only one visible reply level
- No comment-list cache
- Rate limiting is comment-create only and fails open
- Redis is an accelerator, not a write-behind store
- Writes that happen while Redis is down cannot invalidate existing keys. After Redis restarts from AOF, a stale counter can survive until TTL expires (default 10 minutes). PostgreSQL remains correct; the next cache miss reloads from `COUNT(*)`.
- No notifications or realtime updates

## Definition of Done

See the Milestone 6 request checklist: durable interactions, Redis acceleration, fail-open cache, frontend controls, existing media tests still passing, and no `.env` edits.
