package com.openrangelabs.donpetre.ingestion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing an ingestion job execution
 * Tracks the lifecycle and performance of data ingestion operations
 */
@Table("ingestion_jobs")
public class IngestionJob {

    @Id
    private UUID id;

    @Column("connector_config_id")
    private UUID connectorConfigId;

    @Column("job_type")
    private String jobType;

    private String status = JobStatus.PENDING.name();

    @Column("started_at")
    private LocalDateTime startedAt;

    @Column("completed_at")
    private LocalDateTime completedAt;

    @Column("last_sync_cursor")
    private String lastSyncCursor;

    @Column("items_processed")
    private Integer itemsProcessed = 0;

    @Column("items_failed")
    private Integer itemsFailed = 0;

    @Column("error_message")
    private String errorMessage;

    private JsonNode metadata;

    // Constructors
    public IngestionJob() {}

    public IngestionJob(UUID connectorConfigId, String jobType) {
        this.connectorConfigId = connectorConfigId;
        this.jobType = jobType;
    }

    public IngestionJob(UUID connectorConfigId, JobType jobType) {
        this.connectorConfigId = connectorConfigId;
        this.jobType = jobType.name().toLowerCase();
    }

    // Business methods
    public void start() {
        this.status = JobStatus.RUNNING.name();
        this.startedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = JobStatus.COMPLETED.name();
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = JobStatus.FAILED.name();
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public void cancel() {
        this.status = JobStatus.CANCELLED.name();
        this.completedAt = LocalDateTime.now();
    }

    public void incrementProcessed() {
        this.itemsProcessed++;
    }

    public void incrementFailed() {
        this.itemsFailed++;
    }

    public void addProcessed(int count) {
        this.itemsProcessed += count;
    }

    public void addFailed(int count) {
        this.itemsFailed += count;
    }

    public boolean isRunning() {
        return JobStatus.RUNNING.name().equals(status);
    }

    public boolean isCompleted() {
        return JobStatus.COMPLETED.name().equals(status) || JobStatus.FAILED.name().equals(status) || JobStatus.CANCELLED.name().equals(status);
    }

    public boolean isPending() {
        return JobStatus.PENDING.name().equals(status);
    }

    public boolean isFailed() {
        return JobStatus.FAILED.name().equals(status);
    }

    public boolean isSuccessful() {
        return JobStatus.COMPLETED.name().equals(status);
    }

    public Duration getDuration() {
        if (startedAt == null) return Duration.ZERO;
        LocalDateTime endTime = completedAt != null ? completedAt : LocalDateTime.now();
        return Duration.between(startedAt, endTime);
    }

    public long getDurationMinutes() {
        return getDuration().toMinutes();
    }

    public double getSuccessRate() {
        int total = itemsProcessed + itemsFailed;
        return total > 0 ? (double) itemsProcessed / total * 100.0 : 100.0;
    }

    public double getFailureRate() {
        return 100.0 - getSuccessRate();
    }

    public int getTotalItems() {
        return itemsProcessed + itemsFailed;
    }

    public boolean hasErrors() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }

    public JobStatus getJobStatus() {
        try {
            return JobStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return JobStatus.UNKNOWN;
        }
    }

    public JobType getJobTypeEnum() {
        try {
            return JobType.valueOf(jobType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return JobType.UNKNOWN;
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getConnectorConfigId() { return connectorConfigId; }
    public void setConnectorConfigId(UUID connectorConfigId) { this.connectorConfigId = connectorConfigId; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getLastSyncCursor() { return lastSyncCursor; }
    public void setLastSyncCursor(String lastSyncCursor) { this.lastSyncCursor = lastSyncCursor; }

    public Integer getItemsProcessed() { return itemsProcessed; }
    public void setItemsProcessed(Integer itemsProcessed) { this.itemsProcessed = itemsProcessed; }

    public Integer getItemsFailed() { return itemsFailed; }
    public void setItemsFailed(Integer itemsFailed) { this.itemsFailed = itemsFailed; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public JsonNode getMetadata() { return metadata; }
    public void setMetadata(JsonNode metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IngestionJob that = (IngestionJob) o;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }

    @Override
    public String toString() {
        return "IngestionJob{" +
                "id=" + id +
                ", connectorConfigId=" + connectorConfigId +
                ", jobType='" + jobType + '\'' +
                ", status='" + status + '\'' +
                ", itemsProcessed=" + itemsProcessed +
                ", itemsFailed=" + itemsFailed +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", duration=" + (isCompleted() ? getDuration() : "ongoing") +
                ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                '}';
    }

    // Enums for job status and type
    public enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED,
        UNKNOWN
    }

    public enum JobType {
        FULL_SYNC,
        INCREMENTAL,
        REAL_TIME,
        UNKNOWN
    }
}