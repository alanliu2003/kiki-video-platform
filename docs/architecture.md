# Architecture

This document describes the intended system shape. Milestone 13 adds presigned object delivery and production-like packaging on top of Milestone 12 observability.

## Current architecture

The backend is still a modular monolith plus one worker process, not a set of microservices.

```text
Vue
 ├── REST
 └── WebSocket
       │
       ▼
Spring Boot API
 ├── Auth/User
 ├── Video/Upload
 ├── Social
 ├── Danmaku
 ├── Search
 │     └── Elasticsearch
 ├── Discovery / views
 ├── Recommendations
 ├── Notifications
 ├── Observability / Actuator
 ├── PostgreSQL
 └── Redis
      ├── interaction counters
      ├── rate limits
      ├── danmaku Pub/Sub
      ├── view-dedupe keys
      ├── trending cache
      └── recommendation page cache

PostgreSQL
   ↓
search projection outbox
   ↓
Elasticsearch

RocketMQ
   ↓
media-worker
   ↓
FFmpeg
   ↓
MinIO
```

PostgreSQL is the durable source of truth for users, videos, interactions, danmaku history, logical view counts, authenticated qualified-view history, and notification inbox rows. Elasticsearch is a rebuildable search projection of video metadata — never business-authoritative. Redis is acceleration and ephemeral coordination: hot counters, rate limits, danmaku Pub/Sub, view-dedupe keys, and short trending/recommendation caches. Redis is never the only copy of durable view totals, preference data, danmaku, or unread notifications.

Logical videos reference a physical `media_object`. Processing state lives on that shared row so identical uploads are transcoded once. Raw sources stay at `raw/{sha256}`. Processed HLS lives at `processed/{mediaObjectId}/`.

### Frontend

The frontend is a Vue 3 + TypeScript application using Vite, Vue Router, Pinia, Axios, and hls.js. In local development, Vite proxies `/api` and `/ws` to the Spring Boot process.

Auth state lives in a Pinia store. The access token is stored in `localStorage` and is sent as `Authorization: Bearer <token>` on REST calls. WebSocket auth uses a first-message `AUTH` frame because the browser cannot set that header. Upload, my-videos, and `/notifications` routes are guarded on the client; home, video detail, and `/search` are public. Backend security is authoritative. The browser never talks to Elasticsearch. Authenticated home adds a deterministic “Recommended for you” section; anonymous home still shows only trending and newest uploads. This is not machine learning. Signed-in users see a notification bell with an unread badge polled from PostgreSQL; there is no live notification socket.

Video detail polls playback metadata every 4 seconds while media is `PENDING` or `PROCESSING`. READY HLS uses native MSE/HLS when available, otherwise hls.js. The player consumes `GET /api/videos/{id}/playback` (`url` / `mode`). In `presigned` mode, HLS playlists are still fetched from the API (rewritten) and segments/legacy bytes come from short-lived MinIO URLs. Proxy mode keeps the M12 API byte paths. The same page shows like/favorite/follow controls, comments, and a danmaku overlay synchronized to `HTMLVideoElement.currentTime`. Anonymous users can read counts, comments, and danmaku; writes redirect to login or are rejected by the socket. If an initial delivery URL fails, the page refetches the playback descriptor once.

### Backend

The backend is a multi-module Maven project:

- `common` holds shared constants, media events, and HLS helpers
- `api` is the executable HTTP application, media outbox publisher, and search projector
- `media-worker` is the executable FFmpeg consumer

The API currently exposes:

- `GET /api/health` — application liveness
- `GET /actuator/health` — process and dependency health (optional deps may be `DEGRADED` without taking the API down)
- `GET /actuator/metrics` and `GET /actuator/prometheus` — local scrape (not for public production)
- `POST /api/auth/register` — create a user
- `POST /api/auth/login` — issue a JWT access token
- `GET /api/search/videos` — public video search (`q` required)
- `GET /api/videos/trending` — deterministic public trending page
- `GET /api/videos/recent` — newest logical videos
- `GET /api/recommendations/videos` — authenticated deterministic personalized ranking
- `POST /api/videos/{videoId}/views/qualify` — optional-auth qualified view (idempotent)
- `GET /api/users/me` — current user, JWT required
- `POST /api/videos` — legacy authenticated multipart upload (now creates/links a media object)
- `POST /api/uploads/init` — start or resume a chunked upload
- `GET /api/uploads/{uploadId}` — owner-only session status
- `PUT /api/uploads/{uploadId}/chunks/{chunkIndex}` — upload one chunk
- `POST /api/uploads/{uploadId}/complete` — assemble, persist, schedule processing, return immediately
- `GET /api/videos/{videoId}` — public video metadata including `processingStatus` and `viewCount`
- `GET /api/videos/{videoId}/interactions` — public counts plus optional current-user like/favorite flags
- `PUT` / `DELETE /api/videos/{videoId}/like` — authenticated like/unlike
- `PUT` / `DELETE /api/videos/{videoId}/favorite` — authenticated favorite/unfavorite
- `GET` / `POST /api/videos/{videoId}/comments` — public list, authenticated create/reply
- `GET /api/videos/{videoId}/danmaku` — public historical danmaku for a bounded playback window
- `GET /ws/videos/{videoId}/danmaku` — video-scoped WebSocket (anonymous receive, AUTH to send)
- `GET /api/users/{userId}/relationship` — public follower count plus optional current-user follow flag
- `PUT` / `DELETE /api/users/{userId}/follow` — authenticated follow/unfollow
- `GET /api/videos/{videoId}/playback` — HLS or legacy playback descriptor (API playlist URL plus optional presigned content/thumbnail)
- `GET /api/videos/{videoId}/hls/**` — rewritten HLS playlists; TS bytes may be proxied or referenced via presigned URLs
- `GET /api/videos/{videoId}/thumbnail` — API-proxied JPEG thumbnail (card lists still use this)
- `GET /api/videos/{videoId}/content` — public streamed raw playback with HTTP Range (fallback)
- `GET /api/users/me/videos` — current user's videos, JWT required
- `GET /api/notifications` — current user's inbox, JWT required
- `GET /api/notifications/unread-count` — current user's unread badge, JWT required
- `POST /api/notifications/{id}/read` — mark one owned notification read
- `POST /api/notifications/read-all` — mark the current user's inbox read

The worker exposes Actuator on port 8081 (`/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`). Important HTTP responses include `X-Request-ID`. IDs belong in logs; they are not metric tags.

Users, video metadata, upload sessions, media objects, the processing outbox, the search-index outbox, likes, favorites, follows, comments, danmaku, logical view counts, authenticated qualified-view history, and notifications are stored in PostgreSQL. Schema changes are applied by Flyway. SQL access uses plain MyBatis mapper annotations. Redis stores integer interaction counters, short-lived rate-limit keys, view-dedupe keys, short trending/recommendation caches, and transient danmaku Pub/Sub events. Elasticsearch stores only a derived video search index. View totals live on `videos.view_count`; Redis is never the only copy.

Video files are stored in MinIO. The API generates object keys. `VideoStorage` persists objects; `MediaDeliveryService` issues client URLs. The bucket stays private. In `presigned` mode the API signs short-lived GET URLs using `MINIO_PUBLIC_ENDPOINT`. Temporary upload parts use `uploads/{uploadId}/chunks/{index}`. Deduplicated finals use `raw/{sha256}`. Processed assets use `processed/{mediaObjectId}/`. Legacy Milestone 3 objects remain at `videos/{userId}/{uuid}.ext`.

Spring Security is stateless. The JWT filter reconstructs the principal from token claims and does not create an HTTP session.

### Infrastructure

`docker-compose.yml` starts named-volume services for local development:

- PostgreSQL — users, videos, media objects, outboxes, interactions, danmaku
- MinIO — raw and processed objects
- Redis — hot interaction counters, rate limits, danmaku Pub/Sub, and short discovery caches
- RocketMQ NameServer + Broker — media processing events
- Elasticsearch — video search projection (`kiki-videos` → `kiki-videos-v1`)

`docker-compose.prod.yml` is an overlay that adds packaged API, worker, and Caddy. It reuses the same named volumes. It is not a cloud platform.

Local Elasticsearch runs single-node with security disabled. That is LOCAL DEVELOPMENT ONLY.

## Future target architecture

Later milestones may evolve toward the following shape. These components do **not** exist yet.

```text
Vue
 ↓
Nginx
 ↓
Spring Cloud Gateway
 ↓
Spring services
 ↓
PostgreSQL
Redis
RocketMQ
Elasticsearch
MinIO / FastDFS
FFmpeg workers
```

Possible future responsibilities:

| Area | Planned technology | Status |
| --- | --- | --- |
| Web UI | Vue 3 | Auth + chunked upload + HLS + interactions + danmaku |
| API | Java 21 + Spring Boot | Auth + video + outbox publisher + interactions + danmaku WS |
| Edge / reverse proxy | Caddy (production-like Compose) | Local overlay only; not a cloud edge |
| API gateway | Spring Cloud Gateway | Not started |
| Relational data | PostgreSQL | Users, videos, media, outbox, interactions, danmaku, qualified views, notifications |
| Cache / sessions | Redis | Hot counters, rate limits, danmaku Pub/Sub, short discovery caches |
| Object storage | MinIO, later maybe FastDFS | Raw + processed objects |
| Messaging | RocketMQ | Media processing events |
| Search | Elasticsearch | Video metadata projection |
| Danmaku | WebSocket | Video-scoped raw JSON rooms |
| Media processing | FFmpeg / HLS | Worker process |
| CI / delivery | Docker images + Compose overlay | Local packaging only |
| Observability | Actuator + Micrometer Prometheus + k6 | Local scrape and load scripts; no K8s/Grafana stack |

## Design principles for later work

- Keep the backend modular until service boundaries are proven. Do not extract microservices early.
- Keep Elasticsearch a projection. Do not write business-authoritative video state only to Elasticsearch. Redis remains cache, rate-limit, and Pub/Sub — not durable danmaku storage.
- Keep secrets out of Git. Local values belong in untracked `.env` files.
- Prefer a working monolith with clear modules over a distributed system that is hard to run locally.
- Treat PostgreSQL as the source of truth for user identity, video metadata, social interactions, and danmaku. Search results must remain rebuildable from PostgreSQL.
- Keep `VideoStorage` as the boundary for object storage so later media pipelines can replace or wrap MinIO without rewriting controllers.
- Keep `MediaDeliveryService` as the boundary for client URLs. Do not expose bucket names or object keys to the frontend.
