# Architecture

This document describes the intended system shape. Milestone 5 adds asynchronous FFmpeg HLS processing on top of Milestone 4 chunked upload.

## Current architecture

The backend is still a modular monolith plus one worker process, not a set of microservices.

```text
Vue 3
 │
 │ JWT / chunked upload / video / HLS requests
 ▼
Spring Boot API
 │
 ├── Auth / User / Upload / Video
 ├── Outbox publisher ──► RocketMQ
 ├── MyBatis ───────────► PostgreSQL
 └── MinIO SDK ─────────► MinIO
                              ▲
media-worker ── RocketMQ ─────┘
     └── FFmpeg / FFprobe
```

Redis is still unused by application code.

Logical videos reference a physical `media_object`. Processing state lives on that shared row so identical uploads are transcoded once. Raw sources stay at `raw/{sha256}`. Processed HLS lives at `processed/{mediaObjectId}/`.

### Frontend

The frontend is a Vue 3 + TypeScript application using Vite, Vue Router, Pinia, Axios, and hls.js. In local development, Vite proxies `/api` to the Spring Boot process.

Auth state lives in a Pinia store. The access token is stored in `localStorage` and is sent as `Authorization: Bearer <token>`. Upload and my-videos routes are guarded on the client; video detail is public. Backend security is authoritative.

Video detail polls playback metadata every 4 seconds while media is `PENDING` or `PROCESSING`. READY HLS uses native MSE/HLS when available, otherwise hls.js. Legacy or unprocessed media uses `/api/videos/{id}/content`.

### Backend

The backend is a multi-module Maven project:

- `common` holds shared constants, media events, and HLS helpers
- `api` is the executable HTTP application and outbox publisher
- `media-worker` is the executable FFmpeg consumer

The API currently exposes:

- `GET /api/health` — application liveness
- `POST /api/auth/register` — create a user
- `POST /api/auth/login` — issue a JWT access token
- `GET /api/users/me` — current user, JWT required
- `POST /api/videos` — legacy authenticated multipart upload (now creates/links a media object)
- `POST /api/uploads/init` — start or resume a chunked upload
- `GET /api/uploads/{uploadId}` — owner-only session status
- `PUT /api/uploads/{uploadId}/chunks/{chunkIndex}` — upload one chunk
- `POST /api/uploads/{uploadId}/complete` — assemble, persist, schedule processing, return immediately
- `GET /api/videos/{videoId}` — public video metadata including `processingStatus`
- `GET /api/videos/{videoId}/playback` — HLS or original playback metadata
- `GET /api/videos/{videoId}/hls/**` — API-proxied HLS assets
- `GET /api/videos/{videoId}/thumbnail` — API-proxied JPEG thumbnail
- `GET /api/videos/{videoId}/content` — public streamed raw playback with HTTP Range
- `GET /api/users/me/videos` — current user's videos, JWT required
- Spring Boot Actuator `/actuator/health` — process health

The worker exposes only `/actuator/health` on port 8081.

Users, video metadata, upload sessions, media objects, and the processing outbox are stored in PostgreSQL. Schema changes are applied by Flyway. SQL access uses plain MyBatis mapper annotations.

Video files are stored in MinIO. The API generates object keys and proxies playback. The bucket is not anonymously writable or publicly listed. Temporary upload parts use `uploads/{uploadId}/chunks/{index}`. Deduplicated finals use `raw/{sha256}`. Processed assets use `processed/{mediaObjectId}/`. Legacy Milestone 3 objects remain at `videos/{userId}/{uuid}.ext`.

Spring Security is stateless. The JWT filter reconstructs the principal from token claims and does not create an HTTP session.

### Infrastructure

`docker-compose.yml` starts named-volume services for local development:

- PostgreSQL — users, videos, media objects, outbox
- MinIO — raw and processed objects
- Redis — unused by application code
- RocketMQ NameServer + Broker — media processing events

Elasticsearch is intentionally absent.

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
WebSocket
```

Possible future responsibilities:

| Area | Planned technology | Status |
| --- | --- | --- |
| Web UI | Vue 3 | Auth + chunked upload + HLS playback |
| API | Java 21 + Spring Boot | Auth + video + outbox publisher |
| Edge / reverse proxy | Nginx | Not started |
| API gateway | Spring Cloud Gateway | Not started |
| Relational data | PostgreSQL | Users, videos, media, outbox |
| Cache / sessions | Redis | Container only |
| Object storage | MinIO, later maybe FastDFS | Raw + processed objects |
| Messaging | RocketMQ | Media processing events |
| Search | Elasticsearch | Not started |
| Danmaku | WebSocket | Not started |
| Media processing | FFmpeg / HLS | Worker process |
| CI / delivery | Jenkins, Docker images | Not started |
| Observability | metrics, logs, tracing | Not started |

## Design principles for later work

- Keep the backend modular until service boundaries are proven. Do not extract microservices early.
- Introduce Redis, Elasticsearch, and extra infrastructure only when a later milestone needs them.
- Keep secrets out of Git. Local values belong in untracked `.env` files.
- Prefer a working monolith with clear modules over a distributed system that is hard to run locally.
- Treat PostgreSQL as the source of truth for user identity and video metadata.
- Keep `VideoStorage` as the boundary for object storage so later media pipelines can replace or wrap MinIO without rewriting controllers.
