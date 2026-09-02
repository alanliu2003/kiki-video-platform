# Changelog

All notable changes to kiki-video-platform are recorded here.

The project is **pre-1.0**. Development work is organized as milestones (M1–M15). Product releases use Git tags `v0.x.y` (see [docs/operations/release.md](docs/operations/release.md)).

## Unreleased

### Added

- GitHub Actions CI (`ci.yml`) for backend Testcontainers tests, frontend test/build, and Compose config validation
- Optional container build/publish workflow (`container-build.yml`) with git-SHA and tag image names
- PostgreSQL and MinIO backup/restore scripts plus a non-destructive restore drill
- Elasticsearch rebuild verification helper (does not delete the live alias)
- Operational runbooks under `docs/operations/`
- `/actuator/info` `app.version` / `app.commit` metadata

### Infrastructure

- `backups/` gitignored; secrets stay in untracked `.env`
- OCI labels on API, worker, and frontend images

## Milestone 15 — CI/CD & Release Operations

Local automation for build, test, package, backup, restore, and release notes. Not Kubernetes or a cloud platform.

## Milestone 14 — Public API & Demo Hardening

### Added

- springdoc OpenAPI and Swagger UI
- Public creator profiles and creator video lists
- Demo seed helpers for `demo_*` users

### Changed

- Documented pagination/error conventions; creator names link to public profiles

## Milestone 13 — Production Delivery & Demo Hardening

### Added

- Presigned MinIO media delivery (playlists still rewritten by the API)
- Production-like Compose overlay: packaged API, worker, Caddy
- Graceful shutdown and worker `stopAcceptingJobs()`

## Milestone 12 — Observability & Performance

### Added

- Actuator health groups, Micrometer Prometheus, request IDs
- Outbox backlog gauges and local k6 scripts

## Milestone 11 — Notifications

### Added

- Durable PostgreSQL notification inbox for likes, favorites, comments, replies, and follows

## Milestone 10 — Personalized recommendations

### Added

- Deterministic signed-in recommendations with cold-start fallback to trending/recent

## Milestone 9 — Views & trending

### Added

- Qualified view tracking and deterministic trending / recent feeds

## Milestone 8 — Elasticsearch search

### Added

- Rebuildable video search projection, search outbox, and Vue `/search`

## Milestone 7 — Danmaku

### Added

- Video-scoped WebSocket danmaku, history window, Redis Pub/Sub fan-out

## Milestone 6 — Social interactions

### Added

- Likes, favorites, follows, comments; Redis cache-aside counters

## Milestone 5 — Media processing

### Added

- RocketMQ outbox, `media-worker`, FFmpeg HLS + thumbnails

## Milestone 4 — Chunked upload

### Added

- Resumable chunked upload and SHA-256 physical deduplication

## Milestone 3 — Video core & MinIO

### Added

- Video metadata, MinIO object storage, Range playback

## Milestone 2 — Auth

### Added

- Registration, login, JWT access tokens, `GET /api/users/me`

## Milestone 1 — Foundation

### Added

- Modular Spring Boot API, Vue 3 frontend, Compose PostgreSQL/MinIO, Flyway V1
