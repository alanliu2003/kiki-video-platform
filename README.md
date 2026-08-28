# Video Streaming Platform

A full-stack video streaming platform inspired by Bilibili, built as a portfolio project. The long-term goal is a high-performance system with Vue 3 on the frontend, a Java 21 Spring Boot backend, and supporting infrastructure for storage, messaging, search, media processing, and observability.

This repository is currently at **Milestone 4: Chunked & Resumable Uploads**. An authenticated user can hash a file, upload it in resumable chunks, skip upload when the same bytes already exist, persist a logical video in PostgreSQL, and play it through the API.

## Current status

Milestone 4 is complete on `milestone-4-chunked-resumable-upload`. The repository includes:

- a modular Spring Boot API with Flyway, MyBatis, Spring Security, JWT access tokens, and MinIO object storage
- registration, login, and `GET /api/users/me`
- chunked resumable upload with SHA-256 physical deduplication
- a legacy authenticated multipart upload kept for compatibility
- public video detail, streamed playback, and current-user video listing
- a Vue 3 + Vite frontend with register/login/profile plus chunked upload, my-videos, and video playback
- Docker Compose for PostgreSQL, MinIO, and Redis
- architecture and development documentation

## Current architecture

```text
Vue 3
 │
 │ JWT / chunked upload / video requests
 ▼
Spring Boot
 │
 ├── Auth
 ├── User
 ├── Upload
 └── Video
      │
      ├── MyBatis ──────► PostgreSQL
      │
      └── MinIO SDK ────► MinIO
```

Redis is still unused by application code. Raw MP4/WebM playback through the API is still temporary. New uploads store physical bytes at `raw/{sha256}` and can share that object across logical videos.

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
  common/                Shared constants and primitives
  api/                   Executable HTTP API
frontend/                Vue 3 + TypeScript + Vite application
infra/                   Future Docker and deployment assets
docs/                    Architecture, development, and milestone notes
scripts/                 Local helper scripts
docker-compose.yml       Local PostgreSQL, MinIO, and Redis
```

## Prerequisites

- Java 21 or newer (the project compiles to Java 21)
- Maven 3.9+ or the included Maven Wrapper (`backend/mvnw`)
- Node.js 20+ and npm
- Docker and Docker Compose
- Git

## Local setup

1. Clone the repository.
2. Create local environment files from the examples:

   ```bash
   cp .env.example .env
   cp frontend/.env.example frontend/.env
   ```

3. Do not commit `.env` files. The examples contain local development defaults only.

## Start infrastructure

From the repository root:

```bash
docker compose up -d
```

On Windows PowerShell you can also run:

```powershell
.\scripts\start-infra.ps1
```

PostgreSQL and MinIO are required for the API to start.

## Start backend

```bash
cd backend
./mvnw -pl api -am spring-boot:run
```

On Windows:

```powershell
cd backend
.\mvnw.cmd -pl api -am spring-boot:run
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
| Spring Boot Actuator | 8080 (`/actuator/health`) |
| PostgreSQL | 5432 |
| MinIO API | 9000 |
| MinIO console | 9001 |
| Redis | 6379 |

## Current limitations

- Resume after refresh requires re-selecting the same local file
- Playback is the original uploaded file, proxied by the API; no HLS or transcoding
- Browser playback depends on the codec inside the MP4/WebM container
- Access tokens last one hour; refresh tokens are not implemented
- Frontend stores JWTs in `localStorage` (simple, XSS-sensitive)
- Redis is started locally but is not used by application code
- No comments, likes, follows, danmaku, search, messaging, gateway, or CI/CD
- No performance claims or production deployment yet

## Documentation

- [Architecture](docs/architecture.md)
- [Local development](docs/development.md)
- [Milestone 1](docs/milestones/m01-foundation.md)
- [Milestone 2](docs/milestones/m02-auth-user.md)
- [Milestone 3](docs/milestones/m03-video-core-minio.md)
- [Milestone 4](docs/milestones/m04-chunked-resumable-upload.md)
