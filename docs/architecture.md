# Architecture

This document describes the intended system shape. Only the Milestone 1 slice exists in code.

## Current architecture

Milestone 1 is a single modular application, not a set of microservices.

```text
Vue 3 (Vite)
        |
        |  GET /api/health
        v
Spring Boot API
        |
        +--> PostgreSQL   (running locally, unused by application code)
        +--> MinIO        (running locally, unused by application code)
        +--> Redis        (running locally, unused by application code)
```

### Frontend

The frontend is a Vue 3 + TypeScript application using Vite, Vue Router, Pinia, and Axios. In local development, Vite proxies `/api` to the Spring Boot process.

### Backend

The backend is a multi-module Maven project:

- `common` holds shared constants and simple response types
- `api` is the executable Spring Boot application

The API currently exposes:

- `GET /api/health` — application liveness for local frontend checks
- Spring Boot Actuator `/actuator/health` — process health

Persistence, object storage, caching, and messaging are not wired yet. The PostgreSQL driver is on the classpath so later milestones can add a datasource without changing the module shape.

### Infrastructure

`docker-compose.yml` starts named-volume services for local development:

- PostgreSQL
- MinIO
- Redis

Redis is included because it is inexpensive to run and will be needed soon. RocketMQ and Elasticsearch are intentionally absent.

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
| Web UI | Vue 3 | Scaffolded |
| API | Java 21 + Spring Boot | Scaffolded |
| Edge / reverse proxy | Nginx | Not started |
| API gateway | Spring Cloud Gateway | Not started |
| Relational data | PostgreSQL | Container only |
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
