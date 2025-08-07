package com.openrangelabs.donpetre.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.repository.ConnectorConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorConfigServiceTest {

    @Mock
    private ConnectorConfigRepository repository;

    private ConnectorConfigService configService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        configService = new ConnectorConfigService(repository);
    }

    @Test
    void createConfiguration_Success() throws Exception {
        // Arrange
        String connectorType = "github";
        String name = "test-github";
        JsonNode configuration = objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "test-org"
            }
            """);
        UUID createdBy = UUID.randomUUID();

        ConnectorConfig savedConfig = new ConnectorConfig();
        savedConfig.setId(UUID.randomUUID());
        savedConfig.setConnectorType(connectorType);
        savedConfig.setName(name);
        savedConfig.setConfiguration(configuration);
        savedConfig.setCreatedBy(createdBy);
        savedConfig.setEnabled(true);
        savedConfig.setCreatedAt(LocalDateTime.now());

        when(repository.existsByConnectorTypeAndName(connectorType, name))
            .thenReturn(Mono.just(false));
        when(repository.save(any(ConnectorConfig.class))).thenReturn(Mono.just(savedConfig));

        // Act & Assert
        StepVerifier.create(configService.createConfiguration(connectorType, name, configuration, createdBy))
            .expectNextMatches(config -> {
                assertThat(config.getId()).isEqualTo(savedConfig.getId());
                assertThat(config.getConnectorType()).isEqualTo(connectorType);
                assertThat(config.getName()).isEqualTo(name);
                assertThat(config.getConfiguration()).isEqualTo(configuration);
                assertThat(config.getCreatedBy()).isEqualTo(createdBy);
                assertThat(config.getEnabled()).isTrue();
                return true;
            })
            .verifyComplete();

        verify(repository).save(any(ConnectorConfig.class));
    }

    @Test
    void createConfiguration_AlreadyExists() throws Exception {
        // Arrange
        String connectorType = "github";
        String name = "existing-config";
        JsonNode configuration = objectMapper.readTree("{}");
        UUID createdBy = UUID.randomUUID();

        when(repository.existsByConnectorTypeAndName(connectorType, name))
            .thenReturn(Mono.just(true));

        // Act & Assert
        StepVerifier.create(configService.createConfiguration(connectorType, name, configuration, createdBy))
            .expectErrorMatches(throwable -> 
                throwable instanceof IllegalArgumentException &&
                throwable.getMessage().contains("Configuration already exists"))
            .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void getConfiguration_ByTypeAndName_Found() {
        // Arrange
        String connectorType = "github";
        String name = "test-config";
        ConnectorConfig config = new ConnectorConfig();
        config.setConnectorType(connectorType);
        config.setName(name);

        when(repository.findByConnectorTypeAndName(connectorType, name))
            .thenReturn(Mono.just(config));

        // Act & Assert
        StepVerifier.create(configService.getConfiguration(connectorType, name))
            .expectNext(config)
            .verifyComplete();
    }

    @Test
    void getConfiguration_ByTypeAndName_NotFound() {
        // Arrange
        String connectorType = "github";
        String name = "nonexistent";

        when(repository.findByConnectorTypeAndName(connectorType, name))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(configService.getConfiguration(connectorType, name))
            .verifyComplete();
    }

    @Test
    void getConfiguration_ById_Found() {
        // Arrange
        UUID id = UUID.randomUUID();
        ConnectorConfig config = new ConnectorConfig();
        config.setId(id);

        when(repository.findById(id)).thenReturn(Mono.just(config));

        // Act & Assert
        StepVerifier.create(configService.getConfiguration(id))
            .expectNext(config)
            .verifyComplete();
    }

    @Test
    void getConfigurationsByType_WithType() {
        // Arrange
        String connectorType = "github";
        ConnectorConfig config1 = new ConnectorConfig();
        ConnectorConfig config2 = new ConnectorConfig();

        when(repository.findByConnectorType(connectorType))
            .thenReturn(Flux.just(config1, config2));

        // Act & Assert
        StepVerifier.create(configService.getConfigurationsByType(connectorType))
            .expectNext(config1)
            .expectNext(config2)
            .verifyComplete();
    }

    @Test
    void getConfigurationsByType_AllTypes() {
        // Arrange
        ConnectorConfig config1 = new ConnectorConfig();
        ConnectorConfig config2 = new ConnectorConfig();

        when(repository.findAll()).thenReturn(Flux.just(config1, config2));

        // Act & Assert
        StepVerifier.create(configService.getConfigurationsByType(null))
            .expectNext(config1)
            .expectNext(config2)
            .verifyComplete();
    }

    @Test
    void getEnabledConfigurations() {
        // Arrange
        ConnectorConfig enabledConfig1 = new ConnectorConfig();
        enabledConfig1.setEnabled(true);
        ConnectorConfig enabledConfig2 = new ConnectorConfig();
        enabledConfig2.setEnabled(true);

        when(repository.findByEnabled(true))
            .thenReturn(Flux.just(enabledConfig1, enabledConfig2));

        // Act & Assert
        StepVerifier.create(configService.getEnabledConfigurations())
            .expectNext(enabledConfig1)
            .expectNext(enabledConfig2)
            .verifyComplete();
    }

    @Test
    void updateConfiguration_Success() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        JsonNode newConfiguration = objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "updated-org"
            }
            """);

        ConnectorConfig existingConfig = new ConnectorConfig();
        existingConfig.setId(id);
        existingConfig.setConnectorType("github");

        ConnectorConfig updatedConfig = new ConnectorConfig();
        updatedConfig.setId(id);
        updatedConfig.setConnectorType("github");
        updatedConfig.setConfiguration(newConfiguration);
        updatedConfig.setUpdatedAt(LocalDateTime.now());

        when(repository.findById(id)).thenReturn(Mono.just(existingConfig));
        when(repository.save(any(ConnectorConfig.class))).thenReturn(Mono.just(updatedConfig));

        // Act & Assert
        StepVerifier.create(configService.updateConfiguration(id, newConfiguration))
            .expectNext(updatedConfig)
            .verifyComplete();

        verify(repository).save(argThat(config -> 
            config.getConfiguration().equals(newConfiguration) &&
            config.getUpdatedAt() != null
        ));
    }

    @Test
    void updateConfiguration_NotFound() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        JsonNode newConfiguration = objectMapper.readTree("{}");

        when(repository.findById(id)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(configService.updateConfiguration(id, newConfiguration))
            .verifyComplete();

        verify(repository, never()).save(any());
    }

    @Test
    void setEnabled_Success() {
        // Arrange
        UUID id = UUID.randomUUID();
        boolean enabled = false;

        ConnectorConfig existingConfig = new ConnectorConfig();
        existingConfig.setId(id);
        existingConfig.setEnabled(true);

        ConnectorConfig updatedConfig = new ConnectorConfig();
        updatedConfig.setId(id);
        updatedConfig.setEnabled(enabled);
        updatedConfig.setUpdatedAt(LocalDateTime.now());

        when(repository.findById(id)).thenReturn(Mono.just(existingConfig));
        when(repository.save(any(ConnectorConfig.class))).thenReturn(Mono.just(updatedConfig));

        // Act & Assert
        StepVerifier.create(configService.setEnabled(id, enabled))
            .expectNext(updatedConfig)
            .verifyComplete();

        verify(repository).save(argThat(config -> 
            !config.getEnabled() &&
            config.getUpdatedAt() != null
        ));
    }

    @Test
    void deleteConfiguration_Success() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(repository.deleteById(id)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(configService.deleteConfiguration(id))
            .verifyComplete();

        verify(repository).deleteById(id);
    }

    @Test
    void getConnectorTypeStats() {
        // Arrange
        when(repository.getConnectorTypeStats())
            .thenReturn(Flux.just(
                new ConnectorConfigService.ConnectorTypeStats("github", 5L, 3L),
                new ConnectorConfigService.ConnectorTypeStats("jira", 2L, 1L)
            ));

        // Act & Assert
        StepVerifier.create(configService.getConnectorTypeStats())
            .expectNextMatches(stats -> 
                stats.getConnectorType().equals("github") &&
                stats.getTotalCount() == 5L &&
                stats.getEnabledCount() == 3L
            )
            .expectNextMatches(stats -> 
                stats.getConnectorType().equals("jira") &&
                stats.getTotalCount() == 2L &&
                stats.getEnabledCount() == 1L
            )
            .verifyComplete();
    }

    @Test
    void validateConfiguration_ValidJson() throws Exception {
        // Arrange
        JsonNode validConfig = objectMapper.readTree("""
            {
                "base_url": "https://api.github.com",
                "organization": "test-org"
            }
            """);

        // Act & Assert
        StepVerifier.create(configService.validateConfiguration("github", validConfig))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void validateConfiguration_InvalidJson() throws Exception {
        // Arrange
        JsonNode invalidConfig = objectMapper.readTree("{}");

        // Act & Assert
        StepVerifier.create(configService.validateConfiguration("github", invalidConfig))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void findConfigurationsForScheduledSync() {
        // Arrange
        ConnectorConfig scheduledConfig1 = new ConnectorConfig();
        ConnectorConfig scheduledConfig2 = new ConnectorConfig();

        when(repository.findScheduledConfigurations())
            .thenReturn(Flux.just(scheduledConfig1, scheduledConfig2));

        // Act & Assert
        StepVerifier.create(configService.findConfigurationsForScheduledSync())
            .expectNext(scheduledConfig1)
            .expectNext(scheduledConfig2)
            .verifyComplete();
    }

    @Test
    void updateLastSyncTime_Success() {
        // Arrange
        UUID id = UUID.randomUUID();
        LocalDateTime lastSyncTime = LocalDateTime.now();

        ConnectorConfig existingConfig = new ConnectorConfig();
        existingConfig.setId(id);

        ConnectorConfig updatedConfig = new ConnectorConfig();
        updatedConfig.setId(id);
        updatedConfig.setLastSyncAt(lastSyncTime);

        when(repository.findById(id)).thenReturn(Mono.just(existingConfig));
        when(repository.save(any(ConnectorConfig.class))).thenReturn(Mono.just(updatedConfig));

        // Act & Assert
        StepVerifier.create(configService.updateLastSyncTime(id, lastSyncTime))
            .expectNext(updatedConfig)
            .verifyComplete();

        verify(repository).save(argThat(config -> 
            config.getLastSyncAt().equals(lastSyncTime)
        ));
    }
}