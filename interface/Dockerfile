# =====================================================================
# Interface Service — Multi-stage Dockerfile
# Stage 1: build the fat JAR with Maven
# Stage 2: lean runtime image (Java 21 JRE)
# =====================================================================

# ----------  Build stage  -------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Cache dependency layer separately from source
COPY .mvn/         .mvn/
COPY mvnw           mvnw
COPY pom.xml        pom.xml

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build (skip tests — run in CI separately)
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ----------  Runtime stage  -----------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S tailor && adduser -S tailor -G tailor
USER tailor

WORKDIR /app

# Copy only the executable JAR
COPY --from=builder /build/target/*.jar app.jar

# Actuator health probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-jar", "app.jar"]
