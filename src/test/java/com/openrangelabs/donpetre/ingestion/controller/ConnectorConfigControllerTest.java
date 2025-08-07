package com.openrangelabs.donpetre.ingestion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrangelabs.donpetre.ingestion.dto.CreateConnectorConfigRequest;
import com.openrangelabs.donpetre.ingestion.dto.UpdateConnectorConfigRequest;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.repository.ConnectorConfigRepository;
import com.openrangelabs.donpetre.ingestion.service.ConnectorConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(ConnectorConfigController.class)
class ConnectorConfigControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ConnectorConfigService configService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private ConnectorConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new ConnectorConfig();
        testConfig.setId(UUID.randomUUID());
        testConfig.setConnectorType("github");
        testConfig.setName("test-config");
        testConfig.setEnabled(true);
        testConfig.setCreatedAt(LocalDateTime.now());
        testConfig.setCreatedBy(UUID.randomUUID());

        try {
            JsonNode configNode = objectMapper.readTree("""
                {
                    "base_url": "https://api.github.com",
                    "organization": "test-org"
                }
                """);
            testConfig.setConfiguration(configNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createConfiguration_Success() throws Exception {
        // Arrange
        CreateConnectorConfigRequest request = new CreateConnectorConfigRequest();
        request.setConnectorType("github");
        request.setName("new-config");
        request.setConfiguration(objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "new-org"
            }
            """));

        when(configService.createConfiguration(eq("github"), eq("new-config"), any(), any()))
            .thenReturn(Mono.just(testConfig));

        // Act & Assert
        webTestClient.post()
            .uri("/api/connectors")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(ConnectorConfig.class)
            .value(config -> {
                assertThat(config.getId()).isEqualTo(testConfig.getId());
                assertThat(config.getConnectorType()).isEqualTo("github");
                assertThat(config.getName()).isEqualTo("test-config");
            });

        verify(configService).createConfiguration(eq("github"), eq("new-config"), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createConfiguration_InvalidRequest_BadRequest() {
        // Arrange
        CreateConnectorConfigRequest invalidRequest = new CreateConnectorConfigRequest();
        // Missing required fields

        // Act & Assert
        webTestClient.post()
            .uri("/api/connectors")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().isBadRequest();

        verify(configService, never()).createConfiguration(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createConfiguration_ServiceError_BadRequest() throws Exception {
        // Arrange
        CreateConnectorConfigRequest request = new CreateConnectorConfigRequest();
        request.setConnectorType("github");
        request.setName("duplicate-config");
        request.setConfiguration(objectMapper.readTree("{}"));

        when(configService.createConfiguration(any(), any(), any(), any()))
            .thenReturn(Mono.error(new IllegalArgumentException("Configuration already exists")));

        // Act & Assert
        webTestClient.post()
            .uri("/api/connectors")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser(roles = "USER")
    void createConfiguration_InsufficientPermissions_Forbidden() throws Exception {
        // Arrange
        CreateConnectorConfigRequest request = new CreateConnectorConfigRequest();
        request.setConnectorType("github");
        request.setName("new-config");
        request.setConfiguration(objectMapper.readTree("{}"));

        // Act & Assert
        webTestClient.post()
            .uri("/api/connectors")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isForbidden();

        verify(configService, never()).createConfiguration(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllConfigurations_Success() {
        // Arrange
        ConnectorConfig config2 = new ConnectorConfig();
        config2.setId(UUID.randomUUID());
        config2.setConnectorType("jira");
        config2.setName("jira-config");

        when(configService.getConfigurationsByType(null))
            .thenReturn(Flux.just(testConfig, config2));

        // Act & Assert
        webTestClient.get()
            .uri("/api/connectors")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ConnectorConfig.class)
            .hasSize(2);

        verify(configService).getConfigurationsByType(null);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getConfigurationsByType_Success() {
        // Arrange
        when(configService.getConfigurationsByType("github"))
            .thenReturn(Flux.just(testConfig));

        // Act & Assert
        webTestClient.get()
            .uri("/api/connectors/type/github")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ConnectorConfig.class)
            .hasSize(1);

        verify(configService).getConfigurationsByType("github");
    }

    @Test
    @WithMockUser(roles = "USER")
    void getEnabledConfigurations_Success() {
        // Arrange
        when(configService.getEnabledConfigurations())
            .thenReturn(Flux.just(testConfig));

        // Act & Assert
        webTestClient.get()
            .uri("/api/connectors/enabled")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(ConnectorConfig.class)
            .hasSize(1);

        verify(configService).getEnabledConfigurations();
    }

    @Test
    @WithMockUser(roles = "USER")
    void getConfiguration_Found() {
        // Arrange
        UUID configId = testConfig.getId();
        when(configService.getConfiguration(configId))
            .thenReturn(Mono.just(testConfig));

        // Act & Assert
        webTestClient.get()
            .uri("/api/connectors/{id}", configId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(ConnectorConfig.class)
            .value(config -> {
                assertThat(config.getId()).isEqualTo(configId);
                assertThat(config.getConnectorType()).isEqualTo("github");
            });

        verify(configService).getConfiguration(configId);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getConfiguration_NotFound() {
        // Arrange
        UUID configId = UUID.randomUUID();
        when(configService.getConfiguration(configId))
            .thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.get()
            .uri("/api/connectors/{id}", configId)
            .exchange()
            .expectStatus().isNotFound();

        verify(configService).getConfiguration(configId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateConfiguration_Success() throws Exception {
        // Arrange
        UUID configId = testConfig.getId();
        UpdateConnectorConfigRequest request = new UpdateConnectorConfigRequest();
        request.setConfiguration(objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "updated-org"
            }
            """));

        ConnectorConfig updatedConfig = new ConnectorConfig();
        updatedConfig.setId(configId);
        updatedConfig.setConfiguration(request.getConfiguration());

        when(configService.updateConfiguration(eq(configId), any()))
            .thenReturn(Mono.just(updatedConfig));

        // Act & Assert
        webTestClient.put()
            .uri("/api/connectors/{id}", configId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(ConnectorConfig.class)
            .value(config -> {
                assertThat(config.getId()).isEqualTo(configId);
            });

        verify(configService).updateConfiguration(eq(configId), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateConfiguration_NotFound_BadRequest() throws Exception {
        // Arrange
        UUID configId = UUID.randomUUID();
        UpdateConnectorConfigRequest request = new UpdateConnectorConfigRequest();
        request.setConfiguration(objectMapper.readTree("{}"));

        when(configService.updateConfiguration(eq(configId), any()))
            .thenReturn(Mono.error(new RuntimeException("Configuration not found")));

        // Act & Assert
        webTestClient.put()
            .uri("/api/connectors/{id}", configId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setEnabled_Success() {
        // Arrange
        UUID configId = testConfig.getId();
        Map<String, Boolean> request = Map.of("enabled", false);

        ConnectorConfig updatedConfig = new ConnectorConfig();
        updatedConfig.setId(configId);
        updatedConfig.setEnabled(false);

        when(configService.setEnabled(configId, false))
            .thenReturn(Mono.just(updatedConfig));

        // Act & Assert
        webTestClient.patch()
            .uri("/api/connectors/{id}/enabled", configId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(ConnectorConfig.class)
            .value(config -> {
                assertThat(config.getId()).isEqualTo(configId);
                assertThat(config.getEnabled()).isFalse();
            });

        verify(configService).setEnabled(configId, false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteConfiguration_Success() {
        // Arrange
        UUID configId = testConfig.getId();
        when(configService.deleteConfiguration(configId))
            .thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.delete()
            .uri("/api/connectors/{id}", configId)
            .exchange()
            .expectStatus().isNoContent();

        verify(configService).deleteConfiguration(configId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteConfiguration_Error_BadRequest() {
        // Arrange
        UUID configId = UUID.randomUUID();
        when(configService.deleteConfiguration(configId))
            .thenReturn(Mono.error(new RuntimeException("Delete failed")));

        // Act & Assert
        webTestClient.delete()
            .uri("/api/connectors/{id}", configId)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getConnectorStats_Success() {
        // Arrange
        when(configService.getConnectorTypeStats())
            .thenReturn(Flux.just(
                new ConnectorConfigRepository.ConnectorTypeStats() {
                    @Override
                    public String getConnectorType() { return "github"; }
                    @Override
                    public Long getTotalCount() { return 5L; }
                    @Override
                    public Long getEnabledCount() { return 3L; }
                },
                new ConnectorConfigRepository.ConnectorTypeStats() {
                    @Override
                    public String getConnectorType() { return "jira"; }
                    @Override
                    public Long getTotalCount() { return 2L; }
                    @Override
                    public Long getEnabledCount() { return 1L; }
                }
            ));

        // Act & Assert
        webTestClient.get()
            .uri("/api/connectors/stats")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Map.class)
            .hasSize(2);

        verify(configService).getConnectorTypeStats();
    }

    @Test
    void getAllConfigurations_Unauthenticated_Unauthorized() {
        // Act & Assert
        webTestClient.get()
            .uri("/api/connectors")
            .exchange()
            .expectStatus().isUnauthorized();

        verify(configService, never()).getConfigurationsByType(any());
    }
}