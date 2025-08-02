package com.openrangelabs.donpetre.ingestion.model;

import java.time.LocalDateTime;

/**
 * Context information for synchronization operations
 * Supports both cursor-based and time-based incremental synchronization
 */
public class SyncContext {

    private final String lastSyncCursor;
    private final LocalDateTime lastSyncTime;
    private final SyncType syncType;
    private final boolean fullResync;

    public SyncContext(String lastSyncCursor, LocalDateTime lastSyncTime, SyncType syncType) {
        this.lastSyncCursor = lastSyncCursor;
        this.lastSyncTime = lastSyncTime;
        this.syncType = syncType;
        this.fullResync = syncType == SyncType.FULL;
    }

    public static SyncContext forFullSync() {
        return new SyncContext(null, null, SyncType.FULL);
    }

    // UPDATED: Support both cursor and time-based incremental sync
    public static SyncContext forIncrementalSync(String cursor, LocalDateTime lastSync) {
        return new SyncContext(cursor, lastSync, SyncType.INCREMENTAL);
    }

    // ADDED: Convenience method for time-only incremental sync
    public static SyncContext forIncrementalSync(LocalDateTime lastSync) {
        return new SyncContext(null, lastSync, SyncType.INCREMENTAL);
    }

    // ADDED: Convenience method for cursor-only incremental sync
    public static SyncContext forIncrementalSync(String cursor) {
        return new SyncContext(cursor, null, SyncType.INCREMENTAL);
    }

    public static SyncContext forRealTimeSync() {
        return new SyncContext(null, LocalDateTime.now(), SyncType.REAL_TIME);
    }

    // ADDED: Helper methods for sync strategy determination
    public boolean hasCursor() {
        return lastSyncCursor != null && !lastSyncCursor.trim().isEmpty();
    }

    public boolean hasLastSyncTime() {
        return lastSyncTime != null;
    }

    public boolean isFirstSync() {
        return !hasCursor() && !hasLastSyncTime();
    }

    public boolean shouldUseTimeBasedSync() {
        return hasLastSyncTime() && !hasCursor();
    }

    public boolean shouldUseCursorBasedSync() {
        return hasCursor();
    }

    // Getters
    public String getLastSyncCursor() { return lastSyncCursor; }
    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public SyncType getSyncType() { return syncType; }
    public boolean isFullResync() { return fullResync; }

    @Override
    public String toString() {
        return "SyncContext{" +
                "lastSyncCursor='" + lastSyncCursor + '\'' +
                ", lastSyncTime=" + lastSyncTime +
                ", syncType=" + syncType +
                ", fullResync=" + fullResync +
                ", strategy=" + getSyncStrategy() +
                '}';
    }

    // ADDED: Helper method to determine sync strategy
    private String getSyncStrategy() {
        if (isFullResync()) return "FULL";
        if (shouldUseCursorBasedSync()) return "CURSOR_BASED";
        if (shouldUseTimeBasedSync()) return "TIME_BASED";
        if (isFirstSync()) return "FIRST_SYNC";
        return "UNKNOWN";
    }
}