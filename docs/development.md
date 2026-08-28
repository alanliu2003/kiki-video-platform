# Local development

## Prerequisites

- Java 21+ (JDK 25 is fine; the project targets Java 21 bytecode)
- Maven Wrapper in `backend/` (system Maven is optional)
- Node.js 20+ and npm
- Docker Desktop or an equivalent Docker Engine + Compose install
- Git
- `ffmpeg` and `ffprobe` on PATH if you run `media-worker` with Maven instead of the worker image

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
- RocketMQ NameServer: `9876`
- RocketMQ Broker: `10911`

The API requires PostgreSQL and MinIO. The worker also needs RocketMQ plus FFmpeg. Start Compose before the backend. Redis is still unused by application code.

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
.\mvnw.cmd -pl media-worker -am spring-boot:run
```

The API listens on `http://localhost:8080`. The worker listens on `http://localhost:8081` for Actuator only.

Useful endpoints:

- `GET http://localhost:8080/api/health`
- `POST http://localhost:8080/api/auth/register`
- `POST http://localhost:8080/api/auth/login`
- `GET http://localhost:8080/api/users/me`
- `POST http://localhost:8080/api/videos` (legacy multipart)
- `POST http://localhost:8080/api/uploads/init`
- `GET http://localhost:8080/api/uploads/{uploadId}`
- `PUT http://localhost:8080/api/uploads/{uploadId}/chunks/{chunkIndex}`
- `POST http://localhost:8080/api/uploads/{uploadId}/complete`
- `GET http://localhost:8080/api/videos/{id}`
- `GET http://localhost:8080/api/videos/{id}/playback`
- `GET http://localhost:8080/api/videos/{id}/hls/master.m3u8`
- `GET http://localhost:8080/api/videos/{id}/thumbnail`
- `GET http://localhost:8080/api/videos/{id}/content`
- `GET http://localhost:8080/api/users/me/videos`
- `GET http://localhost:8080/actuator/health`
- `GET http://localhost:8081/actuator/health`

Local Spring settings live in:

- `backend/api/src/main/resources/application.yml`
- `backend/api/src/main/resources/application-local.yml`

The `local` profile is active by default. Flyway runs `V1`–`V4` on API startup. The worker does not run Flyway.

`VIDEO_MAX_UPLOAD_SIZE` is the legacy multipart limit (Spring `DataSize`, for example `1GB`). Chunked uploads use `VIDEO_MAX_FILE_SIZE` (logical file cap, default `10GB`), `VIDEO_UPLOAD_CHUNK_SIZE` (default `8MB`), `VIDEO_UPLOAD_SESSION_TTL` (default `24h`), and `VIDEO_UPLOAD_CLEANUP_INTERVAL` (default `15m`).

Media processing uses `ROCKETMQ_NAMESRV_ADDR`, `ROCKETMQ_MEDIA_TOPIC`, `VIDEO_PROCESSING_TIMEOUT` (default `30m`), `VIDEO_HLS_SEGMENT_DURATION` (default `6`), and `VIDEO_PROCESSING_MAX_ATTEMPTS` (default `3`).

Backend tests start PostgreSQL and MinIO with Testcontainers. Docker must be running for `.\mvnw.cmd test`. Worker FFmpeg integration tests run only when `ffmpeg` and `ffprobe` are installed.

`JWT_ACCESS_TOKEN_TTL` is a Spring Duration, for example `1h` or `3600s`.


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
2. Add any new variables from `.env.example` to your local `.env` (do not commit `.env`).
3. Start the backend.
4. Start the frontend.
5. Open `http://127.0.0.1:5173`, register a user, log in, upload an MP4 at `/videos/upload` (hash + chunks). The complete call should return immediately. The video page shows PENDING/PROCESSING, then plays HLS when READY. Re-selecting the same file after an interruption resumes missing chunks. Uploading the same bytes again skips physical storage and reuses processed HLS when it is already READY.

## Auth API examples

PowerShell:

```powershell
$register = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/auth/register -ContentType application/json -Body '{"username":"alice","email":"alice@example.com","password":"StrongPassword123"}'
$login = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/auth/login -ContentType application/json -Body '{"identifier":"alice","password":"StrongPassword123"}'
Invoke-RestMethod -Uri http://127.0.0.1:8080/api/users/me -Headers @{ Authorization = "Bearer $($login.accessToken)" }
```

## Video API examples

PowerShell:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/auth/login -ContentType application/json -Body '{"identifier":"alice","password":"StrongPassword123"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
# Legacy multipart still works:
curl.exe -H "Authorization: Bearer $($login.accessToken)" -F "title=Demo video" -F "description=First upload" -F "file=@demo.mp4;type=video/mp4" http://127.0.0.1:8080/api/videos
# Preferred chunked flow is used by the Vue upload page: init → PUT chunks → complete.
Invoke-RestMethod -Uri http://127.0.0.1:8080/api/users/me/videos -Headers $headers
```

Inspect video and upload metadata:

```powershell
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, owner_user_id, title, object_key, media_object_id, file_sha256, file_size_bytes, status FROM videos;"
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, sha256, object_key, processing_status, processing_attempts, master_playlist_key, thumbnail_key, duration_seconds, source_width, source_height FROM media_objects;"
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, media_object_id, event_type, status, attempt_count, published_at FROM media_processing_outbox;"
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, user_id, file_name, status, total_chunks, deduplicated, final_video_id, expires_at FROM upload_sessions;"
```

Inspect MinIO objects with the MinIO Client after installing `mc`, or use the console at `http://127.0.0.1:9001`. New final objects are `raw/{sha256}`. Processed HLS is `processed/{mediaObjectId}/`. Temporary parts are `uploads/{uploadId}/chunks/{index}`. Legacy Milestone 3 keys remain `videos/{userId}/{uuid}.mp4`.

If the player loads but does not play after READY, confirm `ffmpeg`/`ffprobe` are installed and the worker Actuator is healthy. If complete succeeds while the video stays PENDING, the worker or RocketMQ is down; the API is intentionally decoupled.

Inspect the stored user (password hashes are omitted here on purpose):

```powershell
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, username, email, display_name, role, status, created_at, (password_hash LIKE '\$2%') AS bcrypt FROM users;"
```

## Troubleshooting

- If `8080` or `5173` is already in use, change `SERVER_PORT` / the Vite `server.port` locally.
- If Compose ports conflict, edit `.env`.
- If the backend fails to start, confirm PostgreSQL and MinIO are healthy: `docker compose ps`.
- If startup reports a video storage error, confirm `MINIO_ENDPOINT` points at the Compose API port (`http://127.0.0.1:9000` by default) and that you copied the new variables from `.env.example` into `.env`.
- If the frontend shows "Backend is not reachable", confirm the Spring Boot process is running and that `/api/health` returns `{"status":"ok"}`.
- If login works but refresh logs you out, the access token is missing, invalid, or expired (default TTL is one hour).
- If the video player loads but does not play, wait for READY HLS or confirm the original codec is browser-decodable on the raw fallback path.
- If seeking fails, confirm the API returns `206` and `Content-Range` for `Range: bytes=0-1023`.
