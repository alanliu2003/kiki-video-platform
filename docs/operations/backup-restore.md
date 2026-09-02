# Backup and restore

This is a local / demo operations guide. It is not a managed backup product and it does not claim point-in-time distributed consistency.

## What is authoritative

| Store | Role | Backup? |
| --- | --- | --- |
| PostgreSQL | Authoritative application state | Yes — `pg_dump` |
| MinIO | Media object bytes | Yes — `mc mirror` |
| Elasticsearch | Rebuildable search projection | No — rebuild from PostgreSQL |
| Redis | Cache, rate limits, Pub/Sub, view-dedupe | No — restart and let it refill |
| RocketMQ | Media-processing transport | No — outbox in PostgreSQL is the durable intent |

Never treat Elasticsearch or Redis as restore sources.

## Recommended order

1. Stop or quiet writers if you need a cleaner cut (optional for local drills).
2. **PostgreSQL first.**
3. MinIO immediately after.
4. Record both timestamps. They will not be the same instant.

PostgreSQL and MinIO backups are independent. After restore you may see:

- DB rows whose objects are missing in MinIO
- MinIO objects with no matching `media_objects` / `videos` row

That is expected. Reconcile by inspecting `media_objects.object_key` / `master_playlist_key` against the bucket. Do not claim a transactional snapshot across both stores.

## PostgreSQL

Custom format (`-Fc`) timestamped files go to `backups/postgres/`. That directory is gitignored.

```powershell
.\scripts\backup-postgres.ps1
.\scripts\restore-postgres.ps1 -BackupFile backups\postgres\video_platform-YYYYMMDD-HHMMSS.dump
```

```bash
./scripts/backup-postgres.sh
./scripts/restore-postgres.sh backups/postgres/video_platform-YYYYMMDD-HHMMSS.dump
```

Restore defaults to `video_platform_restore_test`. The scripts refuse to restore into the current `POSTGRES_DB`. Type `RESTORE` (or pass `-ConfirmRestore` / `CONFIRM_RESTORE=RESTORE`) after reading the prompt.

Credentials come from Compose / environment (`POSTGRES_USER`, `POSTGRES_DB`). Scripts do not embed passwords.

Safe local drill (does not drop the live database or volumes):

```powershell
.\scripts\verify-postgres-backup.ps1
```

That dump → restore-test DB → compare `users`, `videos`, `media_objects`, `comments`, `notifications` → drop only `video_platform_restore_test`.

### Retention (local / demo)

Keep the last few dumps by hand. Delete old files under `backups/` yourself. Do not commit dumps. There is no cloud lifecycle policy.

## MinIO

Mirrors `raw/`, `processed/`, and legacy `videos/` / `thumbnails/` prefixes when present.

```powershell
.\scripts\backup-minio.ps1
.\scripts\restore-minio.ps1 -Source backups\minio\YYYYMMDD-HHMMSS -TargetBucket videos-restore-test
```

Restore refuses the live `videos` bucket and never passes `--remove`. Copy into `videos-restore-test`, check object counts, then delete that test bucket.

## Elasticsearch recovery

Do not back up Elasticsearch as truth.

1. Restore / confirm PostgreSQL.
2. Wait until the API can reach Elasticsearch.
3. Rebuild:

```powershell
cd backend
.\mvnw.cmd -pl api -am spring-boot:run "-Dspring-boot.run.arguments=--app.search.rebuild=true"
```

4. Compare counts (does not delete `kiki-videos` or `kiki-videos-v1`):

```powershell
.\scripts\verify-search-rebuild.ps1
```

A disposable index may be deleted only when its name looks like `kiki-videos-m15*` or `kiki-videos-*-it`. Production alias/version names are rejected.

## Redis recovery

Redis is non-authoritative.

1. Restart the Redis container.
2. Caches and counters refill from PostgreSQL on the next read/write path.
3. Short-lived view-dedupe keys, rate-limit windows, and danmaku Pub/Sub history in Redis are lost. Durable views, comments, danmaku rows, and notifications remain in PostgreSQL.

Do not add Redis dump/restore unless a future requirement appears.

## RocketMQ recovery

Durable media-processing intent lives in `media_processing_outbox` (PostgreSQL). Search intent lives in `search_index_outbox`.

1. Restart NameServer and broker.
2. Confirm API/worker readiness and `kiki.outbox.pending`.
3. The publisher retries pending rows. Do not snapshot the broker for this project.

## Flyway

Never modify a historical Flyway migration after it has been merged. Fixes go in a new `V11+` file.

Existing Testcontainers suites apply `V1`–`V10` to a fresh PostgreSQL before the API starts. That is the automated migration-safety check.

Pre-release upgrade check (manual, non-destructive):

1. `.\scripts\backup-postgres.ps1`
2. Confirm `flyway_schema_history` on the live DB.
3. Start the new API against a **copy** (`video_platform_restore_test` or an isolated Compose project), not by rewriting old SQL.
4. Application rollback after a backward-incompatible migration is not safe. Prefer additive migrations while the project is pre-1.0.

## Isolated smoke project

If you launch a production-like stack for a drill, use a separate Compose project and disposable volumes:

```powershell
$env:JWT_SECRET = "<local value from your .env, not committed>"
$env:POSTGRES_PORT = "15432"
$env:MINIO_API_PORT = "19000"
$env:MINIO_CONSOLE_PORT = "19001"
$env:REDIS_PORT = "16379"
$env:ROCKETMQ_NAMESRV_PORT = "19876"
$env:ROCKETMQ_BROKER_PORT = "20911"
$env:ELASTICSEARCH_PORT = "19200"
$env:PROXY_PORT = "18088"
docker compose -p kiki-m15-smoke -f docker-compose.yml -f docker-compose.prod.yml up --build
```

`docker compose -p kiki-m15-smoke down -v` is allowed **only** for that isolated project. Never run `down -v` against the normal `kiki-video-platform` project.
