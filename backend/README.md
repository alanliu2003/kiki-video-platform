# Backend

Modular Spring Boot application for the video streaming platform.

## Modules

- `common` — shared constants, media events, HLS path helpers, rendition ladder
- `api` — executable HTTP API (auth, users, videos, uploads, outbox publisher)
- `media-worker` — executable FFmpeg HLS worker (Actuator on port 8081)

## Persistence

- PostgreSQL is the user, video-metadata, upload-session, media-object, and outbox datastore
- MinIO stores raw and processed video objects
- Flyway owns schema changes (`api/src/main/resources/db/migration`)
- MyBatis mapper interfaces execute explicit SQL

The API will not start without reachable PostgreSQL and MinIO instances. The worker needs PostgreSQL, MinIO, RocketMQ, `ffmpeg`, and `ffprobe`.

## Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me` (Bearer JWT)
- `POST /api/videos` (Bearer JWT, legacy multipart; now creates/links a media object)
- `POST /api/uploads/init` (Bearer JWT)
- `GET /api/uploads/{uploadId}` (Bearer JWT, owner only)
- `PUT /api/uploads/{uploadId}/chunks/{chunkIndex}` (Bearer JWT, owner only)
- `POST /api/uploads/{uploadId}/complete` (Bearer JWT, owner only; does not wait for FFmpeg)
- `GET /api/videos/{id}` (public, includes `processingStatus`)
- `GET /api/videos/{id}/playback` (public)
- `GET /api/videos/{id}/hls/**` (public, processed assets)
- `GET /api/videos/{id}/thumbnail` (public)
- `GET /api/videos/{id}/content` (public, HTTP Range, raw/legacy)
- `GET /api/users/me/videos` (Bearer JWT)

Passwords are hashed with BCrypt. Access tokens are HS256 JWTs configured by `JWT_SECRET` and `JWT_ACCESS_TOKEN_TTL`. Refresh tokens are not implemented.

`.\mvnw.cmd test` needs Docker because tests use Testcontainers PostgreSQL and MinIO. Worker FFmpeg integration tests run only when `ffmpeg` and `ffprobe` are on PATH.

## Run

From `backend/`:

```bash
./mvnw test
./mvnw -pl api -am spring-boot:run
./mvnw -pl media-worker -am spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl api -am spring-boot:run
.\mvnw.cmd -pl media-worker -am spring-boot:run
```

See the root [README](../README.md) and [docs/development.md](../docs/development.md) for full setup instructions.
