# Video Streaming Platform

A full-stack video streaming platform inspired by Bilibili, built as a portfolio project. The long-term goal is a high-performance system with Vue 3 on the frontend, a Java 21 Spring Boot backend, and supporting infrastructure for storage, messaging, search, media processing, and observability.

This repository is currently at **Milestone 2: Authentication & User Foundation**. Users can register, log in with a JWT access token, and load a protected current-user profile. Video features are not implemented yet.

## Current status

Milestone 2 is complete on `milestone-2-auth-and-user-foundation`. The repository includes:

- a modular Spring Boot API with Flyway, MyBatis, Spring Security, and JWT access tokens
- registration, login, and `GET /api/users/me`
- a Vue 3 + Vite frontend with register/login/profile and persisted auth state
- Docker Compose for PostgreSQL, MinIO, and Redis
- architecture and development documentation

## Current architecture

```text
Vue 3
 │
 │ Axios + JWT
 ▼
Spring Boot API
 │
 ├── Spring Security
 ├── Auth domain
 └── User domain
        │
        ▼
   MyBatis
        │
        ▼
   PostgreSQL
```

MinIO and Redis are started locally but unused by application code.

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

PostgreSQL is required for the API to start.

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

- No video APIs, uploads, or playback
- Access tokens last one hour; refresh tokens are not implemented
- Frontend stores JWTs in `localStorage` (simple, XSS-sensitive)
- MinIO and Redis are started locally but are not used by application code
- No media processing, WebSocket danmaku, search, messaging, gateway, or CI/CD
- No performance claims or production deployment yet

## Documentation

- [Architecture](docs/architecture.md)
- [Local development](docs/development.md)
- [Milestone 1](docs/milestones/m01-foundation.md)
- [Milestone 2](docs/milestones/m02-auth-user.md)
