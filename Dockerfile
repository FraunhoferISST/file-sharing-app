# Build stage
FROM gradle:9.5-jdk21 AS build
WORKDIR /app

# Copy gradle files for dependency caching
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built jar from build stage
COPY --from=build --chown=spring:spring /app/build/libs/*.jar app.jar

# Spring Boot listens on 8080 by default (see k8s service/container ports)
EXPOSE 8080 5005

# Set default profile
ENV SPRING_PROFILES_ACTIVE=prod

# JVM options
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER spring:spring

# Run the application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
