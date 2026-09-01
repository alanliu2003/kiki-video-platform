# Video Streaming Platform

A full-stack video streaming platform inspired by Bilibili, built as a portfolio project. The long-term goal is a high-performance system with Vue 3 on the frontend, a Java 21 Spring Boot backend, and supporting infrastructure for storage, messaging, search, media processing, and observability.

This repository is currently at **Milestone 11: Notifications / Activity Inbox**. Signed-in users get a durable inbox when someone likes, favorites, comments, replies, or follows. Unread state lives in PostgreSQL. The nav badge polls; this is not real-time push. Milestone 10 recommendations are unchanged. PostgreSQL remains authoritative. Redis only caches.

## Current status

Milestone 10 is on `milestone-10-personalized-recommendations`. The repository includes:

- a modular Spring Boot API with Flyway, MyBatis, Spring Security, JWT access tokens, MinIO, Redis, WebSocket danmaku rooms, a transactional processing outbox, and a search-index outbox
- a separate `media-worker` process that consumes RocketMQ events and runs FFmpeg
- registration, login, and `GET /api/users/me`
- chunked resumable upload with SHA-256 physical deduplication
- likes, favorites, follows, comments, and replies
- Redis cache-aside counters with PostgreSQL as the source of truth
- video-scoped danmaku WebSocket, historical retrieval, and Redis Pub/Sub fan-out
- public video detail, HLS playback, thumbnail, raw Range playback, and danmaku overlay
- Elasticsearch video search with highlighting, filters, pagination, and index rebuild
- qualified view tracking, durable logical `view_count`, deterministic trending, and newest-uploads feed
- deterministic personalized recommendations for signed-in users, with cold-start fallback to trending/recent
- durable notification inbox for likes, favorites, comments, replies, and follows, with unread state and a Vue inbox
- a Vue 3 + Vite frontend with processing-state UI, interaction controls, comments, danmaku, `/search`, a discovery home page, and `/notifications`
- Docker Compose for PostgreSQL, MinIO, Redis, RocketMQ, and Elasticsearch
- architecture and development documentation

## Current architecture

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
 ├── Recommendations
 ├── Notifications
 ├── PostgreSQL
 └── Redis

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

PostgreSQL is authoritative for users, videos, interactions, danmaku, logical view counts, authenticated qualified-view history, and notification inbox rows. Elasticsearch is a rebuildable search projection, not business truth. Redis caches hot counts, publishes live danmaku, holds short-lived view-dedupe keys, and caches trending/recommendation pages. New uploads store physical bytes at `raw/{sha256}` and share processed HLS at `processed/{mediaObjectId}/`.

**Future target (not implemented yet):**

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

See [docs/architecture.md](docs/architecture.md) for details.

## Repository structure

```text
backend/                 Modular Spring Boot application
  common/                Shared constants and media contracts
  api/                   Executable HTTP API
  media-worker/          Executable FFmpeg HLS worker
frontend/                Vue 3 + TypeScript + Vite application
infra/                   RocketMQ broker config and future assets
docs/                    Architecture, development, and milestone notes
scripts/                 Local helper scripts
docker-compose.yml       Local PostgreSQL, MinIO, Redis, RocketMQ, Elasticsearch
```

## Prerequisites

- Java 21 or newer (the project compiles to Java 21)
- Maven 3.9+ or the included Maven Wrapper (`backend/mvnw`)
- Node.js 20+ and npm
- Docker and Docker Compose
- Git
- `ffmpeg` and `ffprobe` on PATH for a local (non-Docker) worker

## Local setup

1. Clone the repository.
2. Create local environment files from the examples:

   ```bash
   cp .env.example .env
   cp frontend/.env.example frontend/.env
   ```

3. Copy any new keys from `.env.example` into your existing `.env`. Do not commit `.env` files.

## Start infrastructure

From the repository root:

```bash
docker compose up -d
```

On Windows PowerShell you can also run:

```powershell
.\scripts\start-infra.ps1
```

PostgreSQL and MinIO are required for the API. Redis is used for interaction counters but the API continues if Redis is down. RocketMQ is required for the full media-processing path. Elasticsearch is required for search; uploads still succeed if it is down.

## Start backend

```bash
cd backend
./mvnw -pl api -am spring-boot:run
```

In a second terminal:

```bash
cd backend
./mvnw -pl media-worker -am spring-boot:run
```

Windows:

```powershell
cd backend
.\mvnw.cmd -pl api -am spring-boot:run
.\mvnw.cmd -pl media-worker -am spring-boot:run
```

## Start frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/ws` to `http://localhost:8080`.

## Ports

| Service | Port |
| --- | --- |
| Frontend (Vite) | 5173 |
| Backend API | 8080 |
| Media worker Actuator | 8081 |
| PostgreSQL | 5432 |
| MinIO API | 9000 |
| MinIO console | 9001 |
| Redis | 6379 |
| RocketMQ NameServer | 9876 |
| RocketMQ Broker | 10911 |
| Elasticsearch | 9200 |

## Current limitations

- Resume after refresh requires re-selecting the same local file
- HLS is API-proxied; there is no CDN or signed URL layer
- Not every uploaded codec will transcode successfully
- Access tokens last one hour; refresh tokens are not implemented
- Frontend stores JWTs in `localStorage` (simple, XSS-sensitive)
- Redis is an accelerator, not the source of truth for interactions
- Counters written while Redis is down may stay stale until TTL expires after Redis restarts
- Standard analyzer only; no Chinese plugin
- No comment/danmaku deletion, email/push notifications, gateway, or CI/CD
- No performance claims or production deployment yet

## Documentation

- [Architecture](docs/architecture.md)
- [Local development](docs/development.md)
- [Milestone 1](docs/milestones/m01-foundation.md)
- [Milestone 2](docs/milestones/m02-auth-user.md)
- [Milestone 3](docs/milestones/m03-video-core-minio.md)
- [Milestone 4](docs/milestones/m04-chunked-resumable-upload.md)
- [Milestone 5](docs/milestones/m05-media-processing-hls.md)
- [Milestone 6](docs/milestones/m06-social-interactions-redis.md)
- [Milestone 7](docs/milestones/m07-danmaku-websocket.md)
- [Milestone 8](docs/milestones/m08-elasticsearch-video-search.md)
- [Milestone 9](docs/milestones/m09-view-tracking-trending.md)
- [Milestone 10](docs/milestones/m10-personalized-recommendations.md)
- [Milestone 11](docs/milestones/m11-notifications-activity-inbox.md)
