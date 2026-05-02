# ═══════════════════════════════════════
# Stage 1 — Build
# Maven + Java 21 to compile the project
# ═══════════════════════════════════════
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first — dependencies cached as separate layer
# This means if only source code changes, Maven doesn't
# re-download all dependencies — much faster rebuilds
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# ═══════════════════════════════════════
# Stage 2 — Run
# Slim JRE image — no Maven, no source code
# Final image is much smaller
# ═══════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

# Copy only the jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Own the jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# Virtual threads + container aware JVM flags
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]