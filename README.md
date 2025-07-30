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