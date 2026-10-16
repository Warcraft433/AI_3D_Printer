package com.printguard.storage;

import com.printguard.jobs.PrintJobEntity;
import com.printguard.models.GeneratedModelEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * High-level storage manager that mediates between private Supabase Storage
 * and local filesystem storage.
 * <p>
 * Guarantees:
 * - If Supabase credentials are configured, uploads OBJ, STL, and G-code directly
 *   to private bucket `printguard-files` with strict privacy verification.
 * - Enforces per-user folder scoping: `users/{userId}/models/...` and `users/{userId}/gcode/...`
 * - Caches local copies when needed so PrusaSlicer can execute natively.
 * - Seamlessly falls back to local storage if cloud storage is unconfigured.
 * - Backwards-compatible with legacy database records referencing local `models/...`.
 */
@Service
public class StorageService {
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final SupabaseStorageService supabaseStorageService;
    private final String mediaDir;

    public StorageService(
            SupabaseStorageService supabaseStorageService,
            @Value("${printguard.media-dir:c:/Projects01/AI_3D_Printer/backend/media}") String mediaDir) {
        this.supabaseStorageService = supabaseStorageService;
        this.mediaDir = mediaDir;
    }

    public boolean isCloudActive() {
        return supabaseStorageService.isConfigured();
    }

    public String getStorageType() {
        return isCloudActive() ? "supabase" : "local";
    }

    public String getMediaDir() {
        return mediaDir;
    }

    public SupabaseStorageService getSupabaseStorageService() {
        return supabaseStorageService;
    }

    /**
     * Store a 3D model (OBJ or STL) for a specific user.
     *
     * @param userId      authenticated user ID
     * @param filename    target filename (e.g. "8d91b6cf.obj")
     * @param content     file content bytes
     * @param contentType MIME type (e.g. "model/obj" or "model/stl")
     * @return StoredFile metadata
     */
    public StoredFile storeModelFile(Long userId, String filename, byte[] content, String contentType) throws IOException {
        String safeName = sanitizeFilename(filename);
        String mime = (contentType != null && !contentType.isBlank())
                ? contentType
                : (safeName.toLowerCase().endsWith(".stl") ? "model/stl" : "model/obj");

        // Always ensure local cache copy exists for slicing / fast access
        String localRelPath = "users/" + userId + "/models/" + safeName;
        Path localPath = Paths.get(mediaDir, localRelPath);
        Files.createDirectories(localPath.getParent());
        Files.write(localPath, content);

        if (isCloudActive()) {
            // Verify private bucket posture
            if (supabaseStorageService.isVerifiedPrivate() == null) {
                supabaseStorageService.verifyBucketPrivacy();
            }

            String cloudObjectPath = "users/" + userId + "/models/" + safeName;
            String uploadedPath = supabaseStorageService.uploadFile(cloudObjectPath, content, mime);
            return new StoredFile(
                    uploadedPath,
                    supabaseStorageService.getBucketName(),
                    "supabase",
                    mime,
                    content.length,
                    localPath.toString()
            );
        } else {
            // Local storage fallback
            return new StoredFile(
                    localRelPath,
                    "local",
                    "local",
                    mime,
                    content.length,
                    localPath.toString()
            );
        }
    }

    /**
     * Store sliced G-code file for a specific user and print job.
     *
     * @param userId   authenticated user ID
     * @param jobId    associated print job ID
     * @param filename target filename (e.g. "job_1_92fa7b.gcode")
     * @param content  G-code content bytes
     * @return StoredFile metadata
     */
    public StoredFile storeGCodeFile(Long userId, Long jobId, String filename, byte[] content) throws IOException {
        String safeName = sanitizeFilename(filename);
        String mime = "text/x-gcode";

        String localRelPath = "users/" + userId + "/gcode/" + safeName;
        Path localPath = Paths.get(mediaDir, localRelPath);
        Files.createDirectories(localPath.getParent());
        Files.write(localPath, content);

        if (isCloudActive()) {
            if (supabaseStorageService.isVerifiedPrivate() == null) {
                supabaseStorageService.verifyBucketPrivacy();
            }

            String cloudObjectPath = "users/" + userId + "/gcode/" + safeName;
            String uploadedPath = supabaseStorageService.uploadFile(cloudObjectPath, content, mime);
            return new StoredFile(
                    uploadedPath,
                    supabaseStorageService.getBucketName(),
                    "supabase",
                    mime,
                    content.length,
                    localPath.toString()
            );
        } else {
            return new StoredFile(
                    localRelPath,
                    "local",
                    "local",
                    mime,
                    content.length,
                    localPath.toString()
            );
        }
    }

    /**
     * Load raw binary content of a 3D model, checking Supabase Storage first then local fallback.
     */
    public byte[] loadModelContent(GeneratedModelEntity model) throws IOException {
        if (model == null || model.getFilePath() == null || model.getFilePath().isBlank()) {
            throw new FileNotFoundException("Model file path is empty");
        }

        String rawPath = model.getFilePath().trim();

        // 1. Try Supabase Storage if configured and path matches cloud pattern
        if (isCloudActive() && (rawPath.startsWith("users/") || rawPath.contains("models/"))) {
            try {
                return supabaseStorageService.downloadFile(rawPath);
            } catch (FileNotFoundException ignored) {
                log.warn("Model '{}' not found in Supabase Storage, checking local cache...", rawPath);
            }
        }

        // 2. Check local media directory
        Path localPath = Paths.get(mediaDir, rawPath);
        if (Files.isRegularFile(localPath)) {
            return Files.readAllBytes(localPath);
        }

        // 3. Check legacy models folder if path was users/{id}/models/{file}
        if (rawPath.contains("/")) {
            String basename = Paths.get(rawPath).getFileName().toString();
            Path fallbackPath = Paths.get(mediaDir, "models", basename);
            if (Files.isRegularFile(fallbackPath)) {
                return Files.readAllBytes(fallbackPath);
            }
        }

        // 4. Last attempt: if cloud is active, try by basename
        if (isCloudActive()) {
            try {
                return supabaseStorageService.downloadFile(rawPath);
            } catch (Exception ignored) {}
        }

        throw new FileNotFoundException("Model file not found on storage: " + rawPath);
    }

    /**
     * Load raw G-code bytes for a print job.
     */
    public byte[] loadGCodeContent(PrintJobEntity job) throws IOException {
        if (job == null || job.getGcodePath() == null || job.getGcodePath().isBlank()) {
            throw new FileNotFoundException("Print job G-code path is empty");
        }

        String rawPath = job.getGcodePath().trim();

        // 1. Try Supabase Storage if configured
        if (isCloudActive()) {
            try {
                return supabaseStorageService.downloadFile(rawPath);
            } catch (FileNotFoundException ignored) {
                log.warn("G-code '{}' not found in Supabase Storage, checking local cache...", rawPath);
            }
        }

        // 2. Check local media directory
        Path localPath = Paths.get(mediaDir, rawPath);
        if (Files.isRegularFile(localPath)) {
            return Files.readAllBytes(localPath);
        }

        // 3. Check legacy gcode folder
        if (rawPath.contains("/")) {
            String basename = Paths.get(rawPath).getFileName().toString();
            Path fallbackPath = Paths.get(mediaDir, "gcode", basename);
            if (Files.isRegularFile(fallbackPath)) {
                return Files.readAllBytes(fallbackPath);
            }
        }

        throw new FileNotFoundException("G-code file not found on storage: " + rawPath);
    }

    /**
     * Ensure a local file path exists on disk for a model so that PrusaSlicer can execute against it.
     */
    public Path ensureLocalModelFile(GeneratedModelEntity model) throws IOException {
        String rawPath = model.getFilePath().trim();
        Path localPath = Paths.get(mediaDir, rawPath);

        if (Files.isRegularFile(localPath) && Files.size(localPath) > 0) {
            return localPath;
        }

        // If not on disk, download from Supabase Storage and write to cache
        byte[] content = loadModelContent(model);
        Files.createDirectories(localPath.getParent());
        Files.write(localPath, content);
        return localPath;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8) + ".obj";
        }
        return Paths.get(filename).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
