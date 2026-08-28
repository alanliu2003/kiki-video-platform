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
- `GET http://localhost:8080/actuator/health`

Local Spring settings live in:

- `backend/api/src/main/resources/application.yml`
- `backend/api/src/main/resources/application-local.yml`

The `local` profile is active by default. The application starts without PostgreSQL, Redis, or MinIO because those integrations are not enabled yet.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite app is at `http://127.0.0.1:5173`. The dev server binds to IPv4 localhost so Windows clients do not miss an IPv6-only listener. `/api` is proxied to `http://localhost:8080`, so the home page can show backend connectivity without a CORS browser call.

Production build check:

```bash
cd frontend
npm run build
```

## Environment files

| File | Purpose |
| --- | --- |
| `.env.example` | Documented Docker Compose variables |
| `.env` | Your local Compose overrides (not committed) |
| `frontend/.env.example` | Documented Vite variables |
| `frontend/.env` | Your local Vite overrides (not committed) |
| `backend/api/src/main/resources/application.yml` | Shared Spring defaults |
| `backend/api/src/main/resources/application-local.yml` | Local Spring profile |

Do not put production credentials in the repository.

## Suggested daily workflow

1. Start Docker services.
2. Start the backend.
3. Start the frontend.
4. Open `http://localhost:5173` and confirm the health message.

## Troubleshooting

- If `8080` or `5173` is already in use, change `SERVER_PORT` / the Vite `server.port` locally.
- If Compose ports conflict, edit `.env`.
- If the frontend shows "Backend is not reachable", confirm the Spring Boot process is running and that `/api/health` returns `{"status":"ok"}`.
