# Milestone 7 — Danmaku & WebSockets

## Goal

Add Bilibili-style real-time danmaku synchronized to video playback. Viewers of the same video can load a bounded historical window, connect over a video-scoped WebSocket, and — if authenticated — submit danmaku that is persisted in PostgreSQL and broadcast to current viewers.

## Scope

- durable `danmaku` table and Flyway `V6`
- public historical retrieval API
- raw JSON WebSocket at `/ws/videos/{videoId}/danmaku`
- first-message JWT auth (receive-only until AUTH)
- local video-room registry
- Redis Pub/Sub fan-out with same-instance fallback
- frontend overlay, scheduler, reconnect, and ON/OFF toggle
- tests for persistence, WebSocket rooms, Redis outage, and client scheduling

## Non-goals

General chat, DMs, notifications, Elasticsearch, recommendations, moderation dashboards, AI moderation, Spring Cloud Gateway, Eureka, microservice extraction, Kubernetes, CDN, distributed tracing, multi-region WebSocket routing, and Milestone 8.

## Architecture

```text
Vue
 ├── REST  GET /api/videos/{id}/danmaku
 └── WebSocket  /ws/videos/{id}/danmaku
       │
       ▼
Spring Boot API
 ├── Auth/User
 ├── Video
 ├── Social Interactions
 ├── Danmaku WebSocket
 ├── local video-room registry
 ├── PostgreSQL          durable danmaku history
 └── Redis
      ├── interaction counters
      ├── rate limits
      └── danmaku Pub/Sub   transient cross-instance delivery

RocketMQ
   ↓
media-worker
   ↓
FFmpeg
   ↓
MinIO
```

PostgreSQL is the only durable copy of danmaku. Redis Pub/Sub is ephemeral delivery. Historical REST still works if Redis or WebSocket is down. Playback continues if the socket cannot connect.

## Danmaku persistence model

Table `danmaku`:

| Column | Type | Notes |
| --- | --- | --- |
| id | BIGSERIAL | |
| video_id | BIGINT | FK → videos |
| user_id | BIGINT | FK → users |
| content | VARCHAR(200) | trimmed plain text |
| video_time_ms | BIGINT | playback clock, not wall clock |
| style | VARCHAR(20) | `NORMAL` in this milestone |
| status | VARCHAR(20) | `ACTIVE` (schema also allows `DELETED`) |
| client_message_id | VARCHAR(64) | UNIQUE with user_id |
| created_at | TIMESTAMPTZ | |

Indexes: `(video_id, video_time_ms, id)` and `(video_id, created_at)`.

## WebSocket protocol

Endpoint: `/ws/videos/{videoId}/danmaku`

Raw JSON frames. Version field `v: 1` on server messages.

Client → server:

```json
{ "type": "AUTH", "token": "..." }
{ "type": "DANMAKU_SEND", "clientMessageId": "uuid", "content": "hello", "videoTimeMs": 12345 }
```

Server → client:

```json
{ "v": 1, "type": "AUTH_OK" }
{ "v": 1, "type": "DANMAKU", "danmaku": { "...": "canonical record" } }
{ "v": 1, "type": "DANMAKU_ACK", "clientMessageId": "...", "danmakuId": 123 }
{ "v": 1, "type": "ERROR", "code": "DANMAKU_RATE_LIMITED", "message": "..." }
```

Error codes: `DANMAKU_AUTH_REQUIRED`, `DANMAKU_AUTH_FAILED`, `DANMAKU_INVALID_CONTENT`, `DANMAKU_INVALID_TIMESTAMP`, `DANMAKU_INVALID_MESSAGE`, `DANMAKU_RATE_LIMITED`, `VIDEO_NOT_FOUND`, `DANMAKU_INTERNAL_ERROR`.

Ordinary validation errors do not close the socket. Tokens are never logged.

STOMP was not used. An explicit JSON protocol is easier to explain and test for this portfolio project.

## Authentication

The browser WebSocket API cannot set `Authorization` headers. JWT remains in `localStorage`, so the client sends `AUTH` as the first message after connect.

Anonymous connections are allowed and receive-only. Send requires a successful AUTH on that socket. The backend enforces this; the UI only hides the send affordance.

Tradeoff: the access token travels once in a WebSocket frame instead of a query string (URLs are more likely to be logged). This is still XSS-sensitive, matching the existing REST token model.

## Room model

`DanmakuRoomRegistry` keeps a `ConcurrentHashMap<Long, Set<WebSocketSession>>`. Connect adds the session; close, transport error, or failed send removes it. Broadcast is local only. There is one Redis subscription for the process, not one per viewer.

Tomcat / Spring WebSocket ping-pong is used for transport liveness. Stale sessions disappear on close or write failure. No custom application heartbeat is required for Milestone 7.

## Redis Pub/Sub design

Single channel: `kiki:danmaku` (configurable). Payload includes `videoId` and the canonical danmaku DTO.

Option A: persist → Redis publish → every instance, including the publisher, broadcasts from the subscriber. The publishing instance does not also broadcast locally on the happy path.

## Redis failure semantics

If Redis publish fails after PostgreSQL commit:

- the write is already durable
- the API falls back to local room broadcast
- same-instance viewers still see the live message
- other instances miss immediate fan-out
- historical REST recovers the row later

Rate limiting uses `kiki:ratelimit:danmaku:{userId}` and fails open if Redis is down, same as comments.

## Historical retrieval

`GET /api/videos/{videoId}/danmaku?fromMs=0&toMs=30000`

- public
- `fromMs >= 0`
- `toMs > fromMs`
- maximum window `DANMAKU_HISTORY_WINDOW` (default 60s)
- `video_time_ms` half-open range `[fromMs, toMs)`
- default window is `0` to `historyWindow` when params are omitted

The frontend prefetches 60s buckets aligned to 30s (`42s` → `30s–90s`) and does not refetch a loaded bucket.

## Timestamp synchronization

`video_time_ms` is integer milliseconds of `HTMLVideoElement.currentTime`.

Validation:

- always `>= 0`
- if `media_objects.duration_seconds` is known: `<= duration + DANMAKU_TIMESTAMP_TOLERANCE` (default 2s)
- if duration is missing (legacy / unprocessed): `<= DANMAKU_LEGACY_MAX_TIMESTAMP` (default 6h)

Legacy videos are not blocked from playback or danmaku; the upper bound only rejects absurd timestamps.

## Client scheduler

`danmakuScheduler` tracks known IDs, pending items, and last playhead. It spawns items when `lastMs < videoTimeMs <= currentMs`. Seek updates the playhead and drops pending items that are no longer ahead of the new time. Pause prevents spawning. Live WebSocket records and REST history share the same ID set.

The overlay uses CSS transforms. Animation duration does not scale with `playbackRate`; timing of *when* a comment appears still follows `currentTime`.

## Reconnect behavior

Bounded exponential reconnect: 1s, 2s, 4s, 8s, max 10s. Unmount or video change closes the socket and cancels timers. After reconnect the client re-sends AUTH (if logged in) and reloads the current historical window. Only one socket is kept.

## Deduplication

- server: `UNIQUE(user_id, client_message_id)` — retries return the existing row and do not insert again
- client: danmaku `id` is the render key; live + historical repeats are ignored
- send waits for server ACK / broadcast (no optimistic temporary IDs)

## Rate limiting

Default: 10 danmaku / 10 seconds / user. Over-limit returns `DANMAKU_RATE_LIMITED` without disconnecting.

## Frontend overlay

`DanmakuOverlay` assigns horizontal lanes from overlay height. Items animate right → left and unmount on `animationend`. OFF hides the overlay and skips spawning but keeps the socket. Preference is stored in `localStorage` (`kiki.danmaku.enabled`). Overlay works for HLS (`HlsPlayer` exposed video element) and raw `<video>`.

## Tests

- PostgreSQL persistence, window query, validation, idempotent `clientMessageId`
- WebSocket room fan-out, anonymous send rejection, invalid JSON, disconnect cleanup, rate limit
- Redis publish failure → DB success + local fallback; rate-limit fail-open
- Frontend socket lifecycle, backoff, scheduler seek/pause/dedupe, toggle, overlay text rendering

## Manual verification

Two browser windows on the same video should both render a sent comment. A third window on another video should not. Reload and seek should show comments near their `videoTimeMs` without replaying the skipped range. Redis down should still persist and locally broadcast.

## Known limitations

- Multi-instance Redis fan-out is implemented and unit/integration-tested on one JVM; it was not load-tested with two API processes
- CSS animation speed does not track `playbackRate`
- No TOP/BOTTOM style editor, deletion, or moderation
- JWT still lives in `localStorage`
- Custom heartbeat is not implemented beyond container ping/pong

## Definition of Done

See the Milestone 7 prompt checklist: V6 applied, historical + live paths, rooms, Redis fallback, frontend overlay/reconnect/dedupe, existing M1–M6 tests still passing, `.env` untouched.
