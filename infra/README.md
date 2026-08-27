# Infrastructure

Local development infrastructure is defined in the root `docker-compose.yml`.

This directory will hold additional Docker and deployment files in later milestones (Nginx, observability, CI images).

## Current services

- PostgreSQL (`5432`)
- MinIO API (`9000`) and console (`9001`)
- Redis (`6379`) — scaffolded only; unused by the application in Milestone 1

## Start

From the repository root:

```bash
cp .env.example .env
docker compose up -d
```

Or use `scripts/start-infra.ps1` / `scripts/start-infra.sh`.

## Current layout

- `docker/` — reserved for service-specific Docker assets

