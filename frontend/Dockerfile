FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=/api
ARG VITE_APP_VERSION=0.1.0
ARG VITE_GIT_COMMIT=unknown
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_APP_VERSION=$VITE_APP_VERSION \
    VITE_GIT_COMMIT=$VITE_GIT_COMMIT
RUN npm run build

FROM caddy:2-alpine
ARG APP_VERSION=0.1.0
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.title="kiki-frontend" \
      org.opencontainers.image.description="kiki-video-platform Caddy + Vue UI" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.source="https://github.com/alanliu2003/kiki-video-platform"
COPY --from=build /app/dist /usr/share/caddy
COPY Caddyfile /etc/caddy/Caddyfile
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=10 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
