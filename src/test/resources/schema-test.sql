-- Test schema for Knowledge Ingestion Service
-- Simplified schema for H2 in-memory database

CREATE TABLE IF NOT EXISTS connector_configs (
    id UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    connector_type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    configuration JSON NOT NULL,
    enabled BOOLEAN DEFAULT true,
    created_by UUID,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_sync_at TIMESTAMP,
    UNIQUE(connector_type, name)
);

CREATE TABLE IF NOT EXISTS api_credentials (
    id UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    connector_id UUID NOT NULL,
    credential_name VARCHAR(100) NOT NULL,
    encrypted_value TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP,
    FOREIGN KEY (connector_id) REFERENCES connector_configs(id) ON DELETE CASCADE,
    UNIQUE(connector_id, credential_name)
);

CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    connector_config_id UUID NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    processed_items INTEGER DEFAULT 0,
    failed_items INTEGER DEFAULT 0,
    last_sync_cursor TEXT,
    error_message TEXT,
    metadata JSON,
    FOREIGN KEY (connector_config_id) REFERENCES connector_configs(id) ON DELETE CASCADE
);

-- Indexes for better test performance
CREATE INDEX IF NOT EXISTS idx_connector_configs_type ON connector_configs(connector_type);
CREATE INDEX IF NOT EXISTS idx_connector_configs_enabled ON connector_configs(enabled);
CREATE INDEX IF NOT EXISTS idx_api_credentials_connector ON api_credentials(connector_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_connector ON ingestion_jobs(connector_config_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_status ON ingestion_jobs(status);
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_created ON ingestion_jobs(created_at);

-- Views for testing
CREATE VIEW IF NOT EXISTS connector_stats AS
SELECT 
    connector_type,
    COUNT(*) as total_count,
    COUNT(CASE WHEN enabled = true THEN 1 END) as enabled_count,
    MAX(last_sync_at) as last_sync_time
FROM connector_configs
GROUP BY connector_type;