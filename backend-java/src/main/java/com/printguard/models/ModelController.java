package com.printguard.models;

import com.printguard.ai.AiBridgeService;
import com.printguard.auth.SecurityUtils;
import com.printguard.auth.UserEntity;
import com.printguard.storage.StorageService;
import com.printguard.storage.StoredFile;
import com.printguard.websocket.WebSocketEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "3D Models", description = "Query, inspect, download, and generate 3D CAD models (OBJ/STL) via OpenAI Shap-E and procedural CAD")
@RestController
@RequestMapping({"/api/models", "/api/models/"})
public class ModelController {
    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final GeneratedModelRepository repository;
    private final SecurityUtils securityUtils;
    private final StorageService storageService;
    private final AiBridgeService aiBridgeService;
    private final WebSocketEventPublisher eventPublisher;

    @Value("${printguard.media-dir:c:/Projects01/AI_3D_Printer/media}")
    private String mediaDir;

    public ModelController(
            GeneratedModelRepository repository,
            SecurityUtils securityUtils,
            StorageService storageService,
            AiBridgeService aiBridgeService,
            WebSocketEventPublisher eventPublisher) {
        this.repository = repository;
        this.securityUtils = securityUtils;
        this.storageService = storageService;
        this.aiBridgeService = aiBridgeService;
        this.eventPublisher = eventPublisher;
    }

    public record GenerateModelRequest(
            String prompt,
            String engine,
            Double guidanceScale,
            Integer inferenceSteps,
            Integer steps,
            String format
    ) {}

    @Operation(
            summary = "Generate 3D Model from Text Prompt",
            description = "Synthesizes a 3D model (OBJ/STL) using OpenAI Shap-E neural diffusion or procedural CAD generator"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "3D model successfully generated"),
            @ApiResponse(responseCode = "400", description = "Invalid prompt or generation parameters"),
            @ApiResponse(responseCode = "422", description = "Geometry generation failed")
    })
    @CacheEvict(value = "modelMetadata", allEntries = true)
    @PostMapping({"/generate", "/generate/"})
    public ResponseEntity<ModelResponse> generate(@RequestBody GenerateModelRequest req) {
        if (req == null || req.prompt() == null || req.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt cannot be empty");
        }

        UserEntity user = securityUtils.requireCurrentUser();
        String engine = (req.engine() != null && !req.engine().isBlank()) ? req.engine() : "shap-e";
        int numSteps = req.inferenceSteps() != null ? req.inferenceSteps() : (req.steps() != null ? req.steps() : 32);
        double guidance = req.guidanceScale() != null ? req.guidanceScale() : 15.0;
        String fmt = "stl".equalsIgnoreCase(req.format()) ? "stl" : "obj";

        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(user);
        model.setPrompt(req.prompt().trim());
        model.setSteps(numSteps);
        model.setSourceType("procedural".equalsIgnoreCase(engine) ? "procedural" : "text");
        model.setCeleryTaskId(UUID.randomUUID().toString());

        String ext = "." + fmt;
        String filename = "models/" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dest = Paths.get(mediaDir, filename);

        try {
            Files.createDirectories(dest.getParent());
            long startMs = System.currentTimeMillis();

            Map<String, Object> aiResult = aiBridgeService.generateTextTo3D(
                    req.prompt().trim(),
                    dest,
                    engine,
                    guidance,
                    numSteps,
                    fmt
            );

            double durationSec = Math.max(0.15, (System.currentTimeMillis() - startMs) / 1000.0);
            model.setGenerationTime(durationSec);

            if (aiResult != null && Boolean.TRUE.equals(aiResult.get("success")) && Files.exists(dest) && Files.size(dest) > 100) {
                byte[] modelBytes = Files.readAllBytes(dest);
                String mimeType = "stl".equals(fmt) ? "model/stl" : "model/obj";
                StoredFile stored = storageService.storeModelFile(
                        user.getId(),
                        dest.getFileName().toString(),
                        modelBytes,
                        mimeType
                );
                model.setFilePath(stored.storagePath());
                model.setStatus("ready");
                model.setIsRepaired(true);

                String desc = (String) aiResult.get("metadata_description");
                if (desc == null || desc.isBlank()) {
                    desc = "procedural".equalsIgnoreCase(engine)
                            ? "[PROCEDURAL CAD] Generated parametric 3D mesh via CAD geometry engine."
                            : "[SHAP-E NEURAL] Text-to-3D neural synthesis via OpenAI Shap-E diffusion.";
                }
                if (stored.isCloudStored()) {
                    desc += " [Storage: Supabase " + stored.bucket() + "]";
                }
                model.setErrorDetail(desc);

                GeneratedModelEntity saved = repository.save(model);
                ModelResponse resp = ModelResponse.from(saved);
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), saved.getGenerationTime(), resp.fileUrl(), saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.CREATED).body(resp);
            } else {
                model.setStatus("failed");
                model.setFilePath("");
                String errMsg = (aiResult != null && aiResult.get("error") != null)
                        ? String.valueOf(aiResult.get("error"))
                        : "Text-to-3D generation failed for prompt: '" + req.prompt() + "'";
                model.setErrorDetail(errMsg);
                GeneratedModelEntity saved = repository.save(model);
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), 0.0, "", saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ModelResponse.from(saved));
            }
        } catch (Exception e) {
            log.error("Failed to execute text-to-3d generation: {}", e.getMessage(), e);
            model.setStatus("failed");
            model.setFilePath("");
            model.setErrorDetail("Generation error: " + e.getMessage());
            GeneratedModelEntity saved = repository.save(model);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ModelResponse.from(saved));
        }
    }

    @Operation(
            summary = "Generate 3D Model from Reference Image",
            description = "Converts an uploaded photo or sketch into a 3D model using OpenAI Shap-E Vision or procedural CAD"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "3D model successfully generated from image"),
            @ApiResponse(responseCode = "400", description = "Invalid or empty image file uploaded"),
            @ApiResponse(responseCode = "422", description = "Vision generation failed to produce valid mesh")
    })
    @CacheEvict(value = "modelMetadata", allEntries = true)
    @PostMapping(value = {"/generate-from-image", "/generate-from-image/", "/image-to-3d", "/image-to-3d/"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModelResponse> generateFromImage(
            @Parameter(description = "Reference photo or sketch (PNG, JPG, WEBP)")
            @RequestParam(value = "image", required = false) MultipartFile image,
            @Parameter(description = "Alternative reference photo file parameter")
            @RequestParam(value = "file", required = false) MultipartFile fileParam,
            @Parameter(description = "Optional name or prompt for the model")
            @RequestParam(value = "name", required = false) String name,
            @Parameter(description = "Generation engine: 'shap-e' or 'procedural'")
            @RequestParam(value = "engine", defaultValue = "shap-e") String engine,
            @Parameter(description = "Diffusion guidance scale")
            @RequestParam(value = "guidanceScale", required = false) Double guidanceScale,
            @Parameter(description = "Number of diffusion inference steps")
            @RequestParam(value = "inferenceSteps", required = false) Integer inferenceSteps,
            @Parameter(description = "Alternative steps parameter")
            @RequestParam(value = "steps", required = false) Integer steps,
            @Parameter(description = "Output format: 'obj' or 'stl'")
            @RequestParam(value = "format", defaultValue = "obj") String format) {

        MultipartFile actualImage = (image != null && !image.isEmpty()) ? image : fileParam;
        if (actualImage == null || actualImage.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file required (as 'image' or 'file')");
        }

        UserEntity user = securityUtils.requireCurrentUser();
        String modelName = (name != null && !name.isBlank()) ? name.trim() : ("Image-to-3D: " + actualImage.getOriginalFilename());
        int numSteps = inferenceSteps != null ? inferenceSteps : (steps != null ? steps : 32);
        double guidance = guidanceScale != null ? guidanceScale : 3.0;
        String fmt = "stl".equalsIgnoreCase(format) ? "stl" : "obj";

        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(user);
        model.setPrompt(modelName);
        model.setSourceType("image");
        model.setSteps(numSteps);
        model.setCeleryTaskId(UUID.randomUUID().toString());

        String ext = "." + fmt;
        String filename = "models/" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dest = Paths.get(mediaDir, filename);

        try {
            Files.createDirectories(dest.getParent());
            long startMs = System.currentTimeMillis();

            Map<String, Object> aiResult = aiBridgeService.generateImageTo3D(
                    actualImage,
                    dest,
                    modelName,
                    engine,
                    guidance,
                    numSteps,
                    fmt
            );

            double durationSec = Math.max(0.15, (System.currentTimeMillis() - startMs) / 1000.0);
            model.setGenerationTime(durationSec);

            if (aiResult != null && Boolean.TRUE.equals(aiResult.get("success")) && Files.exists(dest) && Files.size(dest) > 100) {
                byte[] modelBytes = Files.readAllBytes(dest);
                String mimeType = "stl".equals(fmt) ? "model/stl" : "model/obj";
                StoredFile stored = storageService.storeModelFile(
                        user.getId(),
                        dest.getFileName().toString(),
                        modelBytes,
                        mimeType
                );
                model.setFilePath(stored.storagePath());
                model.setStatus("ready");
                model.setIsRepaired(true);

                String desc = (String) aiResult.get("metadata_description");
                if (desc == null || desc.isBlank()) {
                    desc = "[SHAP-E VISION] Image-to-3D neural synthesis via OpenAI Shap-E Vision.";
                }
                if (stored.isCloudStored()) {
                    desc += " [Storage: Supabase " + stored.bucket() + "]";
                }
                model.setErrorDetail(desc);

                GeneratedModelEntity saved = repository.save(model);
                ModelResponse resp = ModelResponse.from(saved);
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), saved.getGenerationTime(), resp.fileUrl(), saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.CREATED).body(resp);
            } else {
                model.setStatus("failed");
                model.setFilePath("");
                String origName = actualImage.getOriginalFilename() != null ? actualImage.getOriginalFilename() : "input.png";
                String errMsg = (aiResult != null && aiResult.get("error") != null)
                        ? String.valueOf(aiResult.get("error"))
                        : "Image-to-3D generation failed for image: " + origName;
                model.setErrorDetail(errMsg);
                GeneratedModelEntity saved = repository.save(model);
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), 0.0, "", saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ModelResponse.from(saved));
            }
        } catch (Exception e) {
            log.error("Failed to execute image-to-3d generation: {}", e.getMessage(), e);
            model.setStatus("failed");
            model.setFilePath("");
            model.setErrorDetail("Image generation error: " + e.getMessage());
            GeneratedModelEntity saved = repository.save(model);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ModelResponse.from(saved));
        }
    }

    @Operation(summary = "List 3D Models", description = "Retrieves all 3D models belonging to the authenticated user")
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

    @Operation(summary = "Get 3D Model by ID", description = "Fetches model metadata, watertight status, and storage path")
    @Cacheable(value = "modelMetadata", key = "#id")
    @GetMapping({"/{id}", "/{id}/"})
    public ModelResponse get(@Parameter(description = "Primary key ID of the 3D model") @PathVariable Long id) {
        GeneratedModelEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found"));
        securityUtils.verifyOwnershipOrAdmin(entity.getUser(), "view this model");
        return ModelResponse.from(entity);
    }

    @GetMapping({"/{id}/download", "/{id}/download/", "/{id}/download.{ext}"})
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            @PathVariable(required = false) String ext
    ) {
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

    @GetMapping({"/{id}/file", "/{id}/file/", "/{id}/file.{ext}"})
    public ResponseEntity<byte[]> streamFile(
            @PathVariable Long id,
            @PathVariable(required = false) String ext
    ) {
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

    @CacheEvict(value = "modelMetadata", key = "#id")
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
