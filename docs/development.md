# Local development

## Prerequisites

- Java 21+ (JDK 25 is fine; the project targets Java 21 bytecode)
- Maven Wrapper in `backend/` (system Maven is optional)
- Node.js 20+ and npm
- Docker Desktop or an equivalent Docker Engine + Compose install
- Git

## First-time setup

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env
```

`.env` is gitignored. Edit local values if the default ports are already in use.

## Infrastructure

```bash
docker compose up -d
docker compose ps
```

Windows:

```powershell
.\scripts\start-infra.ps1
```

macOS / Linux:

```bash
chmod +x scripts/start-infra.sh
./scripts/start-infra.sh
```

Default ports:

- PostgreSQL: `5432`
- MinIO API: `9000`
- MinIO console: `9001`
- Redis: `6379`

The API now requires PostgreSQL. Start Compose before the backend.

Stop infrastructure with `docker compose down`. Named volumes keep data until you run `docker compose down -v`.

## Backend

```bash
cd backend
./mvnw test
./mvnw -pl api -am spring-boot:run
```

Windows:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd -pl api -am spring-boot:run
```

The API listens on `http://localhost:8080`.

Useful endpoints:

- `GET http://localhost:8080/api/health`
- `POST http://localhost:8080/api/auth/register`
- `POST http://localhost:8080/api/auth/login`
- `GET http://localhost:8080/api/users/me`
- `GET http://localhost:8080/actuator/health`

Local Spring settings live in:

- `backend/api/src/main/resources/application.yml`
- `backend/api/src/main/resources/application-local.yml`

The `local` profile is active by default. Flyway runs `V1__create_users.sql` on startup.

`JWT_ACCESS_TOKEN_TTL` is a Spring Duration, for example `1h` or `3600s`.

Backend tests start PostgreSQL with Testcontainers. Docker must be running for `.\mvnw.cmd test`.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite app is at `http://127.0.0.1:5173`. The dev server binds to IPv4 localhost so Windows clients do not miss an IPv6-only listener. `/api` is proxied to `http://localhost:8080`.

Production build and frontend tests:

```bash
cd frontend
npm run build
npm test
```

## Environment files

| File | Purpose |
| --- | --- |
| `.env.example` | Documented Docker Compose and local API variables |
| `.env` | Your local Compose / JWT overrides (not committed) |
| `frontend/.env.example` | Documented Vite variables |
| `frontend/.env` | Your local Vite overrides (not committed) |
| `backend/api/src/main/resources/application.yml` | Shared Spring defaults |
| `backend/api/src/main/resources/application-local.yml` | Local Spring profile |

Do not put production credentials in the repository. The JWT secret in `.env.example` is a local-development placeholder only.

## Suggested daily workflow

1. Start Docker services.
2. Start the backend.
3. Start the frontend.
4. Open `http://127.0.0.1:5173`, register a user, log in, open Profile, refresh, then log out.

## Auth API examples

PowerShell:

```powershell
$register = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/auth/register -ContentType application/json -Body '{"username":"alice","email":"alice@example.com","password":"StrongPassword123"}'
$login = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/auth/login -ContentType application/json -Body '{"identifier":"alice","password":"StrongPassword123"}'
Invoke-RestMethod -Uri http://127.0.0.1:8080/api/users/me -Headers @{ Authorization = "Bearer $($login.accessToken)" }
```

Inspect the stored user (password hashes are omitted here on purpose):

```powershell
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, username, email, display_name, role, status, created_at, (password_hash LIKE '\$2%') AS bcrypt FROM users;"
```

## Troubleshooting

- If `8080` or `5173` is already in use, change `SERVER_PORT` / the Vite `server.port` locally.
- If Compose ports conflict, edit `.env`.
- If the backend fails to start, confirm PostgreSQL is healthy: `docker compose ps`.
- If the frontend shows "Backend is not reachable", confirm the Spring Boot process is running and that `/api/health` returns `{"status":"ok"}`.
- If login works but refresh logs you out, the access token is missing, invalid, or expired (default TTL is one hour).
