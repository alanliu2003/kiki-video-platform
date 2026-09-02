# Release

kiki-video-platform is pre-1.0. **Milestones** (M1–M15) are development slices. **Releases** are Git tags `v0.x.y`.

Example: `v0.15.0` for the Milestone 15 cut.

There is no automated semantic-release bot.

## Version mapping

| Identity | Where |
| --- | --- |
| Git tag | `v0.x.y` |
| Maven / jar | `0.0.1-SNAPSHOT` until a dedicated version bump (Dockerfiles copy that jar name) |
| Frontend `package.json` | `0.1.0` |
| Runtime `/actuator/info` | `{ "app": { "version", "commit" } }` from build-info, git properties, or `APP_VERSION` / `GIT_COMMIT` |
| Container tags | `ghcr.io/<owner>/kiki-api:<git-sha>` and `ghcr.io/<owner>/kiki-api:v0.x.y` |

Do not rely on `:latest` alone. `latest` is published only from `main` when the container workflow publishes.

## How to cut a release

1. Clean `git status` on the intended commit.
2. Run the [release checklist](#release-checklist) locally.
3. Update `CHANGELOG.md` with the tag date and notes.
4. Create an annotated tag: `git tag -a v0.15.0 -m "v0.15.0"`.
5. Push the tag when you intend GitHub Actions to build (and optionally publish) images.
6. Record the tag ↔ commit SHA ↔ image tags in the GitHub Release notes.

Changelog process: append a `## [v0.x.y] - YYYY-MM-DD` section with Added / Changed / Fixed / Infrastructure. Do not paste entire milestone documents.

## GitHub Actions

Workflows live under `.github/workflows/`.

| Workflow | Trigger | Purpose |
| --- | --- | --- |
| `ci.yml` | `pull_request`, `push` to `main` | Backend Testcontainers tests, frontend test/build, Compose config |
| `container-build.yml` | `main`, tags `v*.*.*`, `workflow_dispatch` | Package jars, build images, optional GHCR publish |

CI does **not** start the operator’s local stack and does not run `docker compose down -v`.

API smoke (`scripts/api-smoke.ps1`) is a **local release** check against a running API. It is not a required CI job (full RocketMQ/ES/MinIO stack is too heavy and fragile for every PR).

### Required checks to mark in GitHub

Repository settings cannot be set from this repo. In GitHub → Settings → Branches → `main` protection, mark these CI jobs required:

- `Backend tests`
- `Frontend test and build`
- `Compose config`

Leave `Container build` optional unless you want image builds on every main push to be required.

## Local build inventory

### Backend

- Java 21, Maven Wrapper `backend/mvnw`
- Modules: `common`, `api`, `media-worker`
- Tests: `.\mvnw.cmd test` (Testcontainers: PostgreSQL, Redis, MinIO, Elasticsearch; RocketMQ disabled in API ITs)
- Package: `.\mvnw.cmd -pl api,media-worker -am package -DskipTests`

### Frontend

- Node 22 in CI and the frontend image; Node 20+ is documented for local Vite
- `npm ci` / `npm install`, `npm test` (Vitest), `npm run build`

### Containers

Package jars first, then:

```powershell
docker build -f backend/api/Dockerfile --build-arg GIT_COMMIT=$(git rev-parse HEAD) -t kiki-api:local backend
docker build -f backend/media-worker/Dockerfile --build-arg GIT_COMMIT=$(git rev-parse HEAD) -t kiki-media-worker:local backend
docker build -f frontend/Dockerfile --build-arg VITE_GIT_COMMIT=$(git rev-parse HEAD) -t kiki-frontend:local frontend
```

### Compose

```powershell
docker compose -f docker-compose.yml config
$env:JWT_SECRET = "<from your local .env>"
docker compose -f docker-compose.yml -f docker-compose.prod.yml config
```

## Release checklist

### Before release

- [ ] `git status` clean
- [ ] `cd backend; .\mvnw.cmd test`
- [ ] `cd frontend; npm test; npm run build`
- [ ] Docker images `kiki-api`, `kiki-media-worker`, `kiki-frontend` build
- [ ] Both Compose files `config` successfully
- [ ] Flyway: no edits to merged `V1`–`V10`; new changes are a new version
- [ ] `.\scripts\backup-postgres.ps1`
- [ ] `.\scripts\api-smoke.ps1` against a running API
- [ ] `/actuator/health/readiness` UP
- [ ] Known limitations in the milestone doc reviewed

### After release / deploy of the production-like stack

- [ ] API and worker health / readiness
- [ ] OpenAPI `/v3/api-docs` and a public read (`/api/videos/recent`)
- [ ] Search or documented 503 if ES is down
- [ ] Upload → processing → playback path if you changed media code
- [ ] `/actuator/prometheus` scrape; `kiki.outbox.pending` sane
- [ ] Image tag matches the Git tag / SHA

## Rollback

- Rolling **code** back to a previous image/tag is straightforward **if** Flyway has not applied a new incompatible schema.
- After a backward-incompatible migration, roll **forward** with a new migration. Do not edit old files. Do not claim automatic schema rollback.
- Prefer additive migrations (nullable columns, new tables) while pre-1.0.

## Dependency review

`npm audit` and Maven trees are informational. They are not CI gates. Review them before a tagged release; do not fail the default pipeline on upstream CVE databases.

```powershell
cd frontend; npm audit
cd backend; .\mvnw.cmd -pl api dependency:tree
```

## Secrets

Pass `JWT_SECRET`, database, and MinIO credentials through environment / `.env` (gitignored). `scripts/check-secrets.ps1` scans **tracked** files only and does not print matches’ values.
