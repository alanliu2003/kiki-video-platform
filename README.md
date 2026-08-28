# Video Streaming Platform

A full-stack video streaming platform inspired by Bilibili, built as a portfolio project. The long-term goal is a high-performance system with Vue 3 on the frontend, a Java 21 Spring Boot backend, and supporting infrastructure for storage, messaging, search, media processing, and observability.

This repository is currently at **Milestone 1: Foundation**. It establishes the monorepo layout, local development scaffolding, and Git hygiene. Business features such as authentication, uploads, transcoding, and danmaku are not implemented yet.

## Current status

Milestone 1 (foundation) is complete on `milestone-1-foundation`. The repository includes:

- a modular Spring Boot API that starts and exposes `GET /api/health`
- a Vue 3 + Vite frontend with a simple home page and backend health check
- Docker Compose for PostgreSQL, MinIO, and Redis
- architecture and development documentation

## Planned architecture

**Current (Milestone 1):**

```text
Vue
 ↓
Spring Boot
 ↓
PostgreSQL / MinIO
```

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

- No authentication, users, or video APIs
- PostgreSQL, MinIO, and Redis are started locally but are not used by application code yet
- No media processing, WebSocket danmaku, search, messaging, gateway, or CI/CD
- No performance claims or production deployment yet

## Documentation

- [Architecture](docs/architecture.md)
- [Local development](docs/development.md)
- [Milestone 1](docs/milestones/m01-foundation.md)
