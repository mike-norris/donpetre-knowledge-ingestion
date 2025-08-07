package com.openrangelabs.donpetre.ingestion.service;

import com.openrangelabs.donpetre.ingestion.entity.IngestionJob;
import com.openrangelabs.donpetre.ingestion.repository.IngestionJobRepository;
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
class IngestionJobServiceTest {

    @Mock
    private IngestionJobRepository repository;

    private IngestionJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new IngestionJobService(repository);
    }

    @Test
    void createJob_Success() {
        // Arrange
        UUID connectorConfigId = UUID.randomUUID();
        String jobType = "FULL_SYNC";
        
        IngestionJob savedJob = new IngestionJob(connectorConfigId, jobType);
        savedJob.setId(UUID.randomUUID());
        savedJob.setCreatedAt(LocalDateTime.now());
        savedJob.setStatus("PENDING");

        when(repository.save(any(IngestionJob.class))).thenReturn(Mono.just(savedJob));

        // Act & Assert
        StepVerifier.create(jobService.createJob(connectorConfigId, jobType))
            .expectNextMatches(job -> {
                assertThat(job.getId()).isEqualTo(savedJob.getId());
                assertThat(job.getConnectorConfigId()).isEqualTo(connectorConfigId);
                assertThat(job.getJobType()).isEqualTo(jobType);
                assertThat(job.getStatus()).isEqualTo("PENDING");
                return true;
            })
            .verifyComplete();

        verify(repository).save(any(IngestionJob.class));
    }

    @Test
    void saveJob_Success() {
        // Arrange
        IngestionJob job = new IngestionJob();
        job.setId(UUID.randomUUID());
        job.setStatus("COMPLETED");

        when(repository.save(job)).thenReturn(Mono.just(job));

        // Act & Assert
        StepVerifier.create(jobService.saveJob(job))
            .expectNext(job)
            .verifyComplete();

        verify(repository).save(job);
    }

    @Test
    void getJob_Found() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob();
        job.setId(jobId);

        when(repository.findById(jobId)).thenReturn(Mono.just(job));

        // Act & Assert
        StepVerifier.create(jobService.getJob(jobId))
            .expectNext(job)
            .verifyComplete();
    }

    @Test
    void getJob_NotFound() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        when(repository.findById(jobId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(jobService.getJob(jobId))
            .verifyComplete();
    }

    @Test
    void getJobsForConnector() {
        // Arrange
        UUID connectorConfigId = UUID.randomUUID();
        IngestionJob job1 = new IngestionJob();
        IngestionJob job2 = new IngestionJob();
        
        when(repository.findByConnectorConfigIdOrderByCreatedAtDesc(connectorConfigId))
            .thenReturn(Flux.just(job1, job2));

        // Act & Assert
        StepVerifier.create(jobService.getJobsForConnector(connectorConfigId))
            .expectNext(job1)
            .expectNext(job2)
            .verifyComplete();
    }

    @Test
    void getLatestSuccessfulJob() {
        // Arrange
        UUID connectorConfigId = UUID.randomUUID();
        IngestionJob successfulJob = new IngestionJob();
        successfulJob.setStatus("COMPLETED");
        successfulJob.setLastSyncCursor("cursor123");

        when(repository.findFirstByConnectorConfigIdAndStatusOrderByCreatedAtDesc(
            connectorConfigId, "COMPLETED"))
            .thenReturn(Mono.just(successfulJob));

        // Act & Assert
        StepVerifier.create(jobService.getLatestSuccessfulJob(connectorConfigId))
            .expectNext(successfulJob)
            .verifyComplete();
    }

    @Test
    void getLatestSuccessfulJob_NotFound() {
        // Arrange
        UUID connectorConfigId = UUID.randomUUID();
        when(repository.findFirstByConnectorConfigIdAndStatusOrderByCreatedAtDesc(
            connectorConfigId, "COMPLETED"))
            .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(jobService.getLatestSuccessfulJob(connectorConfigId))
            .verifyComplete();
    }

    @Test
    void getRunningJobs() {
        // Arrange
        IngestionJob runningJob1 = new IngestionJob();
        runningJob1.setStatus("RUNNING");
        IngestionJob runningJob2 = new IngestionJob();
        runningJob2.setStatus("RUNNING");

        when(repository.findByStatus("RUNNING"))
            .thenReturn(Flux.just(runningJob1, runningJob2));

        // Act & Assert
        StepVerifier.create(jobService.getRunningJobs())
            .expectNext(runningJob1)
            .expectNext(runningJob2)
            .verifyComplete();
    }

    @Test
    void updateJobStatus_Success() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        String newStatus = "FAILED";
        String errorMessage = "Connection timeout";

        IngestionJob existingJob = new IngestionJob();
        existingJob.setId(jobId);
        existingJob.setStatus("RUNNING");

        IngestionJob updatedJob = new IngestionJob();
        updatedJob.setId(jobId);
        updatedJob.setStatus(newStatus);
        updatedJob.setErrorMessage(errorMessage);

        when(repository.findById(jobId)).thenReturn(Mono.just(existingJob));
        when(repository.save(any(IngestionJob.class))).thenReturn(Mono.just(updatedJob));

        // Act & Assert
        StepVerifier.create(jobService.updateJobStatus(jobId, newStatus, errorMessage))
            .expectNext(updatedJob)
            .verifyComplete();

        verify(repository).save(argThat(job -> 
            job.getStatus().equals(newStatus) && 
            job.getErrorMessage().equals(errorMessage)
        ));
    }

    @Test
    void updateJobStatus_JobNotFound() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        when(repository.findById(jobId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(jobService.updateJobStatus(jobId, "FAILED", "Error"))
            .verifyComplete();

        verify(repository, never()).save(any());
    }

    @Test
    void markJobCompleted_Success() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        int processedItems = 100;
        int failedItems = 5;
        String nextCursor = "cursor456";

        IngestionJob existingJob = new IngestionJob();
        existingJob.setId(jobId);
        existingJob.setStatus("RUNNING");

        IngestionJob completedJob = new IngestionJob();
        completedJob.setId(jobId);
        completedJob.setStatus("COMPLETED");
        completedJob.setProcessedItems(processedItems);
        completedJob.setFailedItems(failedItems);
        completedJob.setLastSyncCursor(nextCursor);
        completedJob.setCompletedAt(LocalDateTime.now());

        when(repository.findById(jobId)).thenReturn(Mono.just(existingJob));
        when(repository.save(any(IngestionJob.class))).thenReturn(Mono.just(completedJob));

        // Act & Assert
        StepVerifier.create(jobService.markJobCompleted(jobId, processedItems, failedItems, nextCursor))
            .expectNext(completedJob)
            .verifyComplete();

        verify(repository).save(argThat(job -> 
            job.getStatus().equals("COMPLETED") && 
            job.getProcessedItems() == processedItems &&
            job.getFailedItems() == failedItems &&
            job.getLastSyncCursor().equals(nextCursor) &&
            job.getCompletedAt() != null
        ));
    }

    @Test
    void markJobFailed_Success() {
        // Arrange
        UUID jobId = UUID.randomUUID();
        String errorMessage = "Database connection failed";

        IngestionJob existingJob = new IngestionJob();
        existingJob.setId(jobId);
        existingJob.setStatus("RUNNING");

        IngestionJob failedJob = new IngestionJob();
        failedJob.setId(jobId);
        failedJob.setStatus("FAILED");
        failedJob.setErrorMessage(errorMessage);
        failedJob.setCompletedAt(LocalDateTime.now());

        when(repository.findById(jobId)).thenReturn(Mono.just(existingJob));
        when(repository.save(any(IngestionJob.class))).thenReturn(Mono.just(failedJob));

        // Act & Assert
        StepVerifier.create(jobService.markJobFailed(jobId, errorMessage))
            .expectNext(failedJob)
            .verifyComplete();

        verify(repository).save(argThat(job -> 
            job.getStatus().equals("FAILED") && 
            job.getErrorMessage().equals(errorMessage) &&
            job.getCompletedAt() != null
        ));
    }

    @Test
    void deleteOldJobs_Success() {
        // Arrange
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        when(repository.deleteByCreatedAtBefore(cutoffDate)).thenReturn(Mono.just(10L));

        // Act & Assert
        StepVerifier.create(jobService.deleteOldJobs(cutoffDate))
            .expectNext(10L)
            .verifyComplete();

        verify(repository).deleteByCreatedAtBefore(cutoffDate);
    }

    @Test
    void getJobStatistics_Success() {
        // Arrange
        UUID connectorConfigId = UUID.randomUUID();
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        when(repository.countByConnectorConfigIdAndStatusAndCreatedAtAfter(
            connectorConfigId, "COMPLETED", since))
            .thenReturn(Mono.just(15L));
        when(repository.countByConnectorConfigIdAndStatusAndCreatedAtAfter(
            connectorConfigId, "FAILED", since))
            .thenReturn(Mono.just(2L));
        when(repository.countByConnectorConfigIdAndStatusAndCreatedAtAfter(
            connectorConfigId, "RUNNING", since))
            .thenReturn(Mono.just(1L));

        // Act & Assert
        StepVerifier.create(jobService.getJobStatistics(connectorConfigId, since))
            .expectNextMatches(stats -> {
                assertThat(stats.get("completed")).isEqualTo(15L);
                assertThat(stats.get("failed")).isEqualTo(2L);
                assertThat(stats.get("running")).isEqualTo(1L);
                return true;
            })
            .verifyComplete();
    }
}