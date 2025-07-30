package com.openrangelabs.donpetre.ingestion.model;

public enum SyncType {
    FULL,        // Complete synchronization of all data
    INCREMENTAL, // Synchronization of only new/changed data
    REAL_TIME    // Real-time updates via webhooks/streaming
}
