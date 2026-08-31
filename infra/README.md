# Infrastructure

Local development infrastructure is defined in the root `docker-compose.yml`.

This directory will hold additional Docker and deployment files in later milestones (Nginx, observability, CI images).

## Current services

- PostgreSQL (`5432`)
- MinIO API (`9000`) and console (`9001`)
- Redis (`6379`) — interaction counters, rate limits, danmaku Pub/Sub
- RocketMQ NameServer (`9876`) and Broker (`10911`)
- Elasticsearch (`9200`) — video search projection, single-node, security disabled for local development only
- `rocketmq/broker.conf` — local broker settings (`brokerIP1=127.0.0.1` for host-run API/worker). The Compose broker runs as root so the named store volume is writable on Docker Desktop.

## Start

From the repository root:

```bash
cp .env.example .env
docker compose up -d
```

Or use `scripts/start-infra.ps1` / `scripts/start-infra.sh`.

## Current layout

- `docker/` — reserved for service-specific Docker assets

