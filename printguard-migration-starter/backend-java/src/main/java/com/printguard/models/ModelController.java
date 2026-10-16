package com.printguard.models;

import com.printguard.auth.SecurityUtils;
import com.printguard.auth.UserEntity;
import com.printguard.storage.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping({"/api/models", "/api/models/"})
public class ModelController {
    private final GeneratedModelRepository repository;
    private final SecurityUtils securityUtils;
    private final StorageService storageService;

    public ModelController(
            GeneratedModelRepository repository,
            SecurityUtils securityUtils,
            StorageService storageService) {
        this.repository = repository;
        this.securityUtils = securityUtils;
        this.storageService = storageService;
    }

    @GetMapping({"", "/"})
    public List<ModelResponse> list() {
        UserEntity user = securityUtils.requireCurrentUser();
        List<GeneratedModelEntity> models;
        if (securityUtils.isAdmin(user)) {
            models = repository.findAllByOrderByCreatedAtDesc();
        } else {
            models = repository.findByUserIdOrderByCreatedAtDesc(user.getId());
        }
        return models.stream()
                .map(ModelResponse::from)
                .toList();
    }

    @GetMapping({"/{id}", "/{id}/"})
    public ModelResponse get(@PathVariable Long id) {
        GeneratedModelEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
        securityUtils.verifyOwnershipOrAdmin(entity.getUser(), "view this model");
        return ModelResponse.from(entity);
    }

    @GetMapping({"/{id}/download", "/{id}/download/"})
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        GeneratedModelEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
        securityUtils.verifyOwnershipOrAdmin(entity.getUser(), "download this model");

        try {
            byte[] data = storageService.loadModelContent(entity);
            String filename = resolveFilename(entity);
            MediaType mediaType = resolveMediaType(filename);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(data);
        } catch (FileNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset not found in storage: " + e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read model file: " + e.getMessage());
        }
    }

    @GetMapping({"/{id}/file", "/{id}/file/"})
    public ResponseEntity<byte[]> streamFile(@PathVariable Long id) {
        GeneratedModelEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
        securityUtils.verifyOwnershipOrAdmin(entity.getUser(), "view this model");

        try {
            byte[] data = storageService.loadModelContent(entity);
            String filename = resolveFilename(entity);
            MediaType mediaType = resolveMediaType(filename);

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(data);
        } catch (FileNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset not found in storage: " + e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read model file: " + e.getMessage());
        }
    }

    @DeleteMapping({"/{id}", "/{id}/"})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        GeneratedModelEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
        securityUtils.verifyOwnershipOrAdmin(entity.getUser(), "delete this model");

        if (storageService.isCloudActive() && entity.getFilePath() != null && !entity.getFilePath().isBlank()) {
            storageService.getSupabaseStorageService().deleteFile(entity.getFilePath());
        }

        repository.delete(entity);
        return ResponseEntity.noContent().build();
    }

    private String resolveFilename(GeneratedModelEntity entity) {
        if (entity.getFilePath() != null && !entity.getFilePath().isBlank()) {
            return Paths.get(entity.getFilePath()).getFileName().toString();
        }
        return "model_" + entity.getId() + ".obj";
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".stl")) {
            return MediaType.parseMediaType("model/stl");
        } else if (lower.endsWith(".obj")) {
            return MediaType.parseMediaType("model/obj");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
