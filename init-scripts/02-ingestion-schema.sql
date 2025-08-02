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
CREATE INDEX IF NOT EXISTS idx_connector_configs_updated ON connector_configs(updated_at);

CREATE INDEX IF NOT EXISTS idx_api_credentials_config_id ON api_credentials(connector_config_id);
CREATE INDEX IF NOT EXISTS idx_api_credentials_type ON api_credentials(credential_type);
CREATE INDEX IF NOT EXISTS idx_api_credentials_expires ON api_credentials(expires_at);
CREATE INDEX IF NOT EXISTS idx_api_credentials_active ON api_credentials(is_active);

CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_config_id ON ingestion_jobs(connector_config_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_status ON ingestion_jobs(status);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_started ON ingestion_jobs(started_at);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_completed ON ingestion_jobs(completed_at);

-- Insert default connector configurations (disabled by default)
INSERT INTO connector_configs (connector_type, name, enabled, configuration, created_by) VALUES
                                                                                             ('github', 'default-github', false, '{
                                                                                               "base_url": "https://api.github.com",
                                                                                               "data_types": ["commits", "issues", "pull_requests", "readme"],
                                                                                               "include_forks": false,
                                                                                               "include_archived": false,
                                                                                               "polling_interval_minutes": 30,
                                                                                               "rate_limit": {
                                                                                                 "requests_per_hour": 5000,
                                                                                                 "respect_rate_limits": true
                                                                                               }
                                                                                             }', (SELECT id FROM users WHERE username = 'admin')),

                                                                                             ('gitlab', 'default-gitlab', false, '{
                                                                                               "base_url": "https://gitlab.com/api/v4",
                                                                                               "data_types": ["commits", "issues", "merge_requests", "wiki"],
                                                                                               "include_forks": false,
                                                                                               "include_archived": false,
                                                                                               "polling_interval_minutes": 15,
                                                                                               "rate_limit": {
                                                                                                 "requests_per_minute": 300,
                                                                                                 "respect_rate_limits": true
                                                                                               }
                                                                                             }', (SELECT id FROM users WHERE username = 'admin')),

                                                                                             ('jira', 'default-jira', false, '{
                                                                                               "base_url": "https://your-company.atlassian.net",
                                                                                               "projects": [],
                                                                                               "issue_types": ["Story", "Bug", "Task", "Epic"],
                                                                                               "custom_fields": [],
                                                                                               "polling_interval_minutes": 10,
                                                                                               "max_results_per_request": 100
                                                                                             }', (SELECT id FROM users WHERE username = 'admin')),

                                                                                             ('slack', 'default-slack', false, '{
                                                                                               "workspace_url": "https://your-company.slack.com",
                                                                                               "channels": [],
                                                                                               "include_private_channels": false,
                                                                                               "message_history_days": 30,
                                                                                               "include_threads": true,
                                                                                               "include_files": true,
                                                                                               "polling_interval_minutes": 5
                                                                                             }', (SELECT id FROM users WHERE username = 'admin'))
ON CONFLICT (connector_type, name) DO NOTHING;

-- Database migration to add sync tracking fields to connector_configs table
-- File: src/main/resources/db/migration/V1_2__Add_sync_tracking_to_connector_configs.sql

-- Add sync tracking columns to connector_configs table
ALTER TABLE connector_configs
    ADD COLUMN last_sync_time TIMESTAMP;

ALTER TABLE connector_configs
    ADD COLUMN last_sync_cursor VARCHAR(500);

ALTER TABLE connector_configs
    ADD COLUMN sync_interval_minutes INTEGER DEFAULT 60;

ALTER TABLE connector_configs
    ADD COLUMN last_successful_sync TIMESTAMP;

ALTER TABLE connector_configs
    ADD COLUMN consecutive_error_count INTEGER DEFAULT 0;

ALTER TABLE connector_configs
    ADD COLUMN last_error_message TEXT;

ALTER TABLE connector_configs
    ADD COLUMN last_error_time TIMESTAMP;

-- Add indexes for performance
CREATE INDEX idx_connector_configs_last_sync_time ON connector_configs(last_sync_time);
CREATE INDEX idx_connector_configs_enabled_sync_interval ON connector_configs(enabled, sync_interval_minutes);
CREATE INDEX idx_connector_configs_error_count ON connector_configs(consecutive_error_count);

-- Update existing records to have default sync interval
UPDATE connector_configs
SET sync_interval_minutes = 60
WHERE sync_interval_minutes IS NULL;

-- Add check constraint to ensure positive sync interval
ALTER TABLE connector_configs
    ADD CONSTRAINT chk_sync_interval_positive
        CHECK (sync_interval_minutes IS NULL OR sync_interval_minutes > 0);

-- Add check constraint to ensure error count is non-negative
ALTER TABLE connector_configs
    ADD CONSTRAINT chk_error_count_non_negative
        CHECK (consecutive_error_count >= 0);