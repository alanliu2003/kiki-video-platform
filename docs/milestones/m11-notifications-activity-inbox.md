# Milestone 11 — Notifications / Activity Inbox

## Goal

Add a **durable per-user notification inbox** for meaningful social engagement, with unread state and a Vue inbox. Existing likes, favorites, comments, replies, and follows create useful feedback for recipients. PostgreSQL is authoritative. This milestone does **not** add email, mobile push, Web Push, Kafka, or a notification microservice.

## Scope

- Flyway `notifications` table
- create a notification in the same PostgreSQL transaction as a newly inserted like, favorite, comment, reply, or follow
- authenticated list, unread-count, mark-read, and mark-all-read APIs
- joined DTO hydration (actor / video / comment) without N+1 queries
- `/notifications` inbox, nav bell, unread badge, 30s unread polling
- comment hash anchors for inbox deep links
- backend and frontend tests

## Non-goals

Email, mobile push, Web Push, notification preferences, digest emails, moderation queues, production deployment, Kafka, a separate notification service, AI-generated copy, recommendation changes, user chat/DMs, and Milestone 12.

Real-time WebSocket/SSE delivery is deferred. Danmaku sockets stay video-scoped.

## Architecture

```text
Vue
 ├── REST  GET/POST /api/notifications*
 └── 30s unread-count poll
       │
       ▼
Spring Boot API
 ├── Social writes (like / favorite / comment / follow)
 │     └── same transaction → notifications insert
 └── Notification inbox (PostgreSQL only)

PostgreSQL  notifications  source of truth
Redis       unused for notifications
```

Interaction writes still commit likes, favorites, comments, and follows as before. After a row is **actually inserted**, `NotificationService.createIfNotSelf` inserts the inbox row in the **same `@Transactional` method**. Redis counter updates remain after-commit and do not affect notification durability.

## Schema

Flyway `V10__add_notifications.sql`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | BIGSERIAL PK | |
| `recipient_user_id` | BIGINT NOT NULL | inbox owner; FK → `users` |
| `actor_user_id` | BIGINT NULL | who acted; FK → `users` |
| `type` | VARCHAR(32) | constrained enum values |
| `video_id` | BIGINT NULL | FK → `videos` |
| `comment_id` | BIGINT NULL | FK → `comments` |
| `parent_comment_id` | BIGINT NULL | reply parent; FK → `comments` |
| `is_read` | BOOLEAN NOT NULL DEFAULT FALSE | |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `read_at` | TIMESTAMPTZ NULL | set when first marked read |

Indexes:

- `notifications_recipient_created_idx` on `(recipient_user_id, created_at DESC, id DESC)`
- `notifications_recipient_unread_idx` on `(recipient_user_id, is_read)`

Foreign keys match existing social tables (no `ON DELETE CASCADE`). Titles, usernames, and comment bodies are **not** copied into the row. Rows are logical-user based and unrelated to physical media dedupe.

## Notification types

Java enum `NotificationType` / SQL `CHECK`:

| Type | Event | Recipient |
| --- | --- | --- |
| `VIDEO_LIKED` | new `video_likes` row | video owner |
| `VIDEO_FAVORITED` | new `video_favorites` row | video owner |
| `VIDEO_COMMENTED` | new top-level comment | video owner |
| `COMMENT_REPLIED` | new reply | parent comment author |
| `USER_FOLLOWED` | new `user_follows` row | followed user |

Frontend copy is derived from `type` plus hydrated actor/video/comment data. Arbitrary type strings are rejected by the database check.

## Creation semantics

Create only after the underlying durable action succeeds, and only when it created a **new** row.

| Action | Guard | Notification |
| --- | --- | --- |
| Like | `insertIgnore` returned `> 0` | `VIDEO_LIKED` |
| Favorite | `insertIgnore` returned `> 0` | `VIDEO_FAVORITED` |
| Follow | `insertIgnore` returned `> 0` | `USER_FOLLOWED` |
| Top-level comment | always a new comment row | `VIDEO_COMMENTED` |
| Reply | always a new comment row | `COMMENT_REPLIED` to parent author only |

Self-events never notify:

- owner likes or favorites their own video
- owner comments on their own video
- author replies to their own comment
- self-follow remains forbidden (`SELF_FOLLOW_NOT_ALLOWED`) and creates no row

Recipient is derived server-side from the video owner, parent author, or followed user. Clients cannot spoof it.

A reply notifies the parent author only. It does not also notify the video owner unless they are that parent author.

## Transaction / idempotency

Notification insert runs in the same Spring transaction as the triggering write. If the notification insert fails, the like/favorite/follow/comment rolls back. Notifications are not a best-effort after-commit side effect.

Idempotent repeated likes, favorites, and follows (`ON CONFLICT DO NOTHING`) do not insert another notification.

There is no global text/type dedupe. One notification per newly inserted interaction row.

## Unlike / unfollow / delete

Historical notifications **remain** when the source action is removed.

- like → unlike: existing `VIDEO_LIKED` stays
- later like again (new `video_likes` row): a new notification may be created
- follow → unfollow: existing `USER_FOLLOWED` stays
- later follow again: a new notification may be created

This milestone does not delete notifications when comments or videos disappear. If a referenced row is later missing, list hydration uses `LEFT JOIN` and omits or nulls that section.

## Read / unread model

Unread count is `COUNT(*)` of the current user’s rows where `is_read = FALSE`. PostgreSQL is the only copy.

| Action | Behavior |
| --- | --- |
| Mark one read | sets `is_read` and `read_at` if the row belongs to the caller |
| Repeat mark-read | no-op, still 200 |
| Mark another user’s row | `404 NOTIFICATION_NOT_FOUND` (does not leak existence) |
| Mark all read | updates all unread rows for the caller |
| Repeat mark-all | no-op, still 200 |

List order: `created_at DESC`, then `id DESC`. Default page size 20, max 50.

## API

All endpoints require JWT. The current user can only read or modify their own rows.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/notifications?page=0&size=20` | `{ items, page, size, total }` |
| GET | `/api/notifications/unread-count` | `{ unreadCount }` |
| POST | `/api/notifications/{id}/read` | `{ unreadCount }` |
| POST | `/api/notifications/read-all` | `{ unreadCount: 0 }` |

Hydrated item shape:

```json
{
  "id": 123,
  "type": "VIDEO_LIKED",
  "read": false,
  "createdAt": "2026-08-31T08:00:00Z",
  "actor": { "id": 5, "username": "alice", "displayName": "Alice" },
  "video": { "id": 10, "title": "Clip", "thumbnailUrl": "/api/videos/10/thumbnail" },
  "comment": { "id": 20, "contentSnippet": "Nice work" }
}
```

Unused sections are `null`. Comment snippets are truncated to 120 Unicode code points at read time. Raw HTML is never stored or returned as markup.

List and unread-count use one bounded page query plus one indexed count. Actor, video, and comment fields come from a single joined projection.

## Frontend UX

- Route `/notifications` (`requiresAuth`)
- Header “Notifications” link with a red unread badge (authenticated only)
- Inbox: actor, action text, video title, comment snippet, timestamp, thumbnail fallback, unread highlight
- Click marks read, then navigates when a target exists
- “Mark all as read”, load more, loading / empty / error
- Comment bodies and snippets use text interpolation, not `v-html`

Targets:

| Type | Navigation |
| --- | --- |
| `VIDEO_LIKED` / `VIDEO_FAVORITED` | `/videos/{videoId}` |
| `VIDEO_COMMENTED` / `COMMENT_REPLIED` | `/videos/{videoId}#comment-{commentId}` |
| `USER_FOLLOWED` | no public profile route; click marks read only |

Comment list items now have `id="comment-{id}"`. After comments load, the video page scrolls to the hash when that comment is on a loaded page. Comments on later pages are not auto-fetched.

Anonymous users never call notification APIs.

## Real-time vs polling

**Polling.** The authenticated nav fetches unread count on sign-in and every 30 seconds. The inbox refetches on open and after mark-read. WebSocket/SSE was not added so Milestone 7 danmaku rooms stay unchanged. Reconnect is not applicable; a missed poll is recovered by the next REST read. This is **not** claimed as real-time delivery.

## PostgreSQL vs Redis

| Store | Role |
| --- | --- |
| PostgreSQL | only copy of notification rows and unread state |
| Redis | unused for notifications (still used for interaction counters, danmaku, caches) |

A Redis flush or outage cannot lose unread state. Notification read/write does not call Redis.

## Failure behavior

| Failure | Result |
| --- | --- |
| Anonymous notification request | 401 |
| User A reads or marks User B’s inbox | empty list / 404 |
| Notification insert fails | triggering like/comment/follow rolls back |
| Redis down | inbox and social writes still work; counters fail open as in M6 |
| Missing actor/video/comment on hydrate | that DTO section is null; the row still lists |
| Comment hash not on first page | video page opens; no scroll |

## Tests

Backend:

- first like/favorite/follow → one notification; repeat → no duplicate
- self-like / self-comment / self-reply → no notification
- top-level comment → video owner; reply → parent author
- unlike/unfollow leave history; later re-create may add another row
- unread count, mark-read, idempotent mark-read, mark-all
- user cannot mark another user’s notification
- newest-first pagination, `id` secondary order, page-size cap
- actor/video/comment hydration and snippet truncation
- Flyway table and indexes
- anonymous 401

Frontend:

- bell badge when signed in; anonymous nav does not request notifications
- inbox loading / empty / error
- unread/read styling, mark-read navigation, mark-all
- unsafe HTML shown as text
- `/notifications` route guard
- comment DOM ids for hash targets

## Known limitations

- No live push. Badge can be up to ~30 seconds stale until the next poll or inbox visit.
- No public user profile, so follow notifications are not navigable.
- Hash scroll only works if the target comment is already loaded.
- Notifications are not deleted or edited when the source like/follow/comment is removed.
- No notification preferences, grouping, or mute.
- Thumbnail URLs are always `/api/videos/{id}/thumbnail`; the UI falls back if the image 404s.
- Page `total` is the recipient’s full inbox size, not a filtered unread-only list.

## Definition of Done

Durable inbox for like, favorite, comment, reply, and follow; same-transaction creation; no self or duplicate-idempotent notifications; authenticated read APIs; Vue inbox and badge; tests and docs; no `.env` edits; no M12.
