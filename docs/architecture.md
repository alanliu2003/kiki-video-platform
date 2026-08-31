# Architecture

This document describes the intended system shape. Milestone 8 adds Elasticsearch video search on top of Milestone 7 danmaku.

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
 ├── PostgreSQL
 └── Redis
      ├── interaction counters
      ├── rate limits
      └── danmaku Pub/Sub

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

PostgreSQL is the durable source of truth for users, videos, interactions, and danmaku history. Elasticsearch is a rebuildable search projection of video metadata — never business-authoritative. Redis is acceleration and ephemeral coordination: hot counters, rate limits, and danmaku Pub/Sub. Redis is never the only copy of durable danmaku.

Logical videos reference a physical `media_object`. Processing state lives on that shared row so identical uploads are transcoded once. Raw sources stay at `raw/{sha256}`. Processed HLS lives at `processed/{mediaObjectId}/`.

### Frontend

The frontend is a Vue 3 + TypeScript application using Vite, Vue Router, Pinia, Axios, and hls.js. In local development, Vite proxies `/api` and `/ws` to the Spring Boot process.

Auth state lives in a Pinia store. The access token is stored in `localStorage` and is sent as `Authorization: Bearer <token>` on REST calls. WebSocket auth uses a first-message `AUTH` frame because the browser cannot set that header. Upload and my-videos routes are guarded on the client; video detail and `/search` are public. Backend security is authoritative. The browser never talks to Elasticsearch.

Video detail polls playback metadata every 4 seconds while media is `PENDING` or `PROCESSING`. READY HLS uses native MSE/HLS when available, otherwise hls.js. Legacy or unprocessed media uses `/api/videos/{id}/content`. The same page shows like/favorite/follow controls, comments, and a danmaku overlay synchronized to `HTMLVideoElement.currentTime`. Anonymous users can read counts, comments, and danmaku; writes redirect to login or are rejected by the socket.

### Backend

The backend is a multi-module Maven project:

- `common` holds shared constants, media events, and HLS helpers
- `api` is the executable HTTP application, media outbox publisher, and search projector
- `media-worker` is the executable FFmpeg consumer

The API currently exposes:

- `GET /api/health` — application liveness
- `POST /api/auth/register` — create a user
- `POST /api/auth/login` — issue a JWT access token
- `GET /api/search/videos` — public video search (`q` required)
- `GET /api/users/me` — current user, JWT required
- `POST /api/videos` — legacy authenticated multipart upload (now creates/links a media object)
- `POST /api/uploads/init` — start or resume a chunked upload
- `GET /api/uploads/{uploadId}` — owner-only session status
- `PUT /api/uploads/{uploadId}/chunks/{chunkIndex}` — upload one chunk
- `POST /api/uploads/{uploadId}/complete` — assemble, persist, schedule processing, return immediately
- `GET /api/videos/{videoId}` — public video metadata including `processingStatus`
- `GET /api/videos/{videoId}/interactions` — public counts plus optional current-user like/favorite flags
- `PUT` / `DELETE /api/videos/{videoId}/like` — authenticated like/unlike
- `PUT` / `DELETE /api/videos/{videoId}/favorite` — authenticated favorite/unfavorite
- `GET` / `POST /api/videos/{videoId}/comments` — public list, authenticated create/reply
- `GET /api/videos/{videoId}/danmaku` — public historical danmaku for a bounded playback window
- `GET /ws/videos/{videoId}/danmaku` — video-scoped WebSocket (anonymous receive, AUTH to send)
- `GET /api/users/{userId}/relationship` — public follower count plus optional current-user follow flag
- `PUT` / `DELETE /api/users/{userId}/follow` — authenticated follow/unfollow
- `GET /api/videos/{videoId}/playback` — HLS or original playback metadata
- `GET /api/videos/{videoId}/hls/**` — API-proxied HLS assets
- `GET /api/videos/{videoId}/thumbnail` — API-proxied JPEG thumbnail
- `GET /api/videos/{videoId}/content` — public streamed raw playback with HTTP Range
- `GET /api/users/me/videos` — current user's videos, JWT required
- Spring Boot Actuator `/actuator/health` — process health

The worker exposes only `/actuator/health` on port 8081.

Users, video metadata, upload sessions, media objects, the processing outbox, the search-index outbox, likes, favorites, follows, comments, and danmaku are stored in PostgreSQL. Schema changes are applied by Flyway. SQL access uses plain MyBatis mapper annotations. Redis stores integer interaction counters, short-lived rate-limit keys, and transient danmaku Pub/Sub events. Elasticsearch stores only a derived video search index.

Video files are stored in MinIO. The API generates object keys and proxies playback. The bucket is not anonymously writable or publicly listed. Temporary upload parts use `uploads/{uploadId}/chunks/{index}`. Deduplicated finals use `raw/{sha256}`. Processed assets use `processed/{mediaObjectId}/`. Legacy Milestone 3 objects remain at `videos/{userId}/{uuid}.ext`.

Spring Security is stateless. The JWT filter reconstructs the principal from token claims and does not create an HTTP session.

### Infrastructure

`docker-compose.yml` starts named-volume services for local development:

- PostgreSQL — users, videos, media objects, outboxes, interactions, danmaku
- MinIO — raw and processed objects
- Redis — hot interaction counters, rate limits, and danmaku Pub/Sub
- RocketMQ NameServer + Broker — media processing events
- Elasticsearch — video search projection (`kiki-videos` → `kiki-videos-v1`)

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
| Edge / reverse proxy | Nginx | Not started |
| API gateway | Spring Cloud Gateway | Not started |
| Relational data | PostgreSQL | Users, videos, media, outbox, interactions, danmaku |
| Cache / sessions | Redis | Hot counters, rate limits, danmaku Pub/Sub |
| Object storage | MinIO, later maybe FastDFS | Raw + processed objects |
| Messaging | RocketMQ | Media processing events |
| Search | Elasticsearch | Video metadata projection |
| Danmaku | WebSocket | Video-scoped raw JSON rooms |
| Media processing | FFmpeg / HLS | Worker process |
| CI / delivery | Jenkins, Docker images | Not started |
| Observability | metrics, logs, tracing | Not started |

## Design principles for later work

- Keep the backend modular until service boundaries are proven. Do not extract microservices early.
- Keep Elasticsearch a projection. Do not write business-authoritative video state only to Elasticsearch. Redis remains cache, rate-limit, and Pub/Sub — not durable danmaku storage.
- Keep secrets out of Git. Local values belong in untracked `.env` files.
- Prefer a working monolith with clear modules over a distributed system that is hard to run locally.
- Treat PostgreSQL as the source of truth for user identity, video metadata, social interactions, and danmaku. Search results must remain rebuildable from PostgreSQL.
- Keep `VideoStorage` as the boundary for object storage so later media pipelines can replace or wrap MinIO without rewriting controllers.
