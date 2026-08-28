# Frontend

Vue 3 + TypeScript + Vite application for the video streaming platform.

## Scripts

```bash
npm install
npm run dev
npm run build
npm test
```

The Vite dev server proxies `/api` to `http://localhost:8080`. Authenticated routes include `/videos/upload` and `/my/videos`. Video detail at `/videos/:id` is public and plays `/api/videos/{id}/content`.

See the root [README](../README.md) and [docs/development.md](../docs/development.md) for full setup instructions.
