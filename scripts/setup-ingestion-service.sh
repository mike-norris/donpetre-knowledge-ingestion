#!/bin/bash
# Setup script for Knowledge Ingestion Service
# scripts/setup-ingestion-service.sh

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PROJECT_DIR="knowledge-ingestion"
SECRETS_DIR="$PROJECT_DIR/secrets"

echo -e "${GREEN}🚀 Setting up Knowledge Ingestion Service${NC}"

# Function to print section headers
print_section() {
    echo -e "\n${BLUE}===== $1 =====${NC}"
}

# Check if we're in the right directory structure
check_project_structure() {
    print_section "Checking Project Structure"

    if [ ! -d "api-gateway" ]; then
        echo -e "${RED}❌ This script should be run from the main project directory containing api-gateway${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ Project structure looks correct${NC}"
}

# Create ingestion service directory structure
create_project_structure() {
    print_section "Creating Ingestion Service Structure"

    mkdir -p "$PROJECT_DIR"/{src/{main/{java/com/openrangelabs/donpetre/ingestion/{connector/{github,gitlab,jira,slack},controller,dto,entity,model,repository,service,config,scheduler},resources},test/java/com/openrangelabs/donpetre/ingestion},scripts,init-scripts}

    echo -e "${GREEN}✓ Created project directory structure${NC}"
}

# Create secrets directory
setup_secrets() {
    print_section "Setting up Secrets Management"

    mkdir -p "$SECRETS_DIR"
    chmod 700 "$SECRETS_DIR"

    # Generate sample API key placeholders
    cat > "$SECRETS_DIR/github-token.example" << EOF
# GitHub Personal Access Token or GitHub App Token
# Scopes needed: repo, read:org, read:user
ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
EOF

    cat > "$SECRETS_DIR/gitlab-token.example" << EOF
# GitLab Personal Access Token or Project Access Token
# Scopes needed: api, read_api, read_repository
glpat-xxxxxxxxxxxxxxxxxxxx
EOF

    cat > "$SECRETS_DIR/jira-token.example" << EOF
# Jira API Token (for Atlassian Cloud)
# Or password for Jira Server
ATATTxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
EOF

    cat > "$SECRETS_DIR/slack-token.example" << EOF
# Slack Bot Token
# Scopes needed: channels:history, groups:history, im:history, mpim:history, files:read
xoxb-xxxxxxxxxx-xxxxxxxxxx-xxxxxxxxxxxxxxxxxxxxxxxx
EOF

    echo -e "${GREEN}✓ Created secrets directory with example files${NC}"
    echo -e "${YELLOW}⚠️  Copy .example files and add real tokens (never commit real tokens!)${NC}"
}

# Create Docker setup
create_docker_setup() {
    print_section "Creating Docker Configuration"

    # Create Dockerfile for ingestion service
    cat > "$PROJECT_DIR/Dockerfile" << 'EOF'
FROM amazoncorretto:17-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM amazoncorretto:17-alpine AS runtime

RUN apk add --no-cache curl jq \
    && addgroup -S appuser \
    && adduser -S appuser -G appuser

WORKDIR /app

COPY --from=builder /app/target/knowledge-ingestion-*.jar app.jar

RUN mkdir -p /app/logs /app/temp \
    && chown -R appuser:appuser /app

USER appuser

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
EOF

    echo -e "${GREEN}✓ Created Dockerfile for ingestion service${NC}"
}

# Update main docker-compose.yml
update_main_docker_compose() {
    print_section "Updating Main Docker Compose"

    # Check if docker-compose.yml exists
    if [ ! -f "docker-compose.yml" ]; then
        echo -e "${YELLOW}⚠️  Main docker-compose.yml not found, skipping update${NC}"
        return
    fi

    # Add ingestion service to main docker-compose.yml if not already present
    if ! grep -q "knowledge-ingestion" docker-compose.yml; then
        cat >> docker-compose.yml << 'EOF'

  knowledge-ingestion:
    build:
      context: ./knowledge-ingestion
      dockerfile: Dockerfile
    container_name: donpetre-ingestion
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: postgresql
      DB_PORT: 5432
      DB_NAME: donpetre
      DB_USERNAME: ${DB_USER:-don}
      DB_PASSWORD: ${DB_PASSWORD:-don_pass}
      GATEWAY_HOST: api-gateway
      GATEWAY_PORT: 8080
      LOG_LEVEL: ${LOG_LEVEL:-INFO}
      JAVA_OPTS: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
    depends_on:
      postgresql:
        condition: service_healthy
      api-gateway:
        condition: service_healthy
    networks:
      - donpetre-network
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
EOF
        echo -e "${GREEN}✓ Added ingestion service to main docker-compose.yml${NC}"
    else
        echo -e "${YELLOW}⚠️  Ingestion service already exists in docker-compose.yml${NC}"
    fi
}

# Create build script
create_build_script() {
    print_section "Creating Build Script"

    cat > "$PROJECT_DIR/scripts/build.sh" << 'EOF'
#!/bin/bash
# Build script for Knowledge Ingestion Service

set -euo pipefail

echo "🔨 Building Knowledge Ingestion Service..."

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "❌ pom.xml not found. Run this script from the knowledge-ingestion directory."
    exit 1
fi

# Clean and build
echo "📦 Cleaning and building..."
./mvnw clean package -DskipTests

# Check if JAR was built
if [ -f "target/knowledge-ingestion-1.0.0-SNAPSHOT.jar" ]; then
    echo "✅ Build successful!"
    ls -lh target/knowledge-ingestion-1.0.0-SNAPSHOT.jar
else
    echo "❌ Build failed - JAR not found"
    exit 1
fi

echo "🎉 Knowledge Ingestion Service build completed!"
EOF

    chmod +x "$PROJECT_DIR/scripts/build.sh"
    echo -e "${GREEN}✓ Created build script${NC}"
}

# Create integration test script
create_test_script() {
    print_section "Creating Test Script"

    cat > "$PROJECT_DIR/scripts/test-connectors.sh" << 'EOF'
#!/bin/bash
# Test script for connector configurations

set -euo pipefail

BASE_URL="http://localhost:8081"
GATEWAY_URL="http://localhost:8080"

echo "🧪 Testing Knowledge Ingestion Service Connectors..."

# Function to test endpoint
test_endpoint() {
    local endpoint=$1
    local expected_status=${2:-200}
    local method=${3:-GET}

    echo "Testing $method $endpoint..."

    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "%{http_code}" -o /tmp/response.json "$BASE_URL$endpoint" || echo "000")
    else
        response=$(curl -s -w "%{http_code}" -o /tmp/response.json -X "$method" "$BASE_URL$endpoint" || echo "000")
    fi

    if [ "$response" = "$expected_status" ]; then
        echo "✅ $endpoint - Status: $response"
        return 0
    else
        echo "❌ $endpoint - Expected: $expected_status, Got: $response"
        if [ -f /tmp/response.json ]; then
            echo "Response: $(cat /tmp/response.json)"
        fi
        return 1
    fi
}

# Wait for service to be ready
echo "⏳ Waiting for ingestion service to be ready..."
for i in {1..30}; do
    if curl -s "$BASE_URL/actuator/health" >/dev/null 2>&1; then
        echo "✅ Service is ready!"
        break
    fi
    echo "Waiting... ($i/30)"
    sleep 2
done

# Test health endpoint
test_endpoint "/actuator/health"

# Test available connectors
test_endpoint "/api/connectors"

# Test connector stats
test_endpoint "/api/connectors/stats"

# Test job metrics
test_endpoint "/api/jobs/stats/by-connector"

echo "🎉 Connector tests completed!"
EOF

    chmod +x "$PROJECT_DIR/scripts/test-connectors.sh"
    echo -e "${GREEN}✓ Created test script${NC}"
}

# Create README
create_readme() {
    print_section "Creating Documentation"

    cat > "$PROJECT_DIR/README.md" << 'EOF'
# Knowledge Ingestion Service

The Knowledge Ingestion Service is responsible for collecting data from various external sources and transforming it into knowledge items for the DonPetre platform.

## Features

- **Multi-Source Connectors**: GitHub, GitLab, Jira, Slack
- **Configurable Ingestion**: Database-stored configurations with enable/disable toggles
- **Secure Credential Management**: Encrypted API key storage with expiration tracking
- **Scheduled Data Collection**: Configurable polling intervals with incremental sync
- **Rate Limiting**: Respects API rate limits for all external services
- **Monitoring**: Comprehensive metrics and job tracking

## Quick Start

### 1. Build the Service
```bash
cd knowledge-ingestion
./scripts/build.sh
```

### 2. Set up Secrets
```bash
# Copy example files and add real tokens
cp secrets/github-token.example secrets/github-token.txt
cp secrets/gitlab-token.example secrets/gitlab-token.txt
# Add your real tokens to these files
```

### 3. Start with Docker
```bash
# From the main project directory
docker-compose up -d knowledge-ingestion
```

### 4. Test the Service
```bash
cd knowledge-ingestion
./scripts/test-connectors.sh
```

## API Endpoints

### Connector Configuration
- `GET /api/connectors` - List all configurations
- `POST /api/connectors` - Create new configuration
- `PUT /api/connectors/{id}` - Update configuration
- `PATCH /api/connectors/{id}/enabled` - Enable/disable connector

### Ingestion Operations
- `POST /api/ingestion/sync/full` - Trigger full sync
- `POST /api/ingestion/sync/incremental` - Trigger incremental sync
- `POST /api/ingestion/test-connection` - Test connector connection
- `GET /api/ingestion/metrics/{type}/{name}` - Get connector metrics

### Credential Management
- `POST /api/credentials` - Store encrypted credential
- `GET /api/credentials/connector/{id}` - List credentials
- `PUT /api/credentials/{id}` - Update credential
- `DELETE /api/credentials/{id}` - Deactivate credential

### Job Monitoring
- `GET /api/jobs/running` - List running jobs
- `GET /api/jobs/connector/{id}` - Jobs for specific connector
- `GET /api/jobs/metrics/performance` - Performance metrics

## Configuration Examples

### GitHub Connector
```json
{
  "base_url": "https://api.github.com",
  "organization": "your-org",
  "repositories": ["repo1", "repo2"],
  "data_types": ["commits", "issues", "pull_requests", "readme"],
  "include_forks": false,
  "polling_interval_minutes": 30
}
```

### Jira Connector
```json
{
  "base_url": "https://company.atlassian.net",
  "projects": ["PROJ1", "PROJ2"],
  "issue_types": ["Story", "Bug", "Task"],
  "polling_interval_minutes": 15
}
```

## Monitoring

- Health: `GET /actuator/health`
- Metrics: `GET /actuator/metrics`
- Job Statistics: `GET /api/jobs/stats/by-connector`

## Security

- All API keys are encrypted using AES-256
- JWT-based authentication required for all endpoints
- Role-based access control (ADMIN/USER)
- Credential expiration tracking and alerts
EOF

    echo -e "${GREEN}✓ Created README.md${NC}"
}

# Create Maven POM template
create_maven_pom() {
    print_section "Creating Maven POM"

    cat > "$PROJECT_DIR/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.openrangelabs.donpetre</groupId>
    <artifactId>knowledge-ingestion</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>DonPetre Knowledge Ingestion Service</name>
    <description>Knowledge ingestion service for external data sources</description>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2024.0.0</spring-cloud.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>r2dbc-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- External API Clients -->
        <dependency>
            <groupId>org.kohsuke</groupId>
            <artifactId>github-api</artifactId>
            <version>1.318</version>
        </dependency>
        <dependency>
            <groupId>org.gitlab4j</groupId>
            <artifactId>gitlab4j-api</artifactId>
            <version>5.5.0</version>
        </dependency>
        <dependency>
            <groupId>com.slack.api</groupId>
            <artifactId>slack-api-client</artifactId>
            <version>1.29.2</version>
        </dependency>

        <!-- Circuit Breaker and Resilience -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
        </dependency>

        <!-- Configuration processing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF

    echo -e "${GREEN}✓ Created Maven POM file${NC}"
}

# Create application configuration
create_application_config() {
    print_section "Creating Application Configuration"

    mkdir -p "$PROJECT_DIR/src/main/resources"

    cat > "$PROJECT_DIR/src/main/resources/application.yml" << 'EOF'
server:
  port: 8081
  shutdown: graceful

spring:
  application:
    name: knowledge-ingestion-service

  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  # R2DBC Configuration
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:donpetre}
    username: ${DB_USERNAME:don}
    password: ${DB_PASSWORD:don_pass}
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m

  # Security Configuration
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://${GATEWAY_HOST:localhost}:${GATEWAY_PORT:8080}

# Actuator Configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,scheduledtasks
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized

# Logging Configuration
logging:
  level:
    com.openrangelabs.donpetre.ingestion: ${LOG_LEVEL:DEBUG}
    org.springframework.security: ${SECURITY_LOG_LEVEL:INFO}
    org.springframework.r2dbc: ${R2DBC_LOG_LEVEL:INFO}

# Custom Application Configuration
ingestion:
  connectors:
    github:
      enabled: false
      polling-interval-minutes: 30
    gitlab:
      enabled: false
      polling-interval-minutes: 15
    jira:
      enabled: false
      polling-interval-minutes: 10
    slack:
      enabled: false
      polling-interval-minutes: 5

---
# Docker Profile
spring:
  config:
    activate:
      on-profile: docker

  r2dbc:
    url: r2dbc:postgresql://postgresql:5432/donpetre
    username: don
    password: don_pass
EOF

    echo -e "${GREEN}✓ Created application configuration${NC}"
}

# Create database schema
create_database_schema() {
    print_section "Creating Database Schema"

    mkdir -p "$PROJECT_DIR/init-scripts"

    cat > "$PROJECT_DIR/init-scripts/02-ingestion-schema.sql" << 'EOF'
-- Additional schema for ingestion service

-- Connector configurations table
CREATE TABLE IF NOT EXISTS connector_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT false,
    configuration JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    created_by UUID REFERENCES users(id),
    UNIQUE(connector_type, name)
);

-- API credentials with expiration tracking
CREATE TABLE IF NOT EXISTS api_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_config_id UUID REFERENCES connector_configs(id) ON DELETE CASCADE,
    credential_type VARCHAR(50) NOT NULL,
    encrypted_value TEXT NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    last_used TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

-- Ingestion jobs tracking
CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_config_id UUID REFERENCES connector_configs(id),
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_sync_cursor TEXT,
    items_processed INTEGER DEFAULT 0,
    items_failed INTEGER DEFAULT 0,
    error_message TEXT,
    metadata JSONB
);

-- Update knowledge_sources table
ALTER TABLE knowledge_sources ADD COLUMN IF NOT EXISTS
    connector_config_id UUID REFERENCES connector_configs(id);
ALTER TABLE knowledge_sources ADD COLUMN IF NOT EXISTS
    last_ingestion_job_id UUID REFERENCES ingestion_jobs(id);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_connector_configs_type ON connector_configs(connector_type);
CREATE INDEX IF NOT EXISTS idx_connector_configs_enabled ON connector_configs(enabled);
CREATE INDEX IF NOT EXISTS idx_api_credentials_config_id ON api_credentials(connector_config_id);
CREATE INDEX IF NOT EXISTS idx_api_credentials_expires ON api_credentials(expires_at);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_config_id ON ingestion_jobs(connector_config_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_status ON ingestion_jobs(status);

-- Insert default connector configurations (disabled by default)
INSERT INTO connector_configs (connector_type, name, enabled, configuration, created_by) VALUES
    ('github', 'default-github', false, '{
        "base_url": "https://api.github.com",
        "data_types": ["commits", "issues", "pull_requests", "readme"],
        "include_forks": false,
        "include_archived": false,
        "polling_interval_minutes": 30
    }', (SELECT id FROM users WHERE username = 'admin' LIMIT 1)),

    ('gitlab', 'default-gitlab', false, '{
        "base_url": "https://gitlab.com/api/v4",
        "data_types": ["commits", "issues", "merge_requests", "wiki"],
        "polling_interval_minutes": 15
    }', (SELECT id FROM users WHERE username = 'admin' LIMIT 1)),

    ('jira', 'default-jira', false, '{
        "base_url": "https://your-company.atlassian.net",
        "projects": [],
        "issue_types": ["Story", "Bug", "Task", "Epic"],
        "polling_interval_minutes": 10
    }', (SELECT id FROM users WHERE username = 'admin' LIMIT 1)),

    ('slack', 'default-slack', false, '{
        "workspace_url": "https://your-company.slack.com",
        "channels": [],
        "include_private_channels": false,
        "polling_interval_minutes": 5
    }', (SELECT id FROM users WHERE username = 'admin' LIMIT 1))
ON CONFLICT (connector_type, name) DO NOTHING;
EOF

    echo -e "${GREEN}✓ Created database schema${NC}"
}

# Copy to init-scripts directory for main database
copy_to_main_init_scripts() {
    print_section "Copying to Main Init Scripts"

    if [ -d "init-scripts" ]; then
        cp "$PROJECT_DIR/init-scripts/02-ingestion-schema.sql" "init-scripts/"
        echo -e "${GREEN}✓ Copied schema to main init-scripts directory${NC}"
    else
        echo -e "${YELLOW}⚠️  Main init-scripts directory not found${NC}"
    fi
}

# Main execution
main() {
    echo -e "${GREEN}🎯 Knowledge Ingestion Service Setup${NC}"
    echo -e "${GREEN}=====================================${NC}"

    check_project_structure
    create_project_structure
    setup_secrets
    create_docker_setup
    update_main_docker_compose
    create_build_script
    create_test_script
    create_readme
    create_maven_pom
    create_application_config
    create_database_schema
    copy_to_main_init_scripts

    echo -e "\n${GREEN}🎉 Setup completed successfully!${NC}"
    echo -e "${GREEN}Next steps:${NC}"
    echo -e "  1. Add real API tokens to $SECRETS_DIR/*.txt files"
    echo -e "  2. Copy the Java code files into the appropriate directories"
    echo -e "  3. Run: cd $PROJECT_DIR && ./scripts/build.sh"
    echo -e "  4. Run: docker-compose up -d knowledge-ingestion"
    echo -e "  5. Test: cd $PROJECT_DIR && ./scripts/test-connectors.sh"
    echo -e "\n${YELLOW}⚠️  Remember: Never commit real API tokens to version control!${NC}"
}

# Run main function
main "$@"