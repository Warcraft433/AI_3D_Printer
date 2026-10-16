package com.printguard.models;

import com.printguard.auth.SecurityUtils;
import com.printguard.auth.UserEntity;
import com.printguard.storage.StorageService;
import com.printguard.storage.StoredFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
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
    private final GeneratedModelRepository modelRepository;
    private final SecurityUtils securityUtils;
    private final com.printguard.websocket.WebSocketEventPublisher eventPublisher;
    private final StorageService storageService;
    private final RestClient aiClient;
    private final String aiServiceUrl;

    @Value("${printguard.media-dir:c:/Projects01/AI_3D_Printer/backend/media}")
    private String mediaDir;

    public GenerationController(
            GeneratedModelRepository modelRepository,
            SecurityUtils securityUtils,
            com.printguard.websocket.WebSocketEventPublisher eventPublisher,
            StorageService storageService,
            @Value("${printguard.ai-service-url:http://127.0.0.1:8001}") String aiServiceUrl,
            @Value("${printguard.ai-service-api-key:}") String aiApiKey) {
        this.modelRepository = modelRepository;
        this.securityUtils = securityUtils;
        this.eventPublisher = eventPublisher;
        this.storageService = storageService;
        this.aiServiceUrl = aiServiceUrl;
        RestClient.Builder clientBuilder = RestClient.builder().baseUrl(aiServiceUrl);
        if (aiApiKey != null && !aiApiKey.isBlank()) {
            clientBuilder.defaultHeader("X-Internal-Token", aiApiKey.trim());
        }
        this.aiClient = clientBuilder.build();
    }

    @PostMapping({"/text", "/text/"})
    public ResponseEntity<ModelResponse> generateText(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "");
        Integer steps = (Integer) body.getOrDefault("steps", 32);

        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt cannot be empty");
        }

        UserEntity user = securityUtils.requireCurrentUser();

        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(user);
        model.setPrompt(prompt);
        model.setSteps(steps != null ? steps : 32);
        model.setCeleryTaskId(UUID.randomUUID().toString());

        String filename = "models/" + UUID.randomUUID().toString().substring(0, 8) + ".obj";
        Path dest = Paths.get(mediaDir, filename);

        boolean isExplicitDemo = Boolean.TRUE.equals(body.get("force_demo"))
                || prompt.toLowerCase().contains("[demo]")
                || prompt.toLowerCase().startsWith("demo ");

        if (!isExplicitDemo) {
            long startMs = System.currentTimeMillis();
            ProceduralResult result = tryGenerateProceduralModel(prompt, dest);
            if (result.success() && Files.exists(dest) && dest.toFile().length() > 500) {
                double durationSec = (System.currentTimeMillis() - startMs) / 1000.0;
                model.setSourceType("procedural");
                model.setStatus("ready");
                model.setGenerationTime(Math.max(0.15, durationSec));
                model.setIsRepaired(true);

                try {
                    byte[] modelBytes = Files.readAllBytes(dest);
                    StoredFile stored = storageService.storeModelFile(
                            user.getId(),
                            dest.getFileName().toString(),
                            modelBytes,
                            "model/obj"
                    );
                    model.setFilePath(stored.storagePath());

                    String desc = (result.metadataDescription() != null && !result.metadataDescription().isBlank())
                            ? result.metadataDescription()
                            : "[PROCEDURAL CAD] Generated parametric 3D mesh via CAD geometry engine.";
                    if (stored.isCloudStored()) {
                        desc += " [Storage: Supabase " + stored.bucket() + "]";
                    }
                    model.setErrorDetail(desc);
                } catch (Exception e) {
                    model.setFilePath(filename);
                    model.setErrorDetail((result.metadataDescription() != null ? result.metadataDescription() : "") +
                            " (Storage cache notice: " + e.getMessage() + ")");
                }

                GeneratedModelEntity saved = modelRepository.save(model);
                String fileUrl = "/api/models/" + saved.getId() + "/file";
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), saved.getGenerationTime(), fileUrl, saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.CREATED).body(ModelResponse.from(saved));
            } else {
                model.setStatus("failed");
                model.setFilePath("");
                model.setGenerationTime(0.0);
                model.setErrorDetail("Procedural CAD generation failed for prompt: '" + prompt + "'. The geometry generator could not build a valid manifold mesh.");
                GeneratedModelEntity saved = modelRepository.save(model);
                eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), 0.0, "", saved.getErrorDetail());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ModelResponse.from(saved));
            }
        }

        // Demo fallback (only when explicitly requested via force_demo or [demo])
        try {
            Files.createDirectories(dest.getParent());
            String sampleObj = """
                # PrintGuard 3D Model Output [DEMO FALLBACK]
                # Static fallback mesh generated because Shap-E AI synthesis requires dedicated GPU inference
                v -1.0 -1.0  1.0
                v  1.0 -1.0  1.0
                v -1.0  1.0  1.0
                v  1.0  1.0  1.0
                v -1.0  1.0 -1.0
                v  1.0  1.0 -1.0
                v -1.0 -1.0 -1.0
                v  1.0 -1.0 -1.0
                f 1 2 4 3
                f 3 4 6 5
                f 5 6 8 7
                f 7 8 2 1
                f 2 8 6 4
                f 7 1 3 5
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
        } catch (Exception e) {
            model.setStatus("failed");
            model.setFilePath("");
            model.setGenerationTime(0.0);
            model.setErrorDetail("Failed to write demo mesh: " + e.getMessage());
        }

        GeneratedModelEntity saved = modelRepository.save(model);
        String fileUrl = saved.getFilePath().isBlank() ? "" : "/api/models/" + saved.getId() + "/file";
        eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), saved.getGenerationTime(), fileUrl, saved.getErrorDetail());
        return ResponseEntity.status(HttpStatus.CREATED).body(ModelResponse.from(saved));
    }

    public record ProceduralResult(boolean success, String pieceName, String metadataDescription) {}

    private ProceduralResult tryGenerateProceduralModel(String prompt, Path dest) {
        try {
            Files.createDirectories(dest.getParent());

            // 1. Try FastAPI microservice
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = aiClient.post()
                        .uri("/api/geometry/generate")
                        .body(Map.of(
                                "prompt", prompt,
                                "output_path", dest.toAbsolutePath().toString(),
                                "format", "obj"
                        ))
                        .retrieve()
                        .body(Map.class);
                if (resp != null && Boolean.TRUE.equals(resp.get("success")) && Files.exists(dest)) {
                    String pieceName = (String) resp.get("piece_name");
                    String desc = (String) resp.get("metadata_description");
                    return new ProceduralResult(true, pieceName, desc);
                }
            } catch (Exception httpEx) {
                // Microservice unreachable or endpoint failed, fall through to CLI
            }

            // 2. Direct Python process invocation as fallback
            File pyExe = new File("c:/Projects01/AI_3D_Printer/backend/venv/Scripts/python.exe");
            File script = new File("c:/Projects01/AI_3D_Printer/backend/geometry/chess_generator.py");
            if (pyExe.exists() && script.exists()) {
                Process process = new ProcessBuilder(
                        pyExe.getAbsolutePath(),
                        script.getAbsolutePath(),
                        "--prompt", prompt,
                        "--output", dest.toAbsolutePath().toString(),
                        "--format", "obj"
                ).redirectErrorStream(true).start();
                boolean done = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                if (done && process.exitValue() == 0 && Files.exists(dest)) {
                    return new ProceduralResult(true, "Procedural CAD Model",
                            "[PROCEDURAL CAD] Generated parametric watertight mesh for '" + prompt + "' via CAD geometry engine.");
                }
            }
        } catch (Exception ignored) {}
        return new ProceduralResult(false, null, null);
    }

    @PostMapping({"/image", "/image/"})
    public ResponseEntity<ModelResponse> generateImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "steps", defaultValue = "32") Integer steps) {

        if (image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file required");
        }

        UserEntity user = securityUtils.requireCurrentUser();

        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(user);
        model.setPrompt("Image-to-3D: " + image.getOriginalFilename());
        model.setSourceType("image");
        model.setSteps(steps);
        model.setCeleryTaskId(UUID.randomUUID().toString());

        String filename = "models/" + UUID.randomUUID().toString().substring(0, 8) + ".obj";
        Path dest = Paths.get(mediaDir, filename);

        try {
            Files.createDirectories(dest.getParent());
            String sampleObj = """
                # PrintGuard 3D Model Output [DEMO FALLBACK]
                # Static fallback mesh generated because Shap-E AI synthesis requires dedicated GPU inference
                v 0.0 1.0 0.0
                v -1.0 -1.0 1.0
                v 1.0 -1.0 1.0
                v 0.0 -1.0 -1.0
                f 1 2 3
                f 1 3 4
                f 1 4 2
                f 2 4 3
                """;
            byte[] demoBytes = sampleObj.getBytes(StandardCharsets.UTF_8);
            Files.write(dest, demoBytes);

            StoredFile stored = storageService.storeModelFile(
                    user.getId(),
                    dest.getFileName().toString(),
                    demoBytes,
                    "model/obj"
            );
            model.setFilePath(stored.storagePath());
            model.setStatus("ready");
            model.setGenerationTime(0.2);
            model.setIsRepaired(true);

            String desc = "[DEMO FALLBACK] Generated static demo mesh (tetrahedron). Python Shap-E 3D generative inference was not executed.";
            if (stored.isCloudStored()) {
                desc += " [Storage: Supabase " + stored.bucket() + "]";
            }
            model.setErrorDetail(desc);
        } catch (Exception e) {
            model.setStatus("failed");
            model.setFilePath("");
            model.setGenerationTime(0.0);
            model.setErrorDetail("Failed to write demo mesh: " + e.getMessage());
        }

        GeneratedModelEntity saved = modelRepository.save(model);
        String fileUrl = saved.getFilePath().isBlank() ? "" : "/api/models/" + saved.getId() + "/file";
        eventPublisher.publishModelStatus(saved.getId(), saved.getStatus(), saved.getGenerationTime(), fileUrl, saved.getErrorDetail());
        return ResponseEntity.status(HttpStatus.CREATED).body(ModelResponse.from(saved));
    }

    @GetMapping({"/status", "/status/"})
    public ResponseEntity<Map<String, Object>> getStatus() {
        File cacheDir = new File("c:/Projects01/AI_3D_Printer/shap_e_model_cache");
        boolean cacheReady = cacheDir.isDirectory() && cacheDir.list() != null && cacheDir.list().length > 0;

        boolean aiOnline = false;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> health = aiClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            aiOnline = health != null && "UP".equalsIgnoreCase(String.valueOf(health.get("status")));
        } catch (Exception ignored) {}

        return ResponseEntity.ok(Map.of(
            "shape_available", false,
            "ai_service_online", aiOnline,
            "ai_service_url", aiServiceUrl,
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
