package com.openrangelabs.donpetre.ingestion.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrangelabs.donpetre.ingestion.TestSecurityConfiguration;
import com.openrangelabs.donpetre.ingestion.dto.CreateConnectorConfigRequest;
import com.openrangelabs.donpetre.ingestion.dto.StoreCredentialRequest;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.repository.ApiCredentialRepository;
import com.openrangelabs.donpetre.ingestion.repository.ConnectorConfigRepository;
import com.openrangelabs.donpetre.ingestion.repository.IngestionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestSecurityConfiguration.class)
class IngestionIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ConnectorConfigRepository configRepository;

    @Autowired
    private ApiCredentialRepository credentialRepository;

    @Autowired
    private IngestionJobRepository jobRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Clean up repositories
        configRepository.deleteAll().block();
        credentialRepository.deleteAll().block();
        jobRepository.deleteAll().block();
    }

    @Test
    void fullConnectorLifecycle_Success() throws Exception {
        // Step 1: Create connector configuration
        CreateConnectorConfigRequest createRequest = new CreateConnectorConfigRequest();
        createRequest.setConnectorType("github");
        createRequest.setName("test-integration-config");
        createRequest.setConfiguration(objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "test-org",
                "include_issues": true,
                "include_pull_requests": true
            }
            """));

        ConnectorConfig createdConfig = webTestClient.post()
            .uri("/api/connectors")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(ConnectorConfig.class)
            .returnResult()
            .getResponseBody();

        assertThat(createdConfig).isNotNull();
        UUID configId = createdConfig.getId();

        // Step 2: Store credentials for the connector
        StoreCredentialRequest credentialRequest = new StoreCredentialRequest();
        credentialRequest.setConnectorConfigId(configId);
        credentialRequest.setCredentialType("api_token");
        credentialRequest.setValue("ghp_test_token_123");

        webTestClient.post()
            .uri("/api/credentials")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(credentialRequest)
            .exchange()
            .expectStatus().isCreated();

        // Step 3: Get the configuration
        webTestClient.get()
            .uri("/api/connectors/{id}", configId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(ConnectorConfig.class)
            .value(config -> {
                assertThat(config.getId()).isEqualTo(configId);
                assertThat(config.getConnectorType()).isEqualTo("github");
                assertThat(config.getName()).isEqualTo("test-integration-config");
                assertThat(config.getEnabled()).isTrue();
            });

        // Step 4: List configurations
        webTestClient.get()
            .uri("/api/connectors")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ConnectorConfig.class)
            .hasSize(1);

        // Step 5: List credentials for the connector
        webTestClient.get()
            .uri("/api/credentials/connector/{connectorId}", configId)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class)
            .hasSize(1);

        // Step 6: Update configuration
        JsonNode updatedConfig = objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "updated-org",
                "include_issues": true,
                "include_pull_requests": false,
                "include_commits": true
            }
            """);

        webTestClient.put()
            .uri("/api/connectors/{id}", configId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("configuration", updatedConfig))
            .exchange()
            .expectStatus().isOk();

        // Step 7: Disable the connector
        webTestClient.patch()
            .uri("/api/connectors/{id}/enabled", configId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("enabled", false))
            .exchange()
            .expectStatus().isOk()
            .expectBody(ConnectorConfig.class)
            .value(config -> {
                assertThat(config.getEnabled()).isFalse();
            });

        // Step 8: Test connection (would fail since it's a mock token)
        webTestClient.post()
            .uri("/api/ingestion/test-connection")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "connectorType", "github",
                "configName", "test-integration-config"
            ))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isBoolean();

        // Step 9: Get connector statistics
        webTestClient.get()
            .uri("/api/connectors/stats")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class);

        // Step 10: Clean up - delete the configuration
        webTestClient.delete()
            .uri("/api/connectors/{id}", configId)
            .exchange()
            .expectStatus().isNoContent();

        // Verify deletion
        webTestClient.get()
            .uri("/api/connectors/{id}", configId)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void connectorConfigValidation_InvalidRequest() throws Exception {
        // Test creating configuration with invalid data
        CreateConnectorConfigRequest invalidRequest = new CreateConnectorConfigRequest();
        invalidRequest.setConnectorType("github");
        invalidRequest.setName("invalid-config");
        // Missing required configuration fields

        webTestClient.post()
            .uri("/api/connectors")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void credentialDuplication_ReturnsError() throws Exception {
        // Create a connector first
        CreateConnectorConfigRequest createRequest = new CreateConnectorConfigRequest();
        createRequest.setConnectorType("github");
        createRequest.setName("test-config");
        createRequest.setConfiguration(objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "test-org"
            }
            """));

        ConnectorConfig config = webTestClient.post()
            .uri("/api/connectors")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(ConnectorConfig.class)
            .returnResult()
            .getResponseBody();

        // Store credential
        StoreCredentialRequest credentialRequest = new StoreCredentialRequest();
        credentialRequest.setConnectorConfigId(config.getId());
        credentialRequest.setCredentialType("api_token");
        credentialRequest.setValue("token1");

        webTestClient.post()
            .uri("/api/credentials")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(credentialRequest)
            .exchange()
            .expectStatus().isCreated();

        // Try to store the same credential again
        credentialRequest.setValue("token2");

        webTestClient.post()
            .uri("/api/credentials")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(credentialRequest)
            .exchange()
            .expectStatus().isBadRequest(); // Should fail due to duplication
    }

    @Test
    void triggerSyncJob_Success() throws Exception {
        // Create and set up a connector configuration
        CreateConnectorConfigRequest createRequest = new CreateConnectorConfigRequest();
        createRequest.setConnectorType("github");
        createRequest.setName("sync-test-config");
        createRequest.setConfiguration(objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "test-org"
            }
            """));

        ConnectorConfig config = webTestClient.post()
            .uri("/api/connectors")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createRequest)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(ConnectorConfig.class)
            .returnResult()
            .getResponseBody();

        // Store credentials
        StoreCredentialRequest credentialRequest = new StoreCredentialRequest();
        credentialRequest.setConnectorConfigId(config.getId());
        credentialRequest.setCredentialType("api_token");
        credentialRequest.setValue("test_token");

        webTestClient.post()
            .uri("/api/credentials")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(credentialRequest)
            .exchange()
            .expectStatus().isCreated();

        // Trigger a full sync
        webTestClient.post()
            .uri("/api/ingestion/trigger-sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "connectorType", "github",
                "configName", "sync-test-config",
                "syncType", "FULL"
            ))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.connectorType").isEqualTo("github")
            .jsonPath("$.syncType").isEqualTo("FULL");
    }

    @Test
    void jobManagement_Workflow() throws Exception {
        // This test would create jobs and test job management endpoints
        // Implementation depends on the actual job management API structure
        
        // Get all jobs (should be empty initially)
        webTestClient.get()
            .uri("/api/jobs")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Object.class)
            .hasSize(0);

        // Job statistics
        webTestClient.get()
            .uri("/api/jobs/stats")
            .exchange()
            .expectStatus().isOk();
    }

    private static class Map {
        public static java.util.Map<String, Object> of(String key1, Object value1) {
            return java.util.Map.of(key1, value1);
        }

        public static java.util.Map<String, Object> of(String key1, Object value1, String key2, Object value2) {
            return java.util.Map.of(key1, value1, key2, value2);
        }

        public static java.util.Map<String, Object> of(String key1, Object value1, String key2, Object value2, String key3, Object value3) {
            return java.util.Map.of(key1, value1, key2, value2, key3, value3);
        }
    }
}