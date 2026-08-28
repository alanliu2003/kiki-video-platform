# Milestone 1 — Foundation

## Goal

Create a clean, professional monorepo foundation for a Bilibili-inspired video streaming platform. This milestone is about repository structure, local development scaffolding, and Git readiness. It does not implement product features.

## Scope

- Inspect the empty workspace and initialize Git
- Create backend, frontend, infra, docs, and scripts directories
- Scaffold a Java 21 Spring Boot multi-module Maven project
- Expose `GET /api/health`
- Scaffold a Vue 3 + TypeScript + Vite frontend
- Add Docker Compose for PostgreSQL and MinIO, plus Redis as low-cost scaffolding
- Establish environment-file conventions
- Add `.gitignore` and `.editorconfig`
- Document architecture, local setup, and this milestone
- Keep work on `milestone-1-foundation`

## Non-goals

- Authentication and users
- Video upload, chunking, or playback
- FFmpeg / HLS processing
- WebSocket danmaku
- RocketMQ
- Elasticsearch
- Recommendation logic
- Microservice extraction
- Kubernetes
- Jenkins
- Performance claims or load testing

## Repository changes

The repository is a monorepo:

```text
backend/      Spring Boot modules (`common`, `api`)
frontend/     Vue 3 application
infra/        Future Docker assets
docs/         Architecture and milestone notes
scripts/      Local helper scripts
```

The workspace root is the repository root. A nested `video-streaming-platform/` directory was not created because this project already lives in `kiki-video-platform`.

## Backend setup

- Spring Boot 4.1.1
- Java 21 bytecode
- Maven multi-module project with Wrapper
- Modules: `common`, `api`
- Dependencies: Spring Web MVC, Validation, Actuator, PostgreSQL driver, test starters
- MyBatis / MyBatis-Plus was not added because there is no persistence yet
- `GET /api/health` returns `{"status":"ok"}`
- Start the API from `backend/` with `./mvnw -pl api -am spring-boot:run` so `common` is built first

## Frontend setup

- Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios
- Home page shows that the development environment is running
- Home page calls `GET /api/health` through the Vite proxy and displays backend connectivity

## Infrastructure setup

Docker Compose services:

- PostgreSQL on `5432`
- MinIO on `9000` / `9001`
- Redis on `6379` (unused by application code)

Credentials and ports come from `.env`, copied from `.env.example`.

## Verification

- `backend/mvnw test`
- `frontend` `npm install` and `npm run build`
- `docker compose config`
- `docker compose up -d` when Docker is available
- Frontend can reach `/api/health` when both apps are running

## Definition of Done

- [x] Repository builds cleanly
- [x] Spring Boot starts
- [x] Frontend starts
- [x] PostgreSQL starts
- [x] MinIO starts
- [x] Frontend can reach the backend health endpoint
- [x] Setup instructions are documented
- [x] No secrets are committed
- [x] Git working tree is clean after the final commit
