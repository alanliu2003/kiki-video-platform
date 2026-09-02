FROM eclipse-temurin:21-jre-jammy
ARG APP_VERSION=0.0.1-SNAPSHOT
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.title="kiki-media-worker" \
      org.opencontainers.image.description="kiki-video-platform media worker" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.source="https://github.com/alanliu2003/kiki-video-platform"
ENV APP_VERSION=${APP_VERSION} \
    GIT_COMMIT=${GIT_COMMIT}
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system kiki \
    && useradd --system --gid kiki --home /app --shell /usr/sbin/nologin kiki
WORKDIR /app
# Package first: mvn -pl media-worker -am package -DskipTests
COPY media-worker/target/video-platform-media-worker-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /tmp/kiki-media && chown -R kiki:kiki /app /tmp/kiki-media
USER kiki
EXPOSE 8081
ENV VIDEO_PROCESSING_TEMP_DIR=/tmp/kiki-media
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=40s \
    CMD curl -fsS http://127.0.0.1:8081/actuator/health/liveness >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
