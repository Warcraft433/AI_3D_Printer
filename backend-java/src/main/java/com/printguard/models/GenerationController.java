package com.printguard.models;

import com.printguard.ai.AiBridgeService;
import com.printguard.auth.SecurityUtils;
import com.printguard.auth.UserEntity;
import com.printguard.storage.StorageService;
import com.printguard.storage.StoredFile;
import com.printguard.websocket.WebSocketEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/generate", "/api/generate/"})
public class GenerationController {
    private static final Logger log = LoggerFactory.getLogger(GenerationController.class);

    private final GeneratedModelRepository modelRepository;
    private final SecurityUtils securityUtils;
    private final WebSocketEventPublisher eventPublisher;
    private final StorageService storageService;
    private final AiBridgeService aiBridgeService;

    @Value("${printguard.media-dir:c:/Projects01/AI_3D_Printer/media}")
    private String mediaDir;

    @Autowired
    public GenerationController(
            GeneratedModelRepository modelRepository,
            SecurityUtils securityUtils,
            WebSocketEventPublisher eventPublisher,
            StorageService storageService,
            AiBridgeService aiBridgeService) {
        this.modelRepository = modelRepository;
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
        this.storageService = storageService;
        this.aiBridgeService = aiBridgeService;
    }

    public GenerationController(
            GeneratedModelRepository modelRepository,
            SecurityUtils securityUtils,
            WebSocketEventPublisher eventPublisher,
            StorageService storageService,
            String aiServiceUrl,
            String aiApiKey) {
        this(modelRepository, securityUtils, eventPublisher, storageService, new AiBridgeService(aiServiceUrl, aiApiKey));
    }

    @PostMapping({"/text", "/text/", "/text-to-3d", "/text-to-3d/"})
    public ResponseEntity<ModelResponse> generateText(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "");
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt cannot be empty");
        }

        UserEntity user = securityUtils.requireCurrentUser();
        int steps = 32;
        if (body.get("steps") instanceof Number n) steps = n.intValue();
        else if (body.get("num_inference_steps") instanceof Number n) steps = n.intValue();

        double guidance = 15.0;
        if (body.get("guidance_scale") instanceof Number n) guidance = n.doubleValue();
        else if (body.get("guidanceScale") instanceof Number n) guidance = n.doubleValue();

        String engine = (String) body.getOrDefault("engine", "shap-e");
        String format = (String) body.getOrDefault("format", "obj");
        String fmt = "stl".equalsIgnoreCase(format) ? "stl" : "obj";

        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(user);
        model.setPrompt(prompt.trim());
        model.setSteps(steps);
        model.setSourceType("procedural".equalsIgnoreCase(engine) ? "procedural" : "text");
        model.setCeleryTaskId(UUID.randomUUID().toString());

        String ext = "." + fmt;
        String filename = "models/" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dest = Paths.get(mediaDir, filename);

        boolean forceDemo = Boolean.TRUE.equals(body.get("force_demo")) || "true".equalsIgnoreCase(String.valueOf(body.get("force_demo")));
        if (forceDemo) {
            try {
                Files.createDirectories(dest.getParent());
                String sampleObj = """
                    # PrintGuard 3D Model Output [DEMO FALLBACK]
                    # Static fallback mesh generated because Shap-E AI synthesis requires dedicated GPU inference
                    v 0.0 0.0 0.0
                    v 20.0 0.0 0.0
                    v 20.0 20.0 0.0
                    v 0.0 20.0 0.0
                    v 0.0 0.0 20.0
                    v 20.0 0.0 20.0
                    v 20.0 20.0 20.0
                    v 0.0 20.0 20.0
                    f 1 2 3 4
                    f 5 8 7 6
                    f 1 5 6 2
                    f 2 6 7 3
                    f 3 7 8 4
                    f 4 8 5 1
                    """;
                byte[] demoBytes = sampleObj.getBytes(StandardCharsets.UTF_8);
                Files.write(dest, demoBytes);

                StoredFile stored = storageService.storeModelFile(
                        user.getId(),
                        dest.getFileName().toString(),
                        demoBytes,
                        "model/obj"
                );
                model.setSourceType("text");
                model.setFilePath(stored.storagePath());
                model.setStatus("ready");
                model.setGenerationTime(0.2);
                model.setIsRepaired(true);

                String desc = "[DEMO FALLBACK] Generated static demo mesh (cube). Python Shap-E 3D generative inference was not executed.";
                if (stored.isCloudStored()) {
                    desc += " [Storage: Supabase " + stored.bucket() + "]";
                }
                model.setErrorDetail(desc);

                GeneratedModelEntity saved = modelRepository.save(model);
                String fileUrl = "/api/models/" + saved.getId() + "/file";
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), saved.getGenerationTime(), fileUrl, saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.CREATED).body(ModelResponse.from(saved));
            } catch (Exception e) {
                log.error("Failed to write demo mesh: {}", e.getMessage(), e);
                model.setStatus("failed");
                model.setFilePath("");
                model.setErrorDetail("Failed to write demo mesh: " + e.getMessage());
                GeneratedModelEntity saved = modelRepository.save(model);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ModelResponse.from(saved));
            }
        }

        try {
            Files.createDirectories(dest.getParent());
            long startMs = System.currentTimeMillis();

            Map<String, Object> aiResult = aiBridgeService.generateTextTo3D(
                    prompt.trim(),
                    dest,
                    engine,
                    guidance,
                    steps,
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
                if (desc.contains("[PROCEDURAL CAD]") || "procedural_cad".equals(aiResult.get("generation_method"))) {
                    model.setSourceType("procedural");
                }
                if (stored.isCloudStored()) {
                    desc += " [Storage: Supabase " + stored.bucket() + "]";
                }
                model.setErrorDetail(desc);

                GeneratedModelEntity saved = modelRepository.save(model);
                String fileUrl = "/api/models/" + saved.getId() + "/file";
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), saved.getGenerationTime(), fileUrl, saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.CREATED).body(ModelResponse.from(saved));
            } else {
                model.setStatus("failed");
                model.setFilePath("");
                String errMsg = (aiResult != null && aiResult.get("error") != null)
                        ? String.valueOf(aiResult.get("error"))
                        : "Text-to-3D generation failed for prompt: '" + prompt + "'";
                model.setErrorDetail(errMsg);
                GeneratedModelEntity saved = modelRepository.save(model);
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), 0.0, "", saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ModelResponse.from(saved));
            }
        } catch (Exception e) {
            log.error("GenerationController Text-to-3D error: {}", e.getMessage(), e);
            model.setStatus("failed");
            model.setFilePath("");
            model.setErrorDetail("Generation error: " + e.getMessage());
            GeneratedModelEntity saved = modelRepository.save(model);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ModelResponse.from(saved));
        }
    }

    @PostMapping(value = {"/image", "/image/", "/image-to-3d", "/image-to-3d/"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModelResponse> generateImage(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "file", required = false) MultipartFile fileParam,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "steps", required = false) Integer steps,
            @RequestParam(value = "num_inference_steps", required = false) Integer numInferenceSteps,
            @RequestParam(value = "guidance_scale", required = false) Double guidanceScale,
            @RequestParam(value = "engine", defaultValue = "shap-e") String engine,
            @RequestParam(value = "format", defaultValue = "obj") String format) {

        MultipartFile actualImage = (image != null && !image.isEmpty()) ? image : fileParam;
        if (actualImage == null || actualImage.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file required (as 'image' or 'file')");
        }

        UserEntity user = securityUtils.requireCurrentUser();
        String modelName = (name != null && !name.isBlank()) ? name.trim() : ("Image-to-3D: " + actualImage.getOriginalFilename());
        int numSteps = numInferenceSteps != null ? numInferenceSteps : (steps != null ? steps : 32);
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

                GeneratedModelEntity saved = modelRepository.save(model);
                String fileUrl = "/api/models/" + saved.getId() + "/file";
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), saved.getGenerationTime(), fileUrl, saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.CREATED).body(ModelResponse.from(saved));
            } else {
                model.setStatus("failed");
                model.setFilePath("");
                String errMsg = (aiResult != null && aiResult.get("error") != null)
                        ? String.valueOf(aiResult.get("error"))
                        : "Image-to-3D generation failed for reference: '" + modelName + "'";
                model.setErrorDetail(errMsg);
                GeneratedModelEntity saved = modelRepository.save(model);
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), 0.0, "", saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ModelResponse.from(saved));
            }
        } catch (Exception e) {
            log.error("GenerationController Image-to-3D error: {}", e.getMessage(), e);
            model.setStatus("failed");
            model.setFilePath("");
            model.setErrorDetail("Generation error: " + e.getMessage());
            GeneratedModelEntity saved = modelRepository.save(model);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ModelResponse.from(saved));
        }
    }

    public record ProceduralResult(boolean success, String pieceName, String metadataDescription) {}

    public ProceduralResult tryGenerateProceduralModel(String prompt, Path dest) {
        try {
            Map<String, Object> resp = aiBridgeService.generateGeometry(prompt, dest);
            if (resp != null && Boolean.TRUE.equals(resp.get("success"))) {
                String pieceName = (String) resp.get("piece_name");
                String desc = (String) resp.get("metadata_description");
                return new ProceduralResult(true, pieceName, desc);
            }
        } catch (Exception ignored) {}
        return new ProceduralResult(false, null, null);
    }

    @GetMapping({"/status", "/status/"})
    public ResponseEntity<Map<String, Object>> getStatus() {
        File cacheDir = new File("c:/Projects01/AI_3D_Printer/shap_e_model_cache");
        boolean cacheReady = cacheDir.isDirectory() && cacheDir.list() != null && cacheDir.list().length > 0;

        boolean aiOnline = false;
        try {
            Map<String, Object> health = aiBridgeService.checkHealth();
            aiOnline = health != null && "UP".equalsIgnoreCase(String.valueOf(health.get("status")));
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
            "shape_available", false,
            "ai_service_online", aiOnline,
            "ai_service_url", aiBridgeService.getAiServiceUrl(),
            "cache_ready", cacheReady,
            "cache_dir", cacheDir.getAbsolutePath(),
            "device", "cpu",
            "fallback_mode", true,
            "storage_mode", storageService.getStorageType(),
            "cloud_storage_active", storageService.isCloudActive(),
            "limitation_note", "AI microservice is configured for YOLO defect detection only. Shap-E 3D model generation is in fallback demo mode."
        ));
    }
}
