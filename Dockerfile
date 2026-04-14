# ─────────────────────────────────────────────────────────────────────────────
# ShipSmart Java API — Production Dockerfile
# Multi-stage build: compile with JDK, run with JRE (minimal image)
# ─────────────────────────────────────────────────────────────────────────────

# ── Build Stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy build configuration
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ ./gradle/
COPY src/ ./src/

# Build the application (skip tests for faster builds)
RUN ./gradlew build -x test --no-daemon

# ── Runtime Stage ──────────────────────────────────────────────────────────────
# Use JRE only (no compiler) for minimal image size
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the compiled JAR from builder stage
COPY --from=builder /app/build/libs/shipsmart-api-java-0.1.0-SNAPSHOT.jar ./app.jar

# Document the port (actual port is set by PORT env var at runtime)
EXPOSE 8080

# Run the application
# Spring Boot automatically binds to 0.0.0.0 and uses PORT env var
ENTRYPOINT ["java", "-jar", "app.jar"]
