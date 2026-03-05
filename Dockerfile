# Stage 1 — build
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

# Copy dependency files first for layer caching
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle gradle.properties ./

RUN ./gradlew dependencies --no-daemon

COPY src/ src/

RUN ./gradlew bootJar --no-daemon

RUN APP_VERSION=$(grep '^appVersion' gradle.properties | cut -d'=' -f2 | tr -d '[:space:]') && \
    cp build/libs/wiremock_instance_manager-${APP_VERSION}.jar app.jar

# Stage 2 — runtime
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=builder /app/app.jar app.jar

RUN mkdir -p /app/wiremock-data && chown -R app:app /app

USER app

ENV SPRING_PROFILES_ACTIVE=docker
ENV WIREMOCK_INSTANCE_MANAGER_DATA_DIR=/app/wiremock-data
ENV WIREMOCK_INSTANCE_MANAGER_CONFIG_FILE=/app/wiremock-data/wiremock-instances.json

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
