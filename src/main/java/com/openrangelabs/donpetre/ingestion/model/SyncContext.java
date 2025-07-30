package com.openrangelabs.donpetre.ingestion.model;

import java.time.LocalDateTime;

/**
 * Context information for synchronization operations
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

    public static SyncContext forIncrementalSync(String cursor, LocalDateTime lastSync) {
        return new SyncContext(cursor, lastSync, SyncType.INCREMENTAL);
    }

    public static SyncContext forRealTimeSync() {
        return new SyncContext(null, LocalDateTime.now(), SyncType.REAL_TIME);
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
                '}';
    }
}