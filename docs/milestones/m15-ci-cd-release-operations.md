# Milestone 15 — CI/CD & Release Operations

## Goal

Make kiki-video-platform something that can be built, tested, packaged, backed up, restored, and released reproducibly. This is not a Kubernetes, Terraform, or cloud-infrastructure milestone.

## Inventory (what was already true)

### Backend

- Maven modules: `common`, `api`, `media-worker`
- Java 21, Spring Boot 4.1.1, artifact version `0.0.1-SNAPSHOT`
- Tests: `backend/mvnw test` — Testcontainers PostgreSQL, Redis, MinIO, Elasticsearch
- RocketMQ is **disabled** in API integration tests (`app.rocketmq.enabled=false`)
- Package: `mvnw -pl api,media-worker -am package -DskipTests`
- Dockerfiles copy the pre-built `0.0.1-SNAPSHOT` jars (M13 convention kept)

### Frontend

- Vue 3 + Vite + Vitest; `package.json` version `0.1.0`
- CI and the frontend image use Node 22; local docs still allow Node 20+
- `npm test`, `npm run build`

### Containers / runtime

| Image / service | Tag used |
| --- | --- |
| API / worker base | `eclipse-temurin:21-jre-jammy` |
| Frontend build | `node:22-bookworm-slim` |
| Reverse proxy | `caddy:2-alpine` |
| PostgreSQL | `postgres:16-alpine` |
| Redis | `redis:7-alpine` |
| RocketMQ | `apache/rocketmq:5.3.2` |
| Elasticsearch | `docker.elastic.co/elasticsearch/elasticsearch:8.17.10` |
| MinIO | `minio/minio:latest` (intentionally unpinned; community image, local/dev only) |

Testcontainers use the same MinIO `latest` tag and Elasticsearch `8.17.10`.

### What was manual

- No `.github/workflows`
- No backup/restore scripts
- No release tag convention
- Compose/image builds were local operator steps
- API smoke (`scripts/api-smoke.ps1`) was local-only (still is)

## CI architecture

`.github/workflows/ci.yml` on `pull_request` and `push` to `main`:

1. **Backend tests** — Java 21, Maven cache, `./mvnw -B test` (full Testcontainers suite; not replaced with mocks)
2. **Frontend test and build** — Node 22, npm cache, `npm ci`, `npm test`, `npm run build`
3. **Compose config** — `docker compose` and prod overlay `config` (dummy `JWT_SECRET` for interpolation only)

No scheduled destructive jobs. No `docker compose down -v`. Full-stack API smoke is **not** in CI (RocketMQ + ES + packaged API is too heavy/fragile for every PR). Use local `.\scripts\api-smoke.ps1`.

`.github/workflows/container-build.yml` on `main`, tags `v*.*.*`, and `workflow_dispatch`:

- Package jars, build API / worker / frontend images
- Tags: git SHA always; `latest` on `main`; git tag name on `v*.*.*`
- Publish to `ghcr.io/<owner>/kiki-*` only on main/tags or an explicit dispatch publish, using `GITHUB_TOKEN`

Until this workflow actually runs on GitHub: **workflow added but not executed remotely**.

### Branch policy (manual GitHub settings)

Mark required on `main`:

- `Backend tests`
- `Frontend test and build`
- `Compose config`

## Version / release

- Milestones ≠ releases
- Releases: annotated tags `v0.x.y` (example `v0.15.0`)
- Maven GAV stays `0.0.1-SNAPSHOT` so Docker COPY paths stay stable
- `/actuator/info` exposes `{ "app": { "version", "commit" } }` from build-info, git-commit-id, or `APP_VERSION` / `GIT_COMMIT`
- Frontend logs the same pair once to the browser console
- Env / configprops / heapdump stay disabled

See [docs/operations/release.md](../operations/release.md) and [CHANGELOG.md](../../CHANGELOG.md).

## Migration safety

- Flyway `V1`–`V10` are historical. **Never modify a merged migration.**
- Fresh-DB apply is already proven by API/worker Testcontainers suites
- Upgrade check: dump current DB, restore into `video_platform_restore_test`, start the new API against the copy if you need a dry run
- Code rollback after an incompatible schema change is **not** safe; prefer additive migrations

## Backup / restore

Scripts (credentials from environment / Compose, no embedded secrets):

| Script | Role |
| --- | --- |
| `scripts/backup-postgres.ps1` / `.sh` | `pg_dump -Fc` → `backups/postgres/` |
| `scripts/restore-postgres.ps1` / `.sh` | Restore into `video_platform_restore_test` only; confirmation required |
| `scripts/verify-postgres-backup.ps1` / `.sh` | Dump, restore-test, compare counts, drop restore DB only |
| `scripts/backup-minio.ps1` / `.sh` | `mc mirror` raw/processed (+ optional prefixes) → `backups/minio/` |
| `scripts/restore-minio.ps1` / `.sh` | Mirror into `videos-restore-test`; no `--remove` |
| `scripts/verify-search-rebuild.ps1` / `.sh` | Compare PG vs ES counts; refuses to delete `kiki-videos` |
| `scripts/check-secrets.ps1` / `.sh` | Tracked-file scan; does not print values |

`backups/` is gitignored.

PostgreSQL and MinIO timestamps can differ. Orphans/missing objects may need reconciliation. Not a distributed snapshot.

Redis: restart only. RocketMQ: restart broker; outbox is the durable intent.

## Runbooks

- [Backup / restore](../operations/backup-restore.md)
- [Release](../operations/release.md)
- [Incidents](../operations/incidents.md)

## Image provenance

Dockerfiles set `org.opencontainers.image.revision`, `version`, `source`, plus `APP_VERSION` / `GIT_COMMIT` env for the JVM processes. GHCR publish (when it runs) adds the same OCI labels from GitHub metadata.

## Security assumptions

- `.env` remains gitignored and is never written by agents
- Example JWT/MinIO/DB values in `.env.example` are local placeholders
- `/actuator/info` does not dump environment
- `npm audit` / Maven `dependency:tree` are manual/informational, not red-CI gates
- No secret-scanning SaaS

## Scope exclusions

Kubernetes, Terraform, Helm, AWS/GCP/Azure, managed DB, hosted Prometheus/Grafana, multi-region, autoscaling, service mesh, blue/green or canary controllers, DB replication, PITR, secret managers, paid CI, GitOps.

## Verification (implementing sessions)

Automated (2026-09-01):

- `docker compose -f docker-compose.yml config` and prod overlay `config` succeeded (`JWT_SECRET` set only for interpolation)
- Frontend: 112 tests passed; `npm run build` succeeded
- Backend: `.\mvnw.cmd test` exited 0, including `ActuatorObservabilityIntegrationTest` (`/actuator/info` has `app.version` / `app.commit`). Flyway applied `V1`–`V10` on a fresh Testcontainers PostgreSQL
- PowerShell scripts parsed; `scripts/check-secrets.ps1` passed on tracked files

Restore drill (2026-09-01, live Compose volumes **not** reset):

- PostgreSQL: dump → `video_platform_restore_test` → counts matched (users 30, videos 22, media_objects 10, comments 5, notifications 107) → dropped **only** the restore database
- MinIO: mirrored `raw/` + `processed/` + legacy `videos/`; sample `processed/2/thumbnail.jpg` etag `e7c7154b5e772f92094750c918132a46` matched live. First restore into `videos-restore-test` nested a timestamp prefix (scripts later copy contents with `/.`). Live `videos` bucket was not overwritten
- Elasticsearch: alias `kiki-videos` had 22 documents vs 22 PostgreSQL videos; disposable `kiki-videos-m15-verify` was created and deleted; live alias left in place

Containers (2026-09-02):

- `mvnw -pl api,media-worker -am package -DskipTests` succeeded
- Images built: `kiki-api:local`, `kiki-media-worker:local`, `kiki-frontend:local` with `org.opencontainers.image.revision=66e2b36…` (current `HEAD`; M15 is uncommitted)
- Leftover `videos-restore-test` bucket removed. Live `videos` remains (`raw/`, `processed/`, `videos/`)
- Live DB counts unchanged after Docker restart: users 30, videos 22, media_objects 10, comments 5, notifications 107; Flyway `V1`–`V10`

Not executed:

- GitHub Actions on GitHub (workflows added, branch not pushed for a remote run)
- `scripts/api-smoke.ps1` (nothing listening on `:8080`)
- Isolated `docker compose -p kiki-m15-smoke` production-like stack
- Host `bash -n` (WSL bash missing)

## Known limitations

- GitHub Actions results are unknown until the branch is pushed and the workflow runs
- Full production-like stack smoke is local/optional (`-p kiki-m15-smoke` only)
- MinIO remains `latest` for local/dev; pin only after a dedicated compatibility pass
- Maven/frontend version numbers are not the release tag
- Backups are not encrypted or off-site
- No claim of distributed crash-consistent backup
- Local image `revision` labels follow `git rev-parse HEAD`, which stays the last commit until M15 is committed

