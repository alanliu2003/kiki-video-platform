# Architecture

This document describes the intended system shape. Milestone 3 adds the first video upload and playback slice on top of Milestone 2 authentication.

## Current architecture

The backend is still a single modular application, not a set of microservices.

```text
Vue 3
 │
 │ JWT / multipart upload / video requests
 ▼
Spring Boot
 │
 ├── Auth
 ├── User
 └── Video
      │
      ├── MyBatis ──────► PostgreSQL
      │
      └── MinIO SDK ────► MinIO
```

Redis is still unused by application code.

Raw MP4/WebM playback through the API is temporary. Future milestones will add chunked/resumable upload, async media processing, FFmpeg, HLS, and adaptive streaming. Those are not implemented yet.

### Frontend

The frontend is a Vue 3 + TypeScript application using Vite, Vue Router, Pinia, and Axios. In local development, Vite proxies `/api` to the Spring Boot process.

Auth state lives in a Pinia store. The access token is stored in `localStorage` and is sent as `Authorization: Bearer <token>`. Upload and my-videos routes are guarded on the client; video detail is public. Backend security is authoritative.

The native `<video>` element requests `/api/videos/{id}/content` directly. Playback does not go through Axios.

### Backend

The backend is a multi-module Maven project:

- `common` holds shared constants and simple response types
- `api` is the executable Spring Boot application

The API currently exposes:

- `GET /api/health` — application liveness
- `POST /api/auth/register` — create a user
- `POST /api/auth/login` — issue a JWT access token
- `GET /api/users/me` — current user, JWT required
- `POST /api/videos` — authenticated multipart upload
- `GET /api/videos/{videoId}` — public video metadata
- `GET /api/videos/{videoId}/content` — public streamed playback with HTTP Range
- `GET /api/users/me/videos` — current user's videos, JWT required
- Spring Boot Actuator `/actuator/health` — process health

Users and video metadata are stored in PostgreSQL. Schema changes are applied by Flyway. SQL access uses plain MyBatis mapper annotations.

Video files are stored in MinIO. The API generates object keys and proxies playback. The bucket is not anonymously writable or publicly listed.

Spring Security is stateless. The JWT filter reconstructs the principal from token claims and does not create an HTTP session.

### Infrastructure

`docker-compose.yml` starts named-volume services for local development:

- PostgreSQL — users and video metadata
- MinIO — raw uploaded video objects
- Redis — unused by application code

RocketMQ and Elasticsearch are intentionally absent.

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
| Web UI | Vue 3 | Auth + first video pages |
| API | Java 21 + Spring Boot | Auth + video vertical slice |
| Edge / reverse proxy | Nginx | Not started |
| API gateway | Spring Cloud Gateway | Not started |
| Relational data | PostgreSQL | Users and video metadata |
| Cache / sessions | Redis | Container only |
| Object storage | MinIO, later maybe FastDFS | Raw video objects |
| Messaging | RocketMQ | Not started |
| Search | Elasticsearch | Not started |
| Danmaku | WebSocket | Not started |
| Media processing | FFmpeg / HLS | Not started |
| CI / delivery | Jenkins, Docker images | Not started |
| Observability | metrics, logs, tracing | Not started |

## Design principles for later work

- Keep the backend modular until service boundaries are proven. Do not extract microservices early.
- Introduce Redis, RocketMQ, Elasticsearch, and FFmpeg when a milestone needs them.
- Keep secrets out of Git. Local values belong in untracked `.env` files.
- Prefer a working monolith with clear modules over a distributed system that is hard to run locally.
- Treat PostgreSQL as the source of truth for user identity and video metadata.
- Keep `VideoStorage` as the boundary for object storage so later media pipelines can replace or wrap MinIO without rewriting controllers.
