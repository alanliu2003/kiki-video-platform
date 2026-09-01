FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system kiki \
    && useradd --system --gid kiki --home /app --shell /usr/sbin/nologin kiki
WORKDIR /app
# Package first: mvn -pl api -am package -DskipTests
COPY api/target/video-platform-api-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R kiki:kiki /app
USER kiki
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=40s \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
