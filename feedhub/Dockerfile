# Multi-stage build: the JDK and Gradle caches stay out of the final image.

FROM gradle:9.5.1-jdk21 AS build
WORKDIR /src

# Dependency layer: rebuilt only when the build scripts change.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN gradle dependencies --no-daemon > /dev/null 2>&1 || true

COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Running as root is unnecessary for a stateless service.
RUN addgroup -S feedhub && adduser -S feedhub -G feedhub
USER feedhub

COPY --from=build /src/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
