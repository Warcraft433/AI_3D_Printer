package com.printguard.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.FileNotFoundException;
import java.util.Map;

/**
 * Low-level client for private Supabase Storage REST API.
 * <p>
 * Enforces:
 * 1. Storage bucket MUST be strictly PRIVATE (public == false).
 * 2. Service role key is kept strictly backend-side and NEVER logged or exposed.
 * 3. Supports OBJ, STL, and G-code file uploads with appropriate MIME types.
 * 4. Downloads via authenticated endpoint only.
 */
@Service
public class SupabaseStorageService {
    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final String supabaseUrl;
    private final String serviceKey;
    private final String bucketName;
    private final RestClient restClient;
    private volatile Boolean verifiedPrivate = null;

    @org.springframework.beans.factory.annotation.Autowired
    public SupabaseStorageService(
            @Value("${printguard.storage.supabase.url:${SUPABASE_URL:https://wlpskpsysnamtmzilpft.supabase.co}}") String supabaseUrl,
            @Value("${printguard.storage.supabase.service-key:${SUPABASE_SERVICE_ROLE_KEY:}}") String serviceKey,
            @Value("${printguard.storage.supabase.bucket:${SUPABASE_STORAGE_BUCKET:printguard-files}}") String bucketName,
            RestClient.Builder restClientBuilder) {
        this.supabaseUrl = (supabaseUrl != null) ? supabaseUrl.replaceAll("/+$", "") : "";
        this.serviceKey = (serviceKey != null) ? serviceKey.trim() : "";
        this.bucketName = (bucketName != null && !bucketName.isBlank()) ? bucketName.trim() : "printguard-files";

        if (this.serviceKey.isBlank()) {
            this.restClient = restClientBuilder.build();
            log.info("Supabase Storage: Service key not configured; operating in local storage fallback mode.");
        } else {
            this.restClient = restClientBuilder
                    .baseUrl(this.supabaseUrl + "/storage/v1")
                    .defaultHeader("apikey", this.serviceKey)
                    .defaultHeader("Authorization", "Bearer " + this.serviceKey)
                    .build();
            log.info("Supabase Storage: Client configured for private bucket '{}' (URL: {})",
                    this.bucketName, this.supabaseUrl);
        }
    }

    public SupabaseStorageService(String supabaseUrl, String serviceKey, String bucketName) {
        this(supabaseUrl, serviceKey, bucketName, RestClient.builder());
    }

    /**
     * @return true if Supabase URL and service role key are populated.
     */
    public boolean isConfigured() {
        return !serviceKey.isBlank() && !supabaseUrl.isBlank();
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    /**
     * Confirms that the target bucket exists and is strictly PRIVATE (public: false).
     *
     * @return true if bucket is verified private.
     * @throws BucketPrivacyException if bucket is configured as public.
     * @throws IllegalStateException  if bucket cannot be accessed or does not exist.
     */
    public boolean verifyBucketPrivacy() {
        if (!isConfigured()) {
            return false;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> bucketMeta = restClient.get()
                    .uri("/bucket/" + bucketName)
                    .retrieve()
                    .body(Map.class);

            if (bucketMeta == null) {
                throw new IllegalStateException("Supabase returned empty response for bucket: " + bucketName);
            }

            Object isPublicObj = bucketMeta.get("public");
            boolean isPublic = Boolean.TRUE.equals(isPublicObj)
                    || "true".equalsIgnoreCase(String.valueOf(isPublicObj));

            if (isPublic) {
                verifiedPrivate = false;
                throw new BucketPrivacyException(
                        "Security violation: Supabase Storage bucket '" + bucketName + "' is configured as PUBLIC. " +
                        "PrintGuard AI strictly enforces PRIVATE buckets to prevent unauthorized access to user CAD and G-code assets."
                );
            }

            verifiedPrivate = true;
            log.info("Supabase Storage bucket '{}' privacy confirmed: strictly PRIVATE (public=false)", bucketName);
            return true;
        } catch (BucketPrivacyException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify privacy of Supabase Storage bucket '{}': {}", bucketName, e.getMessage());
            throw new IllegalStateException("Failed to verify Supabase Storage bucket '" + bucketName + "': " + e.getMessage(), e);
        }
    }

    public Boolean isVerifiedPrivate() {
        return verifiedPrivate;
    }

    /**
     * Upload an object into the private Supabase Storage bucket.
     *
     * @param objectPath  target path within the bucket (e.g. "users/1/models/cube.obj")
     * @param content     raw file bytes
     * @param contentType MIME type (e.g. "model/obj", "model/stl", "text/x-gcode")
     * @return clean object path stored
     */
    public String uploadFile(String objectPath, byte[] content, String contentType) {
        if (!isConfigured()) {
            throw new IllegalStateException("Cannot upload to Supabase: Service key is not configured.");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Cannot upload empty file content to Supabase Storage");
        }

        String cleanPath = sanitizePath(objectPath);
        String mime = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri("/object/" + bucketName + "/" + cleanPath)
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(mime))
                    .body(content)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Securely uploaded {} bytes to private Supabase bucket '{}/{}'",
                        content.length, bucketName, cleanPath);
                return cleanPath;
            } else {
                throw new RuntimeException("Supabase upload returned non-2xx status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Supabase Storage upload failed for '{}/{}': {}", bucketName, cleanPath, e.getMessage());
            throw new RuntimeException("Failed to upload object to Supabase Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Download an object from the private Supabase Storage bucket via the authenticated endpoint.
     *
     * @param objectPath path within the bucket
     * @return raw binary content
     * @throws FileNotFoundException if the object is not found
     */
    public byte[] downloadFile(String objectPath) throws FileNotFoundException {
        if (!isConfigured()) {
            throw new IllegalStateException("Cannot download from Supabase: Service key is not configured.");
        }

        String cleanPath = sanitizePath(objectPath);

        try {
            byte[] data = restClient.get()
                    .uri("/object/authenticated/" + bucketName + "/" + cleanPath)
                    .retrieve()
                    .body(byte[].class);

            if (data == null || data.length == 0) {
                throw new FileNotFoundException("File not found or empty in Supabase Storage: " + cleanPath);
            }
            return data;
        } catch (HttpClientErrorException.NotFound e) {
            throw new FileNotFoundException("File not found in Supabase Storage: " + cleanPath);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                throw new FileNotFoundException("File not found in Supabase Storage: " + cleanPath);
            }
            log.error("Supabase Storage download failed for '{}/{}': {}", bucketName, cleanPath, e.getMessage());
            throw new RuntimeException("Failed to download object from Supabase Storage: " + e.getMessage(), e);
        }
    }

    /**
     * Create a temporary signed download URL for secure client access without exposing service keys.
     *
     * @param objectPath       path within the bucket
     * @param expiresInSeconds validity window in seconds (e.g. 3600)
     * @return absolute signed URL
     */
    public String createSignedUrl(String objectPath, int expiresInSeconds) {
        if (!isConfigured()) {
            throw new IllegalStateException("Cannot create signed URL: Service key is not configured.");
        }

        String cleanPath = sanitizePath(objectPath);
        int expiry = Math.max(60, Math.min(86400, expiresInSeconds));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.post()
                    .uri("/object/sign/" + bucketName + "/" + cleanPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", expiry))
                    .retrieve()
                    .body(Map.class);

            if (resp != null && resp.containsKey("signedURL")) {
                String signed = (String) resp.get("signedURL");
                if (signed.startsWith("http://") || signed.startsWith("https://")) {
                    return signed;
                }
                return supabaseUrl + "/storage/v1" + (signed.startsWith("/") ? "" : "/") + signed;
            }
            throw new RuntimeException("Signed URL missing in Supabase response");
        } catch (Exception e) {
            log.error("Failed to generate signed URL for '{}/{}': {}", bucketName, cleanPath, e.getMessage());
            throw new RuntimeException("Failed to generate signed URL: " + e.getMessage(), e);
        }
    }

    /**
     * Delete an object from the bucket.
     */
    public boolean deleteFile(String objectPath) {
        if (!isConfigured()) {
            return false;
        }
        String cleanPath = sanitizePath(objectPath);
        try {
            restClient.delete()
                    .uri("/object/" + bucketName + "/" + cleanPath)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted object from Supabase Storage: {}/{}", bucketName, cleanPath);
            return true;
        } catch (Exception e) {
            log.warn("Failed to delete object '{}/{}': {}", bucketName, cleanPath, e.getMessage());
            return false;
        }
    }

    private String sanitizePath(String path) {
        if (path == null) return "";
        String p = path.replace("\\", "/").trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.startsWith(bucketName + "/")) {
            p = p.substring(bucketName.length() + 1);
        }
        return p;
    }
}
