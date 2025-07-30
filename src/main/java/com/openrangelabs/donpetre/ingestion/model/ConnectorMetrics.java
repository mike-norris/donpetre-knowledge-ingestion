package com.openrangelabs.donpetre.ingestion.model;

import java.time.LocalDateTime;

/**
 * Metrics for connector performance monitoring
 */
public class ConnectorMetrics {

    private final String connectorType;
    private final long totalProcessed;
    private final long totalFailed;
    private final LocalDateTime lastUpdated;

    public ConnectorMetrics(String connectorType, long totalProcessed, long totalFailed, LocalDateTime lastUpdated) {
        this.connectorType = connectorType;
        this.totalProcessed = totalProcessed;
        this.totalFailed = totalFailed;
        this.lastUpdated = lastUpdated;
    }

    public double getFailureRate() {
        long total = totalProcessed + totalFailed;
        return total > 0 ? (double) totalFailed / total : 0.0;
    }

    public double getSuccessRate() {
        return 1.0 - getFailureRate();
    }

    // Getters
    public String getConnectorType() { return connectorType; }
    public long getTotalProcessed() { return totalProcessed; }
    public long getTotalFailed() { return totalFailed; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }

    @Override
    public String toString() {
        return "ConnectorMetrics{" +
                "connectorType='" + connectorType + '\'' +
                ", totalProcessed=" + totalProcessed +
                ", totalFailed=" + totalFailed +
                ", successRate=" + String.format("%.2f%%", getSuccessRate() * 100) +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}