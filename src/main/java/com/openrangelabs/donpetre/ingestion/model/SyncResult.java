package com.openrangelabs.donpetre.ingestion.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a synchronization operation
 */
public class SyncResult {

    private final String connectorType;
    private final SyncType syncType;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final int processedCount;
    private final int failedCount;
    private final String nextCursor;
    private final List<String> errors;
    private final boolean success;

    private SyncResult(Builder builder) {
        this.connectorType = builder.connectorType;
        this.syncType = builder.syncType;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.processedCount = builder.processedCount;
        this.failedCount = builder.failedCount;
        this.nextCursor = builder.nextCursor;
        this.errors = builder.errors;
        this.success = builder.success;
    }

    public static Builder builder(String connectorType, SyncType syncType) {
        return new Builder(connectorType, syncType);
    }

    // Getters
    public String getConnectorType() { return connectorType; }
    public SyncType getSyncType() { return syncType; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getProcessedCount() { return processedCount; }
    public int getFailedCount() { return failedCount; }
    public String getNextCursor() { return nextCursor; }
    public List<String> getErrors() { return errors; }
    public boolean isSuccess() { return success; }

    public static class Builder {
        private final String connectorType;
        private final SyncType syncType;
        private LocalDateTime startTime = LocalDateTime.now();
        private LocalDateTime endTime;
        private int processedCount = 0;
        private int failedCount = 0;
        private String nextCursor;
        private List<String> errors = new ArrayList<>();
        private boolean success = true;

        private Builder(String connectorType, SyncType syncType) {
            this.connectorType = connectorType;
            this.syncType = syncType;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder processedCount(int processedCount) {
            this.processedCount = processedCount;
            return this;
        }

        public Builder failedCount(int failedCount) {
            this.failedCount = failedCount;
            return this;
        }

        public Builder nextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
            return this;
        }

        public Builder addError(String error) {
            this.errors.add(error);
            this.success = false;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public SyncResult build() {
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }
            return new SyncResult(this);
        }
    }

    @Override
    public String toString() {
        return "SyncResult{" +
                "connectorType='" + connectorType + '\'' +
                ", syncType=" + syncType +
                ", processedCount=" + processedCount +
                ", failedCount=" + failedCount +
                ", success=" + success +
                ", duration=" + (endTime != null ?
                java.time.Duration.between(startTime, endTime) : "ongoing") +
                '}';
    }
}