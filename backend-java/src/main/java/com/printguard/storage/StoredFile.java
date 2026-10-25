package com.printguard.storage;

/**
 * Immutable metadata representing a file persisted in PrintGuard AI storage
 * (either remote private Supabase Storage or local fallback storage).
 */
public record StoredFile(
        String storagePath,
        String bucket,
        String storageType,
        String contentType,
        long sizeBytes,
        String localCachePath
) {
    public boolean isCloudStored() {
        return "supabase".equalsIgnoreCase(storageType);
    }
}
