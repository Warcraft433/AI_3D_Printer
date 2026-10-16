package com.printguard.storage;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StorageHealthResponse(
        @JsonProperty("storage_type") String storageType,
        String bucket,
        @JsonProperty("is_cloud_active") boolean isCloudActive,
        @JsonProperty("is_private") Boolean isPrivate,
        @JsonProperty("supabase_url") String supabaseUrl,
        @JsonProperty("service_key_configured") boolean serviceKeyConfigured,
        String status
) {}
