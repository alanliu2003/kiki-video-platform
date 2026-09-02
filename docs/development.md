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
- Elasticsearch: `9200` (local-only, security disabled)

The API requires PostgreSQL and MinIO. Redis is used for interaction counters; the API keeps serving from PostgreSQL if Redis is down. Search requires Elasticsearch; video creation still succeeds if search is down. The worker also needs RocketMQ plus FFmpeg. Start Compose before the backend. Local Elasticsearch has xpack security disabled — LOCAL DEVELOPMENT ONLY.

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
- `GET http://localhost:8080/api/search/videos?q=trailer`
- `GET http://localhost:8080/api/videos/trending`
- `GET http://localhost:8080/api/videos/recent`
- `POST http://localhost:8080/api/videos/{id}/views/qualify`
- `POST http://localhost:8080/api/auth/register`
- `POST http://localhost:8080/api/auth/login`
- `GET http://localhost:8080/api/users/{id}`
- `GET http://localhost:8080/api/users/{id}/videos`
- `GET http://localhost:8080/api/users/me`
- `GET http://localhost:8080/v3/api-docs`
- `GET http://localhost:8080/swagger-ui.html`
- `POST http://localhost:8080/api/videos` (legacy multipart)
- `POST http://localhost:8080/api/uploads/init`
- `GET http://localhost:8080/api/uploads/{uploadId}`
- `PUT http://localhost:8080/api/uploads/{uploadId}/chunks/{chunkIndex}`
- `POST http://localhost:8080/api/uploads/{uploadId}/complete`
- `GET http://localhost:8080/api/videos/{id}`
- `GET http://localhost:8080/api/videos/{id}/interactions`
- `PUT` / `DELETE http://localhost:8080/api/videos/{id}/like`
- `PUT` / `DELETE http://localhost:8080/api/videos/{id}/favorite`
- `GET` / `POST http://localhost:8080/api/videos/{id}/comments`
- `GET http://localhost:8080/api/videos/{id}/danmaku?fromMs=0&toMs=60000`
- `GET ws://localhost:8080/ws/videos/{id}/danmaku`
- `GET http://localhost:8080/api/users/{id}/relationship`
- `PUT` / `DELETE http://localhost:8080/api/users/{id}/follow`
- `GET http://localhost:8080/api/videos/{id}/playback` (descriptor: `mode`, `url`, `expiresAt`, `deliveryMode`)
- `GET http://localhost:8080/api/videos/{id}/hls/master.m3u8` (rewritten playlist in presigned mode)
- `GET http://localhost:8080/api/videos/{id}/thumbnail`
- `GET http://localhost:8080/api/videos/{id}/content` (Range fallback)
- `GET http://localhost:8080/api/users/me/videos`
- `GET http://localhost:8080/api/notifications`
- `GET http://localhost:8080/api/notifications/unread-count`
- `POST http://localhost:8080/api/notifications/{id}/read`
- `POST http://localhost:8080/api/notifications/read-all`
- `GET http://localhost:8080/actuator/health`
- `GET http://localhost:8080/actuator/health/readiness`
- `GET http://localhost:8080/actuator/metrics`
- `GET http://localhost:8080/actuator/prometheus`
- `GET http://localhost:8081/actuator/health`
- `GET http://localhost:8081/actuator/prometheus`

Local Spring settings live in:

- `backend/api/src/main/resources/application.yml`
- `backend/api/src/main/resources/application-local.yml`
- `backend/api/src/main/resources/application-prod.yml`

The `local` profile is active by default. Flyway runs `V1`–`V10` on API startup. The worker does not run Flyway.

`VIDEO_MAX_UPLOAD_SIZE` is the legacy multipart limit (Spring `DataSize`, for example `1GB`). Chunked uploads use `VIDEO_MAX_FILE_SIZE` (logical file cap, default `10GB`), `VIDEO_UPLOAD_CHUNK_SIZE` (default `8MB`), `VIDEO_UPLOAD_SESSION_TTL` (default `24h`), and `VIDEO_UPLOAD_CLEANUP_INTERVAL` (default `15m`).

Media processing uses `ROCKETMQ_NAMESRV_ADDR`, `ROCKETMQ_MEDIA_TOPIC`, `VIDEO_PROCESSING_TIMEOUT` (default `30m`), `VIDEO_HLS_SEGMENT_DURATION` (default `6`), and `VIDEO_PROCESSING_MAX_ATTEMPTS` (default `3`).

Interaction counters use `REDIS_HOST`, `REDIS_PORT`, and `REDIS_INTERACTION_TTL` (default `10m`). Comment create is limited to `REDIS_COMMENT_RATE_LIMIT` per `REDIS_COMMENT_RATE_WINDOW` when Redis is available. Danmaku uses `DANMAKU_HISTORY_WINDOW` (default `60s`), `DANMAKU_MAX_LENGTH` (default `200`), `DANMAKU_RATE_LIMIT` / `DANMAKU_RATE_WINDOW` (default `10` / `10s`), and `DANMAKU_REDIS_CHANNEL` (default `kiki:danmaku`). When Redis is down, danmaku writes still persist and the API falls back to local room broadcast.

Notifications are PostgreSQL-only. A new like, favorite, comment, reply, or follow inserts an inbox row in the same transaction. The Vue header polls `GET /api/notifications/unread-count` every 30 seconds while signed in. Redis is not used for unread state. There is no notification WebSocket.

Observability uses Spring Boot Actuator. Local development exposes `health`, `info`, `metrics`, and `prometheus`. It does not expose `/env`, `/configprops`, or heap dumps. Optional dependencies (Redis, Elasticsearch, RocketMQ) report `DEGRADED` instead of taking the API down. Outbox backlog gauges sample every `OUTBOX_SAMPLE_INTERVAL` (default `15s`). Copy that key from `.env.example` if you want to override it. API logs include MDC `requestId` from `X-Request-ID` (generated when missing or invalid).

Bounded local load scripts live in `load-tests/`. Prefer host k6, or:

```powershell
docker run --rm -e BASE_URL=http://host.docker.internal:8080 -v ${PWD}/load-tests:/scripts grafana/k6:0.54.0 run /scripts/scenarios/read-heavy.js
```

Those numbers are local observations. See [Milestone 12](milestones/m12-observability-performance.md) and [Milestone 13](milestones/m13-production-delivery-demo-hardening.md).

Media delivery uses `MEDIA_DELIVERY_MODE` (`presigned` or `proxy`, default `presigned`) and `MEDIA_DELIVERY_URL_TTL` (default `15m`). Copy those keys plus `MINIO_PUBLIC_ENDPOINT` and `FRONTEND_ORIGINS` from `.env.example` into `.env`. In presigned mode the API still serves rewritten HLS playlists; TS/MP4 bytes go to MinIO. If a long VOD session outlives the TTL, the player refetches playback once. Set `MEDIA_DELIVERY_MODE=proxy` to restore the M12 Spring byte path.

Production-like packaging (optional, same volumes):

```powershell
cd backend
.\mvnw.cmd -pl api,media-worker -am package -DskipTests
cd ..
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build
```

UI: `http://127.0.0.1:8088`. Caddy body limit default `32MB` covers the default `8MB` chunk size plus headers. Legacy multipart `VIDEO_MAX_UPLOAD_SIZE` (example `1GB`) is larger than the proxy limit — use chunked upload through the proxy. The API applies MinIO CORS on startup; `scripts/setup-minio-cors.ps1` is a fallback. Opt-in demo cleanup of `load12_*` users: review `scripts/demo-cleanup.sql` first.

Qualified views use `VIDEO_VIEW_QUALIFY_SECONDS` (default `10`), `VIDEO_VIEW_QUALIFY_PERCENT` (default `0.25`), and `VIDEO_VIEW_DEDUPE_TTL` (default `30m`). Trending uses `TRENDING_CACHE_TTL` (default `2m`), `TRENDING_MAX_PAGE_SIZE` (default `50`), and the `TRENDING_*_WEIGHT` / `TRENDING_AGE_DECAY` formula weights. Copy new keys from `.env.example` into your existing `.env`. Redis down: qualify remains usable (PostgreSQL idempotency still holds; viewer-window dedupe fails open) and trending reads PostgreSQL.

Search uses `ELASTICSEARCH_ENABLED`, `ELASTICSEARCH_URL` (default `http://127.0.0.1:9200`), alias `ELASTICSEARCH_VIDEO_INDEX` (`kiki-videos`), versioned index `ELASTICSEARCH_VIDEO_INDEX_VERSION` (`kiki-videos-v1`), and `SEARCH_OUTBOX_POLL_INTERVAL` (default `5s`). Existing videos are not indexed automatically on startup. Rebuild with:

```powershell
cd backend
.\mvnw.cmd -pl api -am spring-boot:run "-Dspring-boot.run.arguments=--app.search.rebuild=true"
```

`GET /api/search/videos` requires a non-blank `q`. Empty query is 400. Elasticsearch down or disabled is 503 `SEARCH_UNAVAILABLE` with no SQL LIKE fallback. Inspect the local cluster with `curl.exe http://127.0.0.1:9200` and `curl.exe http://127.0.0.1:9200/kiki-videos/_search?q=title:trailer`.

Backend tests start PostgreSQL and MinIO with Testcontainers. Docker must be running for `.\mvnw.cmd test`. Worker FFmpeg integration tests run only when `ffmpeg` and `ffprobe` are installed.

`JWT_ACCESS_TOKEN_TTL` is a Spring Duration, for example `1h` or `3600s`.


## Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite app is at `http://127.0.0.1:5173`. The dev server binds to IPv4 localhost so Windows clients do not miss an IPv6-only listener. `/api`, `/ws`, `/v3`, and `/swagger-ui` are proxied to `http://localhost:8080`. Public profiles are `/users/:id`.

The HTTP API is unversioned / pre-v1. OpenAPI is documentation, not a compatibility guarantee. Errors return `{ code, message, timestamp, requestId }` and `X-Request-ID`. List pages are zero-based; `size` is clamped (default 20, typical max 50).

Optional demo seed (no volume reset, `demo_*` users only):

```powershell
.\scripts\demo-seed.ps1
.\scripts\api-smoke.ps1
```

Cleanup of those users is `.\scripts\demo-seed-cleanup.ps1` (`DELETE-DEMO`). M13 `load12_*` cleanup remains separate.

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
| `backend/api/src/main/resources/application-prod.yml` | Production-style profile (env vars only, no secrets) |

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
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, video_id, event_type, status, attempt_count, published_at FROM search_index_outbox;"
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, user_id, file_name, status, total_chunks, deduplicated, final_video_id, expires_at FROM upload_sessions;"
```

Inspect MinIO objects with the MinIO Client after installing `mc`, or use the console at `http://127.0.0.1:9001`. New final objects are `raw/{sha256}`. Processed HLS is `processed/{mediaObjectId}/`. Temporary parts are `uploads/{uploadId}/chunks/{index}`. Legacy Milestone 3 keys remain `videos/{userId}/{uuid}.mp4`.

If the player loads but does not play after READY, confirm `ffmpeg`/`ffprobe` are installed and the worker Actuator is healthy. If complete succeeds while the video stays PENDING, the worker or RocketMQ is down; the API is intentionally decoupled.

Inspect Elasticsearch:

```powershell
curl.exe http://127.0.0.1:9200
curl.exe http://127.0.0.1:9200/_cat/indices?v
curl.exe http://127.0.0.1:9200/kiki-videos/_mapping
curl.exe "http://127.0.0.1:9200/kiki-videos/_count"
```

If search returns 503, Elasticsearch is down or `ELASTICSEARCH_ENABLED=false`. Uploads should still succeed. After Elasticsearch returns, pending `search_index_outbox` rows retry. Existing videos need `--app.search.rebuild=true` once.

Inspect the stored user (password hashes are omitted here on purpose):

```powershell
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, username, email, display_name, role, status, created_at, (password_hash LIKE '\$2%') AS bcrypt FROM users;"
docker compose exec postgres psql -U video -d video_platform -c "SELECT user_id, video_id, created_at FROM video_likes;"
docker compose exec postgres psql -U video -d video_platform -c "SELECT follower_user_id, followed_user_id, created_at FROM user_follows;"
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, video_id, author_user_id, parent_comment_id, status, created_at FROM comments;"
docker compose exec postgres psql -U video -d video_platform -c "SELECT id, video_id, user_id, content, video_time_ms, style, status, client_message_id, created_at FROM danmaku ORDER BY id;"
```

Inspect Redis interaction keys:

```powershell
docker compose exec redis redis-cli KEYS "kiki:*"
docker compose exec redis redis-cli GET "kiki:video:1:like-count"
docker compose exec redis redis-cli TTL "kiki:video:1:like-count"
docker compose exec redis redis-cli GET "kiki:ratelimit:danmaku:1"
```

Danmaku Pub/Sub uses channel `kiki:danmaku`. Those messages are not stored as Redis keys.

## Troubleshooting

- If `8080` or `5173` is already in use, change `SERVER_PORT` / the Vite `server.port` locally.
- If Compose ports conflict, edit `.env`.
- If the backend fails to start, confirm PostgreSQL and MinIO are healthy: `docker compose ps`.
- If startup reports a video storage error, confirm `MINIO_ENDPOINT` points at the Compose API port (`http://127.0.0.1:9000` by default) and that you copied the new variables from `.env.example` into `.env`.
- If the frontend shows "Backend is not reachable", confirm the Spring Boot process is running and that `/api/health` returns `{"status":"ok"}`.
- If login works but refresh logs you out, the access token is missing, invalid, or expired (default TTL is one hour).
- If the video player loads but does not play, wait for READY HLS or confirm the original codec is browser-decodable on the raw fallback path.
- If seeking fails, confirm the API returns `206` and `Content-Range` for `Range: bytes=0-1023`.
- If likes/comments work but Redis keys are missing, confirm `REDIS_HOST`/`REDIS_PORT` match Compose and that you copied the new variables from `.env.example`. The API still reads counts from PostgreSQL when Redis is down.

## CI, backups, and release (M15)

GitHub Actions workflows live in `.github/workflows/`. `ci.yml` runs on pull requests and `main`. It does not start or destroy this machine's Compose volumes.

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
npm test
npm run build
cd ..
docker compose -f docker-compose.yml config
# JWT_SECRET must be set for the prod overlay interpolation (use your local .env value)
docker compose -f docker-compose.yml -f docker-compose.prod.yml config
```

Backup the live database without resetting volumes:

```powershell
.\scripts\backup-postgres.ps1
.\scripts\verify-postgres-backup.ps1
.\scripts\backup-minio.ps1
.\scripts\verify-search-rebuild.ps1
.\scripts\check-secrets.ps1
```

Restore always targets `video_platform_restore_test` or `videos-restore-test` unless you explicitly override — and the scripts refuse the live database/bucket. Type `RESTORE` at the prompt.

`GET /actuator/info` returns `{ "app": { "version", "commit" } }`. The Vue app logs the same pair once to the browser console. Neither dumps environment secrets.

See [Milestone 15](milestones/m15-ci-cd-release-operations.md), [Backup / restore](operations/backup-restore.md), [Release](operations/release.md), and [Incidents](operations/incidents.md).

**Never modify a historical Flyway migration after merge.** New fixes use a new `Vn` file.

If you launch a throwaway production-like stack, use `docker compose -p kiki-m15-smoke ...` and `down -v` **only** on that project name. Never `down -v` the normal `kiki-video-platform` project.
