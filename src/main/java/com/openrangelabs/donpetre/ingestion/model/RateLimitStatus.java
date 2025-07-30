package com.openrangelabs.donpetre.ingestion.model;

import java.time.LocalDateTime;

/**
 * Rate limit status for API connectors
 */
public class RateLimitStatus {

    private final int limit;
    private final int remaining;
    private final LocalDateTime resetTime;
    private final String rateLimitType;

    public RateLimitStatus(int limit, int remaining, LocalDateTime resetTime, String rateLimitType) {
        this.limit = limit;
        this.remaining = remaining;
        this.resetTime = resetTime;
        this.rateLimitType = rateLimitType;
    }

    public boolean isNearLimit(double threshold) {
        return (double) remaining / limit <= threshold;
    }

    public boolean isExceeded() {
        return remaining <= 0;
    }

    // Getters
    public int getLimit() { return limit; }
    public int getRemaining() { return remaining; }
    public LocalDateTime getResetTime() { return resetTime; }
    public String getRateLimitType() { return rateLimitType; }

    @Override
    public String toString() {
        return "RateLimitStatus{" +
                "limit=" + limit +
                ", remaining=" + remaining +
                ", resetTime=" + resetTime +
                ", rateLimitType='" + rateLimitType + '\'' +
                '}';
    }
}