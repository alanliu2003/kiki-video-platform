# Milestone 12 — Observability & Performance Validation

## Goal

Add practical application observability and bounded local load validation for the existing M1–M11 platform. This milestone answers: is the process healthy, which dependency is down or slow, how long important work takes, whether outboxes are backing up, and what happens under modest concurrent load.

This is **not** a production Kubernetes / Prometheus / Grafana deployment milestone. Local numbers are observations only.

## Observability architecture

```text
Vue (optional X-Request-ID in DEV error logs)
  └─ REST
       └─ Spring Boot API
            ├─ RequestIdFilter → MDC requestId + X-Request-ID
            ├─ Micrometer HTTP timers (route templates)
            ├─ PlatformMetrics (low-cardinality business counters)
            ├─ OutboxBacklogSampler (15s SQL sample → gauges)
            └─ Actuator
                 /actuator/health
                 /actuator/health/{liveness,readiness,dependencies}
                 /actuator/metrics
                 /actuator/prometheus

media-worker
  ├─ WorkerMetrics
  ├─ FFmpeg / MinIO / RocketMQ / PostgreSQL health
  └─ Actuator on :8081
```

PostgreSQL remains authoritative. Redis is still fail-open cache. Elasticsearch is still a rebuildable projection. IDs belong in logs, not metric tags.

## Actuator endpoints

Exposed on API (`8080`) and worker (`8081`):

| Endpoint | Purpose | Local access |
| --- | --- | --- |
| `/actuator/health` | Process + component status | public |
| `/actuator/health/liveness` | process ping | public |
| `/actuator/health/readiness` | required deps | public |
| `/actuator/health/dependencies` | all checked deps | public |
| `/actuator/info` | build/app info | public |
| `/actuator/metrics` | Micrometer names | public |
| `/actuator/prometheus` | scrape text | public |

Not intended for local use, and not exposed:

- `/actuator/env`
- `/actuator/configprops`
- `/actuator/heapdump`

Unauthenticated calls to those paths receive `401` from Spring Security on the API. Worker Actuator is bound to localhost:8081 with no Spring Security — local-development only.

### Health semantics

API:

| Component | Missing / down | Overall `/actuator/health` |
| --- | --- | --- |
| PostgreSQL (`db`) | DOWN | DOWN (503) |
| MinIO | DOWN | DOWN (503) |
| Redis | DEGRADED | HTTP 200 |
| Elasticsearch | DEGRADED, or UP+`enabled=false` | HTTP 200 |
| RocketMQ | DEGRADED, or UP+`enabled=false` | HTTP 200 |

Readiness includes `db` + `minio` only. Redis / Elasticsearch / RocketMQ outages must not take the API down.

Worker:

| Component | Missing / down |
| --- | --- |
| PostgreSQL / MinIO / FFmpeg | DOWN |
| RocketMQ | DEGRADED when enabled but namesrv unreachable; UP+disabled when `ROCKETMQ_ENABLED=false` |

`DEGRADED` is mapped to HTTP 200.

## Metric catalog

HTTP (Micrometer, auto):

- `http.server.requests` — count, duration histogram, tags `method`, `uri` (template), `status`, `outcome`, `application`

Business (API `kiki.*`):

| Name | Type | Tags |
| --- | --- | --- |
| `kiki.upload.sessions` | counter | `result=initiated\|completed\|deduplicated` |
| `kiki.upload.complete.duration` | timer | none |
| `kiki.media.jobs` | counter | `result=started` |
| `kiki.search.requests` | counter | none |
| `kiki.search.unavailable` | counter | none |
| `kiki.search.index` | counter | `result=success\|failure` |
| `kiki.search.rebuild.duration` | timer | none |
| `kiki.search.rebuild.documents` | counter | none |
| `kiki.views.qualify` | counter | `result=accepted\|already_counted\|viewer_window\|rejected` |
| `kiki.recommendations.requests` | counter | `result=personalized\|cold_start` |
| `kiki.recommendations.cache` | counter | `result=hit\|miss` |
| `kiki.redis.fallback` | counter | `operation=view_dedupe\|cache_read\|cache_write` |
| `kiki.notifications.created` | counter | `type=VIDEO_LIKED\|…` |
| `kiki.notifications.read` | counter | `result=one\|all` |
| `kiki.outbox.publish` | counter | `outbox=media\|search`, `result=success\|failure` |
| `kiki.outbox.pending` | gauge | `outbox=media\|search` |
| `kiki.outbox.oldest.pending.age.seconds` | gauge | `outbox=media\|search` |

Worker:

| Name | Type | Tags |
| --- | --- | --- |
| `kiki.worker.jobs` | counter | `result=consumed\|success\|failed\|retry\|skipped_*` |
| `kiki.worker.processing.duration` | timer | none |
| `kiki.worker.renditions` | counter | none |

Hikari / JVM / Tomcat meters are the Spring Boot defaults (`hikaricp.connections.*`, JVM GC/heap).

### Cardinality rules

Allowed tags: `method`, `status`, `uri` template, `result`, `type` (notification enum), `outbox`, `operation`, `application`, `pool`.

Forbidden tags: `videoId`, `userId`, `outboxId`, username, raw URL, search query, JWT, filename.

## Request correlation

- Header: `X-Request-ID`
- Accept only `[A-Za-z0-9._-]{8,128}`
- Otherwise generate a UUID
- Echo on the response
- Put `requestId` in MDC for API logs
- Clear MDC in `finally`
- Frontend does not need to send the header
- Async jobs log `mediaObjectId` / `outboxId` instead of inventing distributed traces

## Outbox / backlog visibility

`OutboxBacklogSampler` runs every `OUTBOX_SAMPLE_INTERVAL` (default 15s). It counts `PENDING`/`PUBLISHING` rows and the oldest `created_at`. Existing partial indexes are used:

- `media_processing_outbox_due_idx`
- `search_index_outbox_due_idx` (tiny local table still seq-scans; no new index added)

The goal is: API can be UP while async work is silently backing up.

## Worker observability

Consumed RocketMQ messages, claim skips (`missing` / `ready` / `unclaimed`), success, failure, retry-outbox insert, processing duration, rendition count. Logs stay `key=value` (`media processing started mediaObjectId=…`, `durationMs=…`).

## Load-test tooling

Directory: `load-tests/`

Tool: k6 only.

Host k6 is optional. This machine ran scenarios with:

```powershell
docker run --rm `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e VUS=10 -e DURATION=45s `
  -e K6_SUMMARY_TREND_STATS=avg,min,med,p(90),p(95),p(99),max `
  -v E:/kiki-video-platform/load-tests:/scripts `
  grafana/k6:0.54.0 run /scripts/scenarios/read-heavy.js
```

Scenarios:

| Script | What it hits |
| --- | --- |
| `scenarios/read-heavy.js` | recent, trending, video detail, search |
| `scenarios/view-qualify.js` | unique `clientViewId` qualifies |
| `scenarios/view-idempotency.js` | one shared `clientViewId` |
| `scenarios/social.js` | one `load12_*` user, like / unread / unlike |
| `scenarios/search.js` | fixed query pool + recent |
| `scenarios/hls-range.js` | `Range: bytes=0-1023` on `/content` |

Async recovery is a manual checklist in `load-tests/README.md`.

**Side effect of the local view-qualify run:** video `21` received 1410 durable qualified views / idempotency rows. That is documented test data, not production traffic.

## Environment used

| Item | Value |
| --- | --- |
| OS | Windows 10.0.26200 |
| Docker Desktop | Engine 29.3.1 |
| Compose | postgres:16-alpine, redis:7-alpine, minio, RocketMQ 5.3.2, ES 8.17.10 |
| API JVM | Java 25.0.2 (project bytecode 21) |
| API / worker | Maven `spring-boot:run` on 8080 / 8081 |
| Media worker | running during load tests |
| Local catalog | 21 videos, 28 users (after social: +1 `load12_*` user) |
| k6 | `grafana/k6:0.54.0` via Docker |

These are local-development observations. Do not treat them as production capacity.

## Measured results

### recent / trending / detail / search reads

- 10 VUs, 45s, worker running
- requests: 8560
- throughput: 189.3 req/s
- p50: 4.92 ms
- p95: 36.79 ms
- max: 1.04 s
- errors: 0.00%
- checks: 100%

### view qualification (unique clientViewIds)

- 8 VUs, 30s
- requests: 2818 (includes recent lookups)
- iterations: 1409
- throughput: 93.7 req/s
- p50: 8.52 ms
- p95: 19.1 ms
- p99: 44.64 ms
- errors: 0.00%
- after: `videos.view_count` = `video_view_idempotency` count for video 21 (1410 = 1410)

### view idempotency (shared clientViewId)

- 8 VUs, 30s
- requests: 4080
- iterations: 2040
- p50: 7.73 ms
- p95: 10.58 ms
- p99: 38.9 ms
- errors: 0.00%
- retries did not increase `view_count` beyond one row per `(video_id, client_view_id)`

### social like / unread / unlike

- 4 VUs, 30s, one dedicated user
- requests: 1215
- p50: 11.2 ms
- p95: 69.57 ms
- p99: 99.55 ms
- errors: 0.00%

### search (ES up) + recent

- 8 VUs, 30s
- requests: 2748
- p50: 8.43 ms
- p95: 32.96 ms
- p99: 39.46 ms
- errors: 0.00%

### HLS / Range (local observation only)

- 4 VUs, 20s
- requests: 512
- p50: 6.75 ms
- p95: 11.35 ms
- p99: 16.8 ms
- errors: 0.19% (1 request: malformed MIME header while reading API-proxied `/content`)

API metadata throughput is **not** media-delivery capacity. HLS and raw Range still proxy through the API. CDN / signed object URLs are out of scope.

## Failure-mode results

Live `GET /actuator/health` with all Compose services up: API and worker `UP`; redis, elasticsearch, rocketmq, minio, ffmpeg present.

Automated tests (Testcontainers, no volume reset):

| Mode | Evidence | Result |
| --- | --- | --- |
| Redis down | `*RedisUnavailableIntegrationTest` | qualify / trending / recommendations / interactions / danmaku continue |
| Elasticsearch down | `SearchUnavailableIntegrationTest` | search 503 `SEARCH_UNAVAILABLE`; other APIs unchanged |
| RocketMQ disabled | existing media outbox ITs | upload completion durable; outbox retries |
| Worker not consuming | M5 media ITs | upload returns immediately; media stays PENDING until a worker claims |

Live `docker stop` of shared Compose services was not executed in this session (operational stop of named containers). Repeat locally with `docker stop` / `docker start` only — never `docker compose down -v`.

Expected live health when a dependency is stopped:

- Redis/ES/RocketMQ down → API health HTTP 200, component `DEGRADED`
- MinIO/Postgres down → API readiness/health DOWN
- Worker down → upload 200, media PENDING, `kiki.outbox.pending{outbox=media}` rises, recovers when worker starts

## Bottlenecks found

On this 21-video local dataset, no application bottleneck was saturating:

- Hikari `kiki-api` active connections after load: 0
- Recent / trending EXPLAIN: seq scan + in-memory sort, execution `< 1 ms`
- Notification unread: index-only scan on `notifications_recipient_unread_idx`, `0.05 ms`
- Media outbox pending count: bitmap on `media_processing_outbox_due_idx`, `0.04 ms`
- Search outbox pending count: seq scan of 24 rows, `0.02 ms`

The 1.04 s max on the read-heavy run is an outlier, not a sustained p95. Pool size was left at Spring Boot defaults.

## Optimizations

None. No index, pool, or query rewrite was justified by the measurements.

Existing indexes already match the important paths (recent `videos_created_id_idx`, notification inbox/unread, outbox due partial indexes, recommendation history indexes). Adding more indexes on 21 rows would be cargo-culting.

## Query inspection

| Path | Local plan | Index decision |
| --- | --- | --- |
| recent | seq scan 21 rows, sort `created_at DESC, id DESC` | keep `videos_created_id_idx`; planner prefers seq scan at this size |
| trending | hash/nested-loop of tiny aggregates | no new index; score is computed, not stored |
| notifications list/unread | bitmap / index-only on recipient indexes | no change |
| view qualify | PK on `(video_id, client_view_id)` | already unique; counts matched after load |
| outbox pending | partial due index (media) | no new index |
| search hydration | existing video/user joins after ES ids | unchanged |

## Security / cardinality review

- Metrics use route templates (`/api/videos/{videoId}`), not `/videos/21`
- Prometheus text from the IT suite does not contain `Bearer`, JWT secrets, or raw `/api/videos/123`
- Logs may include IDs; they must not include passwords, JWTs, or file bytes
- Local Actuator is open for scrape convenience. Production should restrict `/actuator/metrics` and `/actuator/prometheus` to an internal network or authenticated scrape. Do not expose `/env`, `/configprops`, or heap dumps.

## Known limitations

- No Prometheus/Grafana Compose services (sample `infra/prometheus/prometheus.yml` + curl only)
- No OpenTelemetry / Jaeger / Loki
- HLS/content remains API-proxied
- Local load used a 21-video catalog; results do not extrapolate
- View-qualify load wrote 1410 idempotency rows on video 21
- One Range request failed with a malformed MIME header under concurrency — treat as local proxy-media noise
- Frontend only logs request IDs in DEV; no analytics SaaS

## Tests

Backend `.\mvnw.cmd test`: 193 tests (11 common + 170 api + 12 worker), 0 failures.

Frontend `npm test`: 93 tests. `npm run build`: success.
