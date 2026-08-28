# Backend

Modular Spring Boot application for the video streaming platform.

## Modules

- `common` — shared constants and primitives
- `api` — executable HTTP API

## Run

From `backend/`:

```bash
./mvnw test
./mvnw -pl api -am spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -pl api -am spring-boot:run
```

See the root [README](../README.md) and [docs/development.md](../docs/development.md) for full setup instructions.
