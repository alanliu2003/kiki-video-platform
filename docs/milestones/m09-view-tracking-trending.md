# Milestone 9 — View Tracking & Trending Discovery

## Goal

Add qualified video view tracking, a durable per-logical-video view count, deterministic trending, a newest-uploads feed, and a discovery-oriented home page. This is **not** personalized recommendation. There is no ML/AI ranking.

## Scope

- qualified-view rule with local watch accumulation
- idempotent `POST /api/videos/{id}/views/qualify`
- `videos.view_count` as the durable aggregate
- Redis viewer dedupe (ephemeral) and trending list cache
- `GET /api/videos/trending` and `GET /api/videos/recent`
- view counts on detail, my-videos, search cards, and home cards
- Vue home feed + player integration

## Non-goals

Personalized recommendations, collaborative filtering, embeddings, Kafka, a recommendation microservice, CDN, notifications, subscriptions-feed redesign, analytics dashboards, watch-history UI, production deployment, and load-test claims.

## Architecture

```text
Vue player
  └─ accumulate watched playback locally
       └─ POST /api/videos/{id}/views/qualify  (once per qualification)
            ├─ Redis SET NX viewer dedupe (fail-open)
            ├─ PostgreSQL unique (video_id, client_view_id)
            └─ atomic UPDATE videos.view_count = view_count + 1

Vue home
  ├─ GET /api/videos/trending  → Redis cache → PostgreSQL score query
  └─ GET /api/videos/recent    → PostgreSQL created_at DESC, id DESC
```

PostgreSQL is authoritative for view totals. Redis never holds the only copy of a view count. A Redis flush cannot destroy durable totals.

Physical media dedupe does **not** merge view counts. Two logical videos that share one `media_object_id` have independent `view_count` values.

## Qualified-view rule

A page load is not a view.

Qualification requires all of:

1. Playback actually started (the client reports accumulated playback time).
2. Accumulated **played** time reaches the threshold.
3. Threshold = `min(VIDEO_VIEW_QUALIFY_SECONDS, VIDEO_VIEW_QUALIFY_PERCENT × duration)`.
   - Defaults: 10 seconds or 25% of duration, whichever is smaller.
   - Unknown / non-positive duration: threshold is 10 seconds.
   - Short videos stay countable via the percent rule (a 4s video qualifies at 1s of playback).

Authoritative duration is `media_objects.duration_seconds` when present and positive. Client `durationMs` is used only as a fallback. Negative, zero, and absurd (>24h) timestamps/durations are ignored or rejected.

The frontend accumulates wall-clock playback deltas from `timeupdate` while the element is playing. Pause, hidden-tab time, and seek jumps (`delta > 1.5s` or `seeking`) do not count. Seeking to the end does not qualify.

The client POSTs at most once per viewing session after the threshold, and retries with the same `clientViewId` if the request fails.

## Dedupe / idempotency

Two independent layers:

| Layer | Key | Purpose | Authority |
| --- | --- | --- | --- |
| Redis viewer window | `kiki:video:{videoId}:view-dedupe:{viewerKey}` | Suppress repeat increments from the same viewer for `VIDEO_VIEW_DEDUPE_TTL` (default 30m) | Ephemeral |
| PostgreSQL idempotency | `video_view_idempotency (video_id, client_view_id)` unique | Retry-safe identical `clientViewId` | Durable |

`viewerKey`:

- authenticated: `u:{userId}`
- anonymous: `a:{uuid}` from HttpOnly `kiki_anon` cookie (issued on first qualify if missing)

No fingerprinting. No client IP/UA stored.

## Anonymous / authenticated handling

Anonymous qualify is public. A missing/invalid anon cookie causes the API to issue a new UUID cookie (`Path=/`, `HttpOnly`, `SameSite=Lax`, 365 days). Authenticated requests use the user id even if a cookie is present. Login after an anonymous qualify can increment again; that is accepted for this milestone.

## PostgreSQL vs Redis

| Data | Store |
| --- | --- |
| Durable view total | `videos.view_count` (atomic `UPDATE … + 1`) |
| Retry idempotency | `video_view_idempotency` |
| Viewer 30-minute window | Redis SET NX + TTL |
| Trending page payload | Redis JSON, TTL `TRENDING_CACHE_TTL` (default 2m) |

Redis failure:

- Playback and unrelated endpoints are unaffected (Redis is excluded from Actuator health).
- Qualify **fails open** on the Redis dedupe check: the request still validates and may increment. Same `clientViewId` remains idempotent via PostgreSQL. Different `clientViewId`s from the same viewer may increment during a Redis outage.
- Trending **falls back to PostgreSQL** when the cache is down or unreadable.
- Cache write failures never roll back a qualify write.

## Trending formula

Deterministic, not ML. Computed in PostgreSQL, not Elasticsearch.

```text
score =
    ln(1 + views)      * TRENDING_VIEW_WEIGHT      (default 3)
  + ln(1 + likes)      * TRENDING_LIKE_WEIGHT      (default 2)
  + ln(1 + favorites)  * TRENDING_FAVORITE_WEIGHT  (default 2)
  + ln(1 + comments)   * TRENDING_COMMENT_WEIGHT   (default 1.5)
  - max(0, ageHours)   * TRENDING_AGE_DECAY        (default 0.02)
```

`log1p` keeps a single metric from dominating linearly. Age decay is 0.02 per hour (~0.48/day) so recency matters without instantly burying all older videos. Secondary order is `id DESC`. Page size is bounded (`TRENDING_MAX_PAGE_SIZE`, default 50).

The SQL uses grouped `COUNT(*)` subqueries for likes/favorites/active comments plus the stored `view_count`. It does not load all videos into Java memory.

## Cache behavior

Key: `kiki:trending:page:{page}:size:{size}`. TTL default 2 minutes. Miss or Redis error → query PostgreSQL → best-effort cache write.

## API endpoints

| Method | Path | Auth | Result |
| --- | --- | --- | --- |
| POST | `/api/videos/{id}/views/qualify` | optional | `{ counted, alreadyCounted, viewCount }` |
| GET | `/api/videos/trending?page=0&size=20` | public | paginated cards |
| GET | `/api/videos/recent?page=0&size=20` | public | newest first, then `id DESC` |

Qualify body:

```json
{ "watchedMs": 12345, "durationMs": 60000, "clientViewId": "<uuid>" }
```

`clientViewId` is required and must be a UUID. `watchedMs` must be `0…24h`. Below-threshold requests return `400 VIEW_NOT_QUALIFIED`. Unknown video: `404 VIDEO_NOT_FOUND`.

Additive DTO fields (existing clients keep working):

- video detail / upload: `viewCount`, `durationSeconds`
- my videos: `viewCount`
- search items: `viewCount` hydrated from PostgreSQL after Elasticsearch hits

## Frontend behavior

Home (`/`) has **Trending** and **New uploads** sections with loading, empty, and error states. Cards show thumbnail (with broken-image fallback), title, creator, duration, compact view count, created date, and optional like count. Cards navigate to `/videos/{id}` using text interpolation only.

The player (HLS and legacy) shares one `QualifiedViewTracker` per video id so processing polls do not re-qualify. Danmaku `timeupdate`/`seek` handlers are unchanged besides also feeding the tracker.

`formatViewCount` is shared (`0 views`, `1 view`, `1.2K views`).

## Tests

Backend: threshold unit tests, qualify increment, idempotent retry, viewer-window dedupe, independent counts for shared media, trending order, newest order, Redis-down fallback, existing video API regressions.

Frontend: formatter tests, tracker threshold/pause/seek/single-POST tests, home feed loading/error/empty/results.

## Known limitations

- Clients can lie about `watchedMs`. This milestone does not attest playback cryptographically.
- Redis-down fail-open can allow extra increments with new `clientViewId`s.
- `video_view_idempotency` grows with successful qualifies; it is not an analytics stream and has no retention job yet.
- Trending is global and identical for every viewer.
- Search view counts are not stored in Elasticsearch; they are joined from PostgreSQL at query time.
- No watch-history UI.

## Definition of Done

See the Milestone 9 request checklist: qualified views, durable logical counts, Redis non-authoritative, deterministic trending + recent feeds, home discovery UI, tests passing, no `.env` edits, no M10 work.
