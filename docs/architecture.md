# Architecture

This document describes the intended system shape. Milestone 2 adds users and authentication on top of the Milestone 1 foundation.

## Current architecture

The backend is still a single modular application, not a set of microservices.

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

MinIO and Redis remain infrastructure-only. They are started by Docker Compose but are unused by application code.

### Frontend

The frontend is a Vue 3 + TypeScript application using Vite, Vue Router, Pinia, and Axios. In local development, Vite proxies `/api` to the Spring Boot process.

Auth state lives in a Pinia store. The access token is stored in `localStorage` for Milestone 2 simplicity and is sent as `Authorization: Bearer <token>`. The profile route is guarded on the client; backend security is authoritative.

### Backend

The backend is a multi-module Maven project:

- `common` holds shared constants and simple response types
- `api` is the executable Spring Boot application

The API currently exposes:

- `GET /api/health` — application liveness
- `POST /api/auth/register` — create a user
- `POST /api/auth/login` — issue a JWT access token
- `GET /api/users/me` — current user, JWT required
- Spring Boot Actuator `/actuator/health` — process health

Users are stored in PostgreSQL. Schema changes are applied by Flyway. SQL access uses plain MyBatis mapper annotations.

Spring Security is stateless. The JWT filter reconstructs the principal from token claims and does not create an HTTP session.

### Infrastructure

`docker-compose.yml` starts named-volume services for local development:

- PostgreSQL — used by the API for user persistence
- MinIO — unused by application code
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
| Web UI | Vue 3 | Auth foundation |
| API | Java 21 + Spring Boot | Auth foundation |
| Edge / reverse proxy | Nginx | Not started |
| API gateway | Spring Cloud Gateway | Not started |
| Relational data | PostgreSQL | Users via Flyway + MyBatis |
| Cache / sessions | Redis | Container only |
| Object storage | MinIO, later maybe FastDFS | Container only |
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
- Treat PostgreSQL as the source of truth for user identity.
