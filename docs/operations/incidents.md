# Incident runbook

Local / demo incidents. Use Actuator and PostgreSQL first. Redis is not the source of truth.

Useful endpoints:

- API `GET /actuator/health`, `/actuator/health/readiness`, `/actuator/health/dependencies`
- API `GET /actuator/prometheus` — `kiki.outbox.pending`, `kiki.outbox.oldest.pending.age.seconds`
- Worker `:8081` same Actuator paths
- `GET /actuator/info` — `{ app.version, app.commit }` only (no env dump)

IDs belong in logs (`X-Request-ID` / MDC), not in metric tags.

## A. Media stuck PENDING

1. Worker `GET http://127.0.0.1:8081/actuator/health/readiness` — `db`, `minio`, `ffmpeg`.
2. RocketMQ: `docker compose ps` NameServer + broker healthy?
3. Prometheus: `kiki.outbox.pending{outbox="media"}` and oldest-age gauge.
4. Worker logs: claimed / skipped shutting-down / FFmpeg errors.
5. SQL:

```sql
SELECT id, media_object_id, status, attempt_count, published_at, last_error
FROM media_processing_outbox
ORDER BY id DESC
LIMIT 20;

SELECT id, processing_status, processing_attempts, last_error
FROM media_objects
WHERE processing_status <> 'READY'
ORDER BY id DESC;
```

6. If the worker is down, the API is supposed to stay up; complete still returns. Start the worker; the outbox publisher retries.
7. On shutdown the worker calls `stopAcceptingJobs()` and then stops the RocketMQ consumer. In-flight FFmpeg is not a coordinated drain; it is bounded by `VIDEO_PROCESSING_TIMEOUT`.

## B. Search unavailable

1. `GET /api/search/videos?q=test` — 503 `SEARCH_UNAVAILABLE` is expected when ES is down.
2. API health `elasticsearch` component may be `DEGRADED`; readiness should still be UP if db+minio are up.
3. `kiki.outbox.pending{outbox="search"}`.
4. `curl.exe http://127.0.0.1:9200/_cluster/health`
5. After ES returns, pending outbox rows retry. Existing videos need a rebuild:

```powershell
.\scripts\verify-search-rebuild.ps1
```

Do not delete `kiki-videos` / `kiki-videos-v1` as a first step.

## C. Redis unavailable

Expected:

- `/actuator/health` stays HTTP 200; Redis component `DEGRADED`.
- Readiness stays UP.
- Likes/favorites/comments/follows persist in PostgreSQL.
- Cache miss / fail-open; counters may look stale until TTL after Redis returns.
- Viewer-window view dedupe fails open; PostgreSQL idempotency still holds.
- Live danmaku falls back to local room broadcast.

Do not restore Redis from backup.

## D. PostgreSQL unavailable

Expected:

- Readiness **DOWN** (503).
- Primary API write/read paths unavailable.
- Restore from `pg_dump` into a **new** database first (`video_platform_restore_test`), validate counts, then plan a cutover. Do not silently restore over the live DB.

## E. MinIO unavailable

Expected:

- Readiness **DOWN**.
- Uploads, processing I/O, and media delivery fail.
- Metadata in PostgreSQL may still read if only MinIO is down (health still fails readiness because minio is required).

Restore objects into a test bucket first. PostgreSQL and MinIO timestamps will not match.

## F. Notifications delayed

Check **application and PostgreSQL**, not Redis.

```sql
SELECT id, recipient_user_id, type, read_at, created_at
FROM notifications
ORDER BY id DESC
LIMIT 20;
```

Inbox insert is in the same transaction as the social write. There is no notification socket. The Vue bell polls unread-count every 30s.

## G. High outbox backlog

1. `kiki.outbox.pending` and `kiki.outbox.oldest.pending.age.seconds` for `media` and `search`.
2. Dependency health: RocketMQ for media, Elasticsearch for search, MinIO/Postgres for both.
3. API publisher logs and worker consumer logs.
4. Confirm the process is not mid-shutdown (`stopAcceptingJobs`).

Sample interval is `OUTBOX_SAMPLE_INTERVAL` (default 15s). See [Milestone 12](../milestones/m12-observability-performance.md).

## Graceful stop (M13)

- API: `server.shutdown=graceful`, 30s phase. Readiness is process health (db+minio); it does not implement a custom “draining” coordinator.
- Worker: `@PreDestroy` stops accepting new claims, then shuts down the consumer. In-flight jobs are not killed immediately.

There is no distributed drain or blue/green controller.
