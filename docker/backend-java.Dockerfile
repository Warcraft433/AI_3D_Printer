# Multi-stage Dockerfile for PrintGuard Java Spring Boot Backend
FROM maven:3.9.6-eclipse-temurin-21-noble AS builder

WORKDIR /build
COPY printguard-migration-starter/backend-java/pom.xml .
# Download dependencies layer
RUN mvn dependency:go-offline -B

# Build application
COPY printguard-migration-starter/backend-java/src ./src
RUN mvn package -DskipTests -B

# Linux runtime image with PrusaSlicer (Ubuntu 24.04 LTS Noble)
FROM eclipse-temurin:21-jre-noble

# Enable universe repository and install PrusaSlicer with dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    software-properties-common \
    && add-apt-repository -y universe \
    && apt-get update && apt-get install -y --no-install-recommends \
    prusa-slicer \
    ca-certificates \
    && apt-get purge -y --auto-remove software-properties-common \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    SLICER_PATH=/usr/bin/prusa-slicer

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
