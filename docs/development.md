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
- `GET http://localhost:8080/api/users/me`
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

The `local` profile is active by default. Flyway runs `V1`–`V8` on API startup. The worker does not run Flyway.

`VIDEO_MAX_UPLOAD_SIZE` is the legacy multipart limit (Spring `DataSize`, for example `1GB`). Chunked uploads use `VIDEO_MAX_FILE_SIZE` (logical file cap, default `10GB`), `VIDEO_UPLOAD_CHUNK_SIZE` (default `8MB`), `VIDEO_UPLOAD_SESSION_TTL` (default `24h`), and `VIDEO_UPLOAD_CLEANUP_INTERVAL` (default `15m`).

Media processing uses `ROCKETMQ_NAMESRV_ADDR`, `ROCKETMQ_MEDIA_TOPIC`, `VIDEO_PROCESSING_TIMEOUT` (default `30m`), `VIDEO_HLS_SEGMENT_DURATION` (default `6`), and `VIDEO_PROCESSING_MAX_ATTEMPTS` (default `3`).

Interaction counters use `REDIS_HOST`, `REDIS_PORT`, and `REDIS_INTERACTION_TTL` (default `10m`). Comment create is limited to `REDIS_COMMENT_RATE_LIMIT` per `REDIS_COMMENT_RATE_WINDOW` when Redis is available. Danmaku uses `DANMAKU_HISTORY_WINDOW` (default `60s`), `DANMAKU_MAX_LENGTH` (default `200`), `DANMAKU_RATE_LIMIT` / `DANMAKU_RATE_WINDOW` (default `10` / `10s`), and `DANMAKU_REDIS_CHANNEL` (default `kiki:danmaku`). When Redis is down, danmaku writes still persist and the API falls back to local room broadcast.

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

The Vite app is at `http://127.0.0.1:5173`. The dev server binds to IPv4 localhost so Windows clients do not miss an IPv6-only listener. `/api` and `/ws` are proxied to `http://localhost:8080`.

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
