package com.openrangelabs.donpetre.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrangelabs.donpetre.ingestion.connector.DataConnector;
import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.entity.IngestionJob;
import com.openrangelabs.donpetre.ingestion.model.SyncResult;
import com.openrangelabs.donpetre.ingestion.model.SyncType;
import com.openrangelabs.donpetre.ingestion.model.ConnectorMetrics;
import com.openrangelabs.donpetre.ingestion.model.RateLimitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionOrchestrationServiceTest {

    @Mock
    private DataConnector mockConnector;
    
    @Mock
    private IngestionJobService jobService;
    
    @Mock
    private ConnectorConfigService configService;
    
    private IngestionOrchestrationService orchestrationService;
    
    private ConnectorConfig testConfig;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(mockConnector.getConnectorType()).thenReturn("github");
        
        orchestrationService = new IngestionOrchestrationService(
            List.of(mockConnector), 
            jobService, 
            configService
        );
        
        ReflectionTestUtils.setField(orchestrationService, "maxConcurrentJobs", 5);
        
        // Create test configuration
        testConfig = new ConnectorConfig();
        testConfig.setId(UUID.randomUUID());
        testConfig.setConnectorType("github");
        testConfig.setName("test-config");
        testConfig.setEnabled(true);
        
        try {
            JsonNode configNode = objectMapper.readTree("""
                {
                    "polling_interval_minutes": 30,
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
    void triggerFullSync_Success() {
        // Arrange
        SyncResult expectedResult = SyncResult.builder("github", SyncType.FULL)
            .processedCount(10)
            .failedCount(0)
            .build();
            
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(jobService.getRunningJobs()).thenReturn(Flux.empty());
        when(mockConnector.performSync(testConfig)).thenReturn(Mono.just(expectedResult));

        // Act & Assert
        StepVerifier.create(orchestrationService.triggerFullSync("github", "test-config"))
            .expectNext(expectedResult)
            .verifyComplete();

        verify(mockConnector).performSync(testConfig);
    }

    @Test
    void triggerFullSync_ConfigurationNotFound() {
        // Arrange
        when(configService.getConfiguration("github", "nonexistent"))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(orchestrationService.triggerFullSync("github", "nonexistent"))
            .expectErrorMatches(throwable -> 
                throwable instanceof IllegalArgumentException &&
                throwable.getMessage().contains("Configuration not found"))
            .verify();
    }

    @Test
    void triggerFullSync_ConnectorDisabled() {
        // Arrange
        testConfig.setEnabled(false);
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));

        // Act & Assert
        StepVerifier.create(orchestrationService.triggerFullSync("github", "test-config"))
            .expectErrorMatches(throwable -> 
                throwable instanceof IllegalStateException &&
                throwable.getMessage().contains("Connector is disabled"))
            .verify();
    }

    @Test
    void triggerFullSync_ConnectorTypeNotFound() {
        // Act & Assert
        StepVerifier.create(orchestrationService.triggerFullSync("nonexistent", "test-config"))
            .expectErrorMatches(throwable -> 
                throwable instanceof IllegalArgumentException &&
                throwable.getMessage().contains("No connector found for type"))
            .verify();
    }

    @Test
    void triggerFullSync_MaxConcurrentJobsReached() {
        // Arrange
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(jobService.getRunningJobs()).thenReturn(
            Flux.range(1, 6).map(i -> new IngestionJob())
        );

        // Act & Assert
        StepVerifier.create(orchestrationService.triggerFullSync("github", "test-config"))
            .expectErrorMatches(throwable -> 
                throwable instanceof IllegalStateException &&
                throwable.getMessage().contains("Maximum concurrent jobs limit reached"))
            .verify();
    }

    @Test
    void triggerIncrementalSync_Success() {
        // Arrange
        String lastCursor = "2023-01-01T00:00:00";
        SyncResult expectedResult = SyncResult.builder("github", SyncType.INCREMENTAL)
            .processedCount(5)
            .failedCount(0)
            .nextCursor("2023-01-02T00:00:00")
            .build();
            
        IngestionJob lastJob = new IngestionJob();
        lastJob.setLastSyncCursor(lastCursor);
            
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(jobService.getRunningJobs()).thenReturn(Flux.empty());
        when(jobService.getLatestSuccessfulJob(testConfig.getId()))
            .thenReturn(Mono.just(lastJob));
        when(mockConnector.performIncrementalSync(testConfig, lastCursor))
            .thenReturn(Mono.just(expectedResult));

        // Act & Assert
        StepVerifier.create(orchestrationService.triggerIncrementalSync("github", "test-config"))
            .expectNext(expectedResult)
            .verifyComplete();

        verify(mockConnector).performIncrementalSync(testConfig, lastCursor);
    }

    @Test
    void triggerIncrementalSync_NoLastJob() {
        // Arrange
        SyncResult expectedResult = SyncResult.builder("github", SyncType.INCREMENTAL)
            .processedCount(5)
            .failedCount(0)
            .build();
            
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(jobService.getRunningJobs()).thenReturn(Flux.empty());
        when(jobService.getLatestSuccessfulJob(testConfig.getId()))
            .thenReturn(Mono.empty());
        when(mockConnector.performIncrementalSync(testConfig, null))
            .thenReturn(Mono.just(expectedResult));

        // Act & Assert
        StepVerifier.create(orchestrationService.triggerIncrementalSync("github", "test-config"))
            .expectNext(expectedResult)
            .verifyComplete();

        verify(mockConnector).performIncrementalSync(testConfig, null);
    }

    @Test
    void testConnection_Success() {
        // Arrange
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(mockConnector.testConnection(testConfig)).thenReturn(Mono.just(true));

        // Act & Assert
        StepVerifier.create(orchestrationService.testConnection("github", "test-config"))
            .expectNext(true)
            .verifyComplete();

        verify(mockConnector).testConnection(testConfig);
    }

    @Test
    void testConnection_Failed() {
        // Arrange
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(mockConnector.testConnection(testConfig)).thenReturn(Mono.just(false));

        // Act & Assert
        StepVerifier.create(orchestrationService.testConnection("github", "test-config"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldRunScheduledSync_NeverSyncedBefore() {
        // Act & Assert
        StepVerifier.create(orchestrationService.shouldRunScheduledSync(testConfig))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldRunScheduledSync_WithinInterval() {
        // Arrange - manually trigger sync to set last sync time
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(jobService.getRunningJobs()).thenReturn(Flux.empty());
        when(mockConnector.performSync(testConfig))
            .thenReturn(Mono.just(SyncResult.builder("github", SyncType.FULL).build()));

        // Trigger sync first to set last sync time
        StepVerifier.create(orchestrationService.triggerFullSync("github", "test-config"))
            .expectNextCount(1)
            .verifyComplete();

        // Act & Assert - should not run again within interval
        StepVerifier.create(orchestrationService.shouldRunScheduledSync(testConfig))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void getAvailableConnectors() {
        // Act
        Map<String, DataConnector> connectors = orchestrationService.getAvailableConnectors();

        // Assert
        assertThat(connectors).hasSize(1);
        assertThat(connectors).containsKey("github");
        assertThat(connectors.get("github")).isEqualTo(mockConnector);
    }

    @Test
    void getConnectorMetrics_Success() {
        // Arrange
        ConnectorMetrics metrics = new ConnectorMetrics();
        RateLimitStatus rateLimit = new RateLimitStatus();
        List<IngestionJob> recentJobs = List.of(new IngestionJob());

        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(mockConnector.getMetrics(testConfig)).thenReturn(Mono.just(metrics));
        when(mockConnector.getRateLimitStatus(testConfig)).thenReturn(Mono.just(rateLimit));
        when(jobService.getJobsForConnector(testConfig.getId()))
            .thenReturn(Flux.fromIterable(recentJobs));

        // Act & Assert
        StepVerifier.create(orchestrationService.getConnectorMetrics("github", "test-config"))
            .expectNextMatches(result -> {
                Map<String, Object> resultMap = (Map<String, Object>) result;
                return resultMap.containsKey("metrics") &&
                       resultMap.containsKey("rateLimit") &&
                       resultMap.containsKey("recentJobs");
            })
            .verifyComplete();
    }

    @Test
    void scheduleIncrementalSync() {
        // Arrange
        SyncResult expectedResult = SyncResult.builder("github", SyncType.INCREMENTAL)
            .processedCount(3)
            .build();
            
        when(configService.getConfiguration("github", "test-config"))
            .thenReturn(Mono.just(testConfig));
        when(jobService.getRunningJobs()).thenReturn(Flux.empty());
        when(jobService.getLatestSuccessfulJob(testConfig.getId()))
            .thenReturn(Mono.empty());
        when(mockConnector.performIncrementalSync(testConfig, null))
            .thenReturn(Mono.just(expectedResult));

        // Act & Assert
        StepVerifier.create(orchestrationService.scheduleIncrementalSync(testConfig))
            .expectNext(expectedResult)
            .verifyComplete();
    }
}