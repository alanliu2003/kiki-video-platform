# Backend

Modular Spring Boot application for the video streaming platform.

## Modules

- `common` — shared constants and primitives
- `api` — executable Spring Boot API (auth, users, health)

## Persistence

- PostgreSQL is the user datastore
- Flyway owns schema changes (`api/src/main/resources/db/migration`)
- MyBatis mapper interfaces execute explicit SQL

The API will not start without a reachable PostgreSQL instance.

## Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me` (Bearer JWT)

Passwords are hashed with BCrypt. Access tokens are HS256 JWTs configured by `JWT_SECRET` and `JWT_ACCESS_TOKEN_TTL`. Refresh tokens are not implemented.

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
