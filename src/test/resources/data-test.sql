-- Test data for Knowledge Ingestion Service
-- Pre-populated test data for consistent testing

-- Test connector configurations
INSERT INTO connector_configs (id, connector_type, name, configuration, enabled, created_by, created_at) VALUES 
('550e8400-e29b-41d4-a716-446655440001', 'github', 'test-github-org', '{"base_url": "https://api.github.com", "organization": "test-org", "include_issues": true, "include_pull_requests": true}', true, '550e8400-e29b-41d4-a716-446655440000', '2024-01-01 10:00:00'),
('550e8400-e29b-41d4-a716-446655440002', 'github', 'test-github-repos', '{"base_url": "https://api.github.com", "repositories": ["repo1", "repo2"], "include_commits": true}', true, '550e8400-e29b-41d4-a716-446655440000', '2024-01-01 11:00:00'),
('550e8400-e29b-41d4-a716-446655440003', 'jira', 'test-jira-project', '{"base_url": "https://company.atlassian.net", "project_key": "TEST", "include_issues": true}', false, '550e8400-e29b-41d4-a716-446655440000', '2024-01-01 12:00:00');

-- Test credentials (encrypted with test key)
INSERT INTO api_credentials (id, connector_id, credential_name, encrypted_value, created_at) VALUES
('650e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'api_token', 'dGVzdC1lbmNyeXB0ZWQtdG9rZW4tMQ==', '2024-01-01 10:30:00'),
('650e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440002', 'api_token', 'dGVzdC1lbmNyeXB0ZWQtdG9rZW4tMg==', '2024-01-01 11:30:00'),
('650e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440003', 'api_token', 'dGVzdC1lbmNyeXB0ZWQtdG9rZW4tMw==', '2024-01-01 12:30:00'),
('650e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440003', 'username', 'dGVzdC11c2VybmFtZQ==', '2024-01-01 12:35:00');

-- Test ingestion jobs with various states
INSERT INTO ingestion_jobs (id, connector_config_id, job_type, status, created_at, started_at, completed_at, processed_items, failed_items, last_sync_cursor) VALUES
('750e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'FULL_SYNC', 'COMPLETED', '2024-01-01 13:00:00', '2024-01-01 13:01:00', '2024-01-01 13:15:00', 150, 2, '2024-01-01T13:15:00Z'),
('750e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440001', 'INCREMENTAL_SYNC', 'COMPLETED', '2024-01-01 14:00:00', '2024-01-01 14:01:00', '2024-01-01 14:05:00', 25, 0, '2024-01-01T14:05:00Z'),
('750e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440002', 'FULL_SYNC', 'FAILED', '2024-01-01 15:00:00', '2024-01-01 15:01:00', '2024-01-01 15:02:00', 0, 0, null),
('750e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440002', 'INCREMENTAL_SYNC', 'RUNNING', '2024-01-01 16:00:00', '2024-01-01 16:01:00', null, 10, 1, null),
('750e8400-e29b-41d4-a716-446655440005', '550e8400-e29b-41d4-a716-446655440003', 'FULL_SYNC', 'PENDING', '2024-01-01 17:00:00', null, null, 0, 0, null);

-- Update last_sync_at for connectors that have completed jobs
UPDATE connector_configs 
SET last_sync_at = '2024-01-01 14:05:00' 
WHERE id = '550e8400-e29b-41d4-a716-446655440001';

UPDATE connector_configs 
SET last_sync_at = '2024-01-01 15:02:00' 
WHERE id = '550e8400-e29b-41d4-a716-446655440002';