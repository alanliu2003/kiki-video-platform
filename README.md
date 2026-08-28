# Video Streaming Platform

A full-stack video streaming platform inspired by Bilibili, built as a portfolio project. The long-term goal is a high-performance system with Vue 3 on the frontend, a Java 21 Spring Boot backend, and supporting infrastructure for storage, messaging, search, media processing, and observability.

This repository is currently at **Milestone 5: Async Media Processing & HLS**. An authenticated user can hash a file, upload it in resumable chunks, skip upload when the same bytes already exist, persist a logical video immediately, and play processed HLS once a separate FFmpeg worker finishes. Raw playback remains available for legacy videos.

## Current status

Milestone 5 is on `milestone-5-media-processing-hls`. The repository includes:

- a modular Spring Boot API with Flyway, MyBatis, Spring Security, JWT access tokens, MinIO, and a transactional processing outbox
- a separate `media-worker` process that consumes RocketMQ events and runs FFmpeg
- registration, login, and `GET /api/users/me`
- chunked resumable upload with SHA-256 physical deduplication
- a legacy authenticated multipart upload that now also creates/links a media object
- public video detail, HLS playback, thumbnail, and raw Range playback
- a Vue 3 + Vite frontend with processing-state UI, polling, and hls.js
- Docker Compose for PostgreSQL, MinIO, Redis, and RocketMQ
- architecture and development documentation

## Current architecture

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
 └── MinIO SDK ─────────► MinIO (raw + processed)
                              ▲
media-worker ── RocketMQ ─────┘
     │
     └── FFmpeg / FFprobe
```

Redis is still unused by application code. New uploads store physical bytes at `raw/{sha256}` and share processed HLS at `processed/{mediaObjectId}/`.

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
WebSocket
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
docker-compose.yml       Local PostgreSQL, MinIO, Redis, RocketMQ
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

PostgreSQL, MinIO, and RocketMQ are required for the full Milestone 5 path.

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

The Vite dev server proxies `/api` to `http://localhost:8080`.

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

## Current limitations

- Resume after refresh requires re-selecting the same local file
- HLS is API-proxied; there is no CDN or signed URL layer
- Not every uploaded codec will transcode successfully
- Access tokens last one hour; refresh tokens are not implemented
- Frontend stores JWTs in `localStorage` (simple, XSS-sensitive)
- Redis is started locally but is not used by application code
- No comments, likes, follows, danmaku, search, gateway, or CI/CD
- No performance claims or production deployment yet

## Documentation

- [Architecture](docs/architecture.md)
- [Local development](docs/development.md)
- [Milestone 1](docs/milestones/m01-foundation.md)
- [Milestone 2](docs/milestones/m02-auth-user.md)
- [Milestone 3](docs/milestones/m03-video-core-minio.md)
- [Milestone 4](docs/milestones/m04-chunked-resumable-upload.md)
- [Milestone 5](docs/milestones/m05-media-processing-hls.md)
