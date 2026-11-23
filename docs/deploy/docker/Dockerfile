# =====================================================================
# OPTIONAL (doc 14 §8.3): container image for Smart Grievance.
# Build the JAR first on the host (./mvnw clean verify), then from the app/ folder:
#   docker build -f ../docs/deploy/docker/Dockerfile -t smart-grievance:0.1.0 .
# or let compose.yaml build it. The image holds only the JRE and the JAR.
# =====================================================================
FROM eclipse-temurin:25-jre-noble

# curl is only for the HEALTHCHECK below; the JRE image has no curl or wget
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app \
 && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /app/uploads /app/logs \
 && chown -R app:app /app \
 && chmod 750 /app/uploads /app/logs

WORKDIR /app
COPY --chown=app:app target/smart-grievance.jar /app/smart-grievance.jar

USER app
EXPOSE 8080

# prod profile; secrets come from compose (.env), never from the image (doc 11 §9.1)
ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/app/uploads \
    LOG_DIR=/app/logs \
    LOG_CONSOLE_THRESHOLD=INFO \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/smart-grievance.jar"]
