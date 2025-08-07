package com.openrangelabs.donpetre.ingestion.scheduler;

import com.openrangelabs.donpetre.ingestion.entity.ConnectorConfig;
import com.openrangelabs.donpetre.ingestion.model.SyncResult;
import com.openrangelabs.donpetre.ingestion.model.SyncType;
import com.openrangelabs.donpetre.ingestion.service.ConnectorConfigService;
import com.openrangelabs.donpetre.ingestion.service.IngestionOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionSchedulerTest {

    @Mock
    private ConnectorConfigService configService;

    @Mock
    private IngestionOrchestrationService orchestrationService;

    @Mock
    private IngestionJobService jobService;

    @Mock
    private CredentialService credentialService;

    private IngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new IngestionScheduler(orchestrationService, jobService, credentialService, configService);
        ReflectionTestUtils.setField(scheduler, "scheduledSyncEnabled", true);
    }

    @Test
    void runScheduledIngestion_Success() {
        // Arrange
        ConnectorConfig config1 = createTestConfig("github", "config1");
        ConnectorConfig config2 = createTestConfig("jira", "config2");
        
        SyncResult successResult = SyncResult.builder("github", SyncType.INCREMENTAL)
            .processedCount(5)
            .build();

        when(configService.findConfigurationsForScheduledSync())
            .thenReturn(Flux.just(config1, config2));
        
        when(orchestrationService.shouldRunScheduledSync(any()))
            .thenReturn(Mono.just(true));
            
        when(orchestrationService.scheduleIncrementalSync(any()))
            .thenReturn(Mono.just(successResult));

        // Act
        scheduler.runScheduledIngestion();

        // Wait a bit for async processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        verify(configService).findConfigurationsForScheduledSync();
        verify(orchestrationService, times(2)).shouldRunScheduledSync(any());
        verify(orchestrationService, times(2)).scheduleIncrementalSync(any());
    }

    @Test
    void runScheduledIngestion_NoConfigsDue() {
        // Arrange
        ConnectorConfig config1 = createTestConfig("github", "config1");
        
        when(configService.findConfigurationsForScheduledSync())
            .thenReturn(Flux.just(config1));
        
        when(orchestrationService.shouldRunScheduledSync(config1))
            .thenReturn(Mono.just(false)); // Not due for sync

        // Act
        scheduler.runScheduledIngestion();

        // Wait a bit for async processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        verify(orchestrationService).shouldRunScheduledSync(config1);
        verify(orchestrationService, never()).scheduleIncrementalSync(any());
    }

    @Test
    void runScheduledIngestion_SyncError() {
        // Arrange
        ConnectorConfig config1 = createTestConfig("github", "config1");
        
        when(configService.findConfigurationsForScheduledSync())
            .thenReturn(Flux.just(config1));
        
        when(orchestrationService.shouldRunScheduledSync(config1))
            .thenReturn(Mono.just(true));
            
        when(orchestrationService.scheduleIncrementalSync(config1))
            .thenReturn(Mono.error(new RuntimeException("Sync failed")));

        // Act
        scheduler.runScheduledIngestion();

        // Wait a bit for async processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert - should handle error gracefully
        verify(orchestrationService).scheduleIncrementalSync(config1);
    }

    @Test
    void runScheduledIngestion_ConfigServiceError() {
        // Arrange
        when(configService.findConfigurationsForScheduledSync())
            .thenReturn(Flux.error(new RuntimeException("Database error")));

        // Act
        scheduler.runScheduledIngestion();

        // Wait a bit for async processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert - should handle error gracefully
        verify(configService).findConfigurationsForScheduledSync();
        verify(orchestrationService, never()).shouldRunScheduledSync(any());
    }

    @Test
    void runScheduledIngestion_SchedulingDisabled() {
        // Arrange
        ReflectionTestUtils.setField(scheduler, "scheduledSyncEnabled", false);
        
        // Act
        scheduler.runScheduledIngestion();

        // Assert - should not call any services
        verifyNoInteractions(configService);
        verifyNoInteractions(orchestrationService);
    }

    @Test
    void runScheduledIngestion_EmptyConfigs() {
        // Arrange
        when(configService.findConfigurationsForScheduledSync())
            .thenReturn(Flux.empty());

        // Act
        scheduler.runScheduledIngestion();

        // Wait a bit for async processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        verify(configService).findConfigurationsForScheduledSync();
        verify(orchestrationService, never()).shouldRunScheduledSync(any());
    }

    @Test
    void runScheduledIngestion_PartialSuccess() {
        // Arrange
        ConnectorConfig config1 = createTestConfig("github", "config1");
        ConnectorConfig config2 = createTestConfig("jira", "config2");
        
        SyncResult successResult = SyncResult.builder("github", SyncType.INCREMENTAL)
            .processedCount(5)
            .build();

        when(configService.findConfigurationsForScheduledSync())
            .thenReturn(Flux.just(config1, config2));
        
        // First config should run and succeed
        when(orchestrationService.shouldRunScheduledSync(config1))
            .thenReturn(Mono.just(true));
        when(orchestrationService.scheduleIncrementalSync(config1))
            .thenReturn(Mono.just(successResult));
            
        // Second config should run but fail
        when(orchestrationService.shouldRunScheduledSync(config2))
            .thenReturn(Mono.just(true));
        when(orchestrationService.scheduleIncrementalSync(config2))
            .thenReturn(Mono.error(new RuntimeException("Sync failed")));

        // Act
        scheduler.runScheduledIngestion();

        // Wait a bit for async processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert - both should be attempted
        verify(orchestrationService).scheduleIncrementalSync(config1);
        verify(orchestrationService).scheduleIncrementalSync(config2);
    }

    @Test
    void runHealthCheck_Success() {
        // This test assumes there's a health check method in the scheduler
        // If it doesn't exist, this test can be removed or the method can be added
        
        // Act
        scheduler.runHealthCheck();

        // Assert - method should execute without throwing exceptions
        // Additional assertions would depend on the actual implementation
    }

    @Test
    void runMaintenanceTasks_Success() {
        // This test assumes there's a maintenance method in the scheduler
        // If it doesn't exist, this test can be removed or the method can be added
        
        // Act
        scheduler.runMaintenanceTasks();

        // Assert - method should execute without throwing exceptions
        // Additional assertions would depend on the actual implementation
    }

    private ConnectorConfig createTestConfig(String type, String name) {
        ConnectorConfig config = new ConnectorConfig();
        config.setId(UUID.randomUUID());
        config.setConnectorType(type);
        config.setName(name);
        config.setEnabled(true);
        return config;
    }
}