FROM amazoncorretto:17-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY donpetre-knowledge-ingestion/pom.xml donpetre-knowledge-ingestion/

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B -f donpetre-knowledge-ingestion/pom.xml

# Copy source code
COPY donpetre-knowledge-ingestion/src donpetre-knowledge-ingestion/src

# Build the application
RUN ./mvnw clean package -DskipTests -B -f donpetre-knowledge-ingestion/pom.xml

# Runtime stage
FROM amazoncorretto:17-alpine AS runtime

# Add security and operational improvements
RUN apk add --no-cache \
    curl \
    jq \
    && addgroup -S appuser \
    && adduser -S appuser -G appuser

WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /app/donpetre-knowledge-ingestion/target/donpetre-knowledge-ingestion.jar app.jar

# Create directories and set permissions
RUN mkdir -p /app/logs /app/temp \
    && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Expose port
EXPOSE 8081

# JVM optimizations
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=docker"

# Entry point
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]