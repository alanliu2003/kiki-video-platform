# Backend

Modular Spring Boot application for the video streaming platform.

## Modules

- `common` — shared constants and primitives
- `api` — executable Spring Boot API (auth, users, videos, uploads, health)

## Persistence

- PostgreSQL is the user, video-metadata, upload-session, and media-object datastore
- MinIO stores uploaded video objects
- Flyway owns schema changes (`api/src/main/resources/db/migration`)
- MyBatis mapper interfaces execute explicit SQL

The API will not start without reachable PostgreSQL and MinIO instances.

## Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me` (Bearer JWT)
- `POST /api/videos` (Bearer JWT, legacy multipart)
- `POST /api/uploads/init` (Bearer JWT)
- `GET /api/uploads/{uploadId}` (Bearer JWT, owner only)
- `PUT /api/uploads/{uploadId}/chunks/{chunkIndex}` (Bearer JWT, owner only)
- `POST /api/uploads/{uploadId}/complete` (Bearer JWT, owner only)
- `GET /api/videos/{id}` (public)
- `GET /api/videos/{id}/content` (public, HTTP Range)
- `GET /api/users/me/videos` (Bearer JWT)

Passwords are hashed with BCrypt. Access tokens are HS256 JWTs configured by `JWT_SECRET` and `JWT_ACCESS_TOKEN_TTL`. Refresh tokens are not implemented.

`.\mvnw.cmd test` needs Docker because tests use Testcontainers PostgreSQL and MinIO.

## Run

From `backend/`:

```bash
./mvnw test
./mvnw -pl api -am spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl api -am spring-boot:run
```

`.\mvnw.cmd test` needs Docker because auth tests use Testcontainers PostgreSQL.

See the root [README](../README.md) and [docs/development.md](../docs/development.md) for full setup instructions.
