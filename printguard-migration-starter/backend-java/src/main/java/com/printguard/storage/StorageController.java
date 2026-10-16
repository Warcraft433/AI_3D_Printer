package com.printguard.storage;

import com.printguard.auth.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/storage", "/api/storage/"})
public class StorageController {

    private final StorageService storageService;
    private final SupabaseStorageService supabaseStorageService;
    private final SecurityUtils securityUtils;

    public StorageController(
            StorageService storageService,
            SupabaseStorageService supabaseStorageService,
            SecurityUtils securityUtils) {
        this.storageService = storageService;
        this.supabaseStorageService = supabaseStorageService;
        this.securityUtils = securityUtils;
    }

    @GetMapping({"/status", "/status/"})
    public ResponseEntity<StorageHealthResponse> getStatus() {
        securityUtils.requireCurrentUser();

        boolean cloud = storageService.isCloudActive();
        Boolean priv = supabaseStorageService.isVerifiedPrivate();
        String status = cloud
                ? (Boolean.TRUE.equals(priv) ? "connected_private" : "connected_unverified")
                : "local_fallback";

        return ResponseEntity.ok(new StorageHealthResponse(
                storageService.getStorageType(),
                supabaseStorageService.getBucketName(),
                cloud,
                priv,
                cloud ? supabaseStorageService.getSupabaseUrl() : "none",
                cloud,
                status
        ));
    }
}
