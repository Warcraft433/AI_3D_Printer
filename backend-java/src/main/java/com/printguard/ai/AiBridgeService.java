package com.printguard.ai;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise client bridge communicating with Python FastAPI AI microservice.
 * Hardened with Resilience4j Circuit Breaker, Retry, and Rate Limiter.
 * Configured with 120-second read timeouts for neural 3D diffusion synthesis.
 */
@Service
public class AiBridgeService {
    private static final Logger log = LoggerFactory.getLogger(AiBridgeService.class);

    private final RestClient aiClient;
    private final String aiServiceUrl;

    public AiBridgeService(
            @Value("${printguard.ai-service-url:http://127.0.0.1:8001}") String aiServiceUrl,
            @Value("${printguard.ai-service-api-key:}") String aiApiKey) {
        this.aiServiceUrl = aiServiceUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(120)); // 120s read timeout for 3D diffusion

        RestClient.Builder clientBuilder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(aiServiceUrl);
        if (aiApiKey != null && !aiApiKey.isBlank()) {
            clientBuilder.defaultHeader("X-Internal-Token", aiApiKey.trim());
        }
        this.aiClient = clientBuilder.build();
        log.info("Initialized AiBridgeService with endpoint: {} (120s diffusion timeout)", aiServiceUrl);
    }

    public String getAiServiceUrl() {
        return aiServiceUrl;
    }

    /**
     * Sends frame bytes to FastAPI YOLOv8 defect detection endpoint.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "detectFrameFallback")
    @Retry(name = "aiService", fallbackMethod = "detectFrameFallback")
    @RateLimiter(name = "aiService", fallbackMethod = "detectFrameRateLimiterFallback")
    public Map<String, Object> detectFrame(byte[] frameBytes) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(frameBytes) {
                @Override
                public String getFilename() {
                    return "frame.jpg";
                }
            });

            @SuppressWarnings("unchecked")
            Map<String, Object> response = aiClient.post()
                    .uri("/api/inference/detect-frame")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return response != null ? response : Map.of("detections", List.of());
        } catch (Exception e) {
            return detectFrameFallback(frameBytes, e);
        }
    }

    public Map<String, Object> detectFrameFallback(byte[] frameBytes, Throwable t) {
        log.warn("AI Service detectFrame CircuitBreaker/Retry fallback triggered: {}", t.getMessage());
        return Map.of(
                "detections", List.of(),
                "fallback", true,
                "message", "AI inference unavailable: " + t.getMessage()
        );
    }

    public Map<String, Object> detectFrameRateLimiterFallback(byte[] frameBytes, Throwable t) {
        log.warn("AI Service detectFrame RateLimiter fallback: {}", t.getMessage());
        return Map.of(
                "detections", List.of(),
                "fallback", true,
                "message", "AI inference request throttled by rate limiter"
        );
    }

    /**
     * Calls FastAPI procedural CAD geometry generation endpoint.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "generateGeometryFallback")
    @Retry(name = "aiService", fallbackMethod = "generateGeometryFallback")
    @RateLimiter(name = "aiService", fallbackMethod = "generateGeometryRateLimiterFallback")
    public Map<String, Object> generateGeometry(String prompt, Path dest) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = aiClient.post()
                    .uri("/api/geometry/generate")
                    .body(Map.of(
                            "prompt", prompt,
                            "output_path", dest != null ? dest.toAbsolutePath().toString() : "",
                            "format", "obj"
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return response;
            }
            throw new RuntimeException("FastAPI geometry returned failure: " + response);
        } catch (Exception e) {
            return generateGeometryFallback(prompt, dest, e);
        }
    }

    public Map<String, Object> generateGeometryFallback(String prompt, Path dest, Throwable t) {
        log.warn("AI Service generateGeometry CircuitBreaker/Retry fallback triggered for prompt '{}': {}", prompt, t.getMessage());
        return Map.of(
                "success", false,
                "fallback", true,
                "error", "FastAPI geometry generation unavailable: " + t.getMessage()
        );
    }

    public Map<String, Object> generateGeometryRateLimiterFallback(String prompt, Path dest, Throwable t) {
        log.warn("AI Service generateGeometry RateLimiter fallback: {}", t.getMessage());
        return Map.of(
                "success", false,
                "fallback", true,
                "error", "Geometry generation request throttled by rate limiter"
        );
    }

    /**
     * Generates a 3D model via OpenAI Shap-E neural Text-to-3D diffusion,
     * with automatic Resilience4j circuit breaker fallback to procedural CAD.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "generateTextTo3DFallback")
    @Retry(name = "aiService", fallbackMethod = "generateTextTo3DFallback")
    public Map<String, Object> generateTextTo3D(
            String prompt,
            Path dest,
            String engine,
            Double guidanceScale,
            Integer inferenceSteps,
            String format) {
        try {
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("prompt", prompt != null ? prompt : "");
            reqBody.put("engine", (engine != null && !engine.isBlank()) ? engine : "shap-e");
            reqBody.put("guidance_scale", guidanceScale != null ? guidanceScale : 15.0);
            reqBody.put("num_inference_steps", inferenceSteps != null ? inferenceSteps : 32);
            reqBody.put("format", (format != null && !format.isBlank()) ? format : "obj");
            if (dest != null) {
                reqBody.put("output_path", dest.toAbsolutePath().toString());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = aiClient.post()
                    .uri("/api/generate/text-to-3d")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return response;
            }
            throw new RuntimeException("FastAPI Text-to-3D returned failure: " + response);
        } catch (Exception e) {
            return generateTextTo3DFallback(prompt, dest, engine, guidanceScale, inferenceSteps, format, e);
        }
    }

    public Map<String, Object> generateTextTo3D(String prompt, Path dest) {
        return generateTextTo3D(prompt, dest, "shap-e", 15.0, 32, "obj");
    }

    public Map<String, Object> generateTextTo3DFallback(
            String prompt,
            Path dest,
            String engine,
            Double guidanceScale,
            Integer inferenceSteps,
            String format,
            Throwable t) {
        log.warn("AI Service generateTextTo3D fallback triggered for prompt '{}': {}. Falling back to procedural CAD generator.", prompt, t.getMessage());

        // 1. Attempt FastAPI procedural CAD endpoint
        Map<String, Object> proceduralResult = generateGeometry(prompt, dest);
        if (proceduralResult != null && Boolean.TRUE.equals(proceduralResult.get("success"))) {
            Map<String, Object> fallbackRes = new HashMap<>(proceduralResult);
            fallbackRes.put("fallback", true);
            fallbackRes.put("is_fallback", true);
            fallbackRes.put("fallback_reason", "Shap-E neural diffusion unavailable: " + t.getMessage());
            return fallbackRes;
        }

        // 2. Direct local procedural CAD process execution fallback
        Map<String, Object> localCli = tryExecuteLocalProceduralCli(prompt, dest, format);
        if (Boolean.TRUE.equals(localCli.get("success"))) {
            Map<String, Object> fallbackRes = new HashMap<>(localCli);
            fallbackRes.put("fallback", true);
            fallbackRes.put("is_fallback", true);
            fallbackRes.put("fallback_reason", "Shap-E neural diffusion unavailable: " + t.getMessage());
            return fallbackRes;
        }

        return Map.of(
                "success", false,
                "fallback", true,
                "is_fallback", true,
                "error", "Shap-E neural generation and procedural fallback failed: " + t.getMessage()
        );
    }

    /**
     * Generates a 3D model via OpenAI Shap-E Image-to-3D neural vision diffusion,
     * with automatic Resilience4j circuit breaker fallback to procedural CAD.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "generateImageTo3DFallback")
    @Retry(name = "aiService", fallbackMethod = "generateImageTo3DFallback")
    public Map<String, Object> generateImageTo3D(
            MultipartFile image,
            Path dest,
            String name,
            String engine,
            Double guidanceScale,
            Integer inferenceSteps,
            String format) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            byte[] bytes = (image != null && !image.isEmpty()) ? image.getBytes() : new byte[0];
            String filename = (image != null && image.getOriginalFilename() != null && !image.getOriginalFilename().isBlank())
                    ? image.getOriginalFilename()
                    : "upload.png";

            body.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
            if (name != null) body.add("name", name);
            body.add("engine", (engine != null && !engine.isBlank()) ? engine : "shap-e");
            body.add("guidance_scale", String.valueOf(guidanceScale != null ? guidanceScale : 3.0));
            body.add("num_inference_steps", String.valueOf(inferenceSteps != null ? inferenceSteps : 32));
            body.add("format", (format != null && !format.isBlank()) ? format : "obj");
            if (dest != null) {
                body.add("output_path", dest.toAbsolutePath().toString());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = aiClient.post()
                    .uri("/api/generate/image-to-3d")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                return response;
            }
            throw new RuntimeException("FastAPI Image-to-3D returned failure: " + response);
        } catch (Exception e) {
            return generateImageTo3DFallback(image, dest, name, engine, guidanceScale, inferenceSteps, format, e);
        }
    }

    public Map<String, Object> generateImageTo3D(MultipartFile image, Path dest, String name) {
        return generateImageTo3D(image, dest, name, "shap-e", 3.0, 32, "obj");
    }

    public Map<String, Object> generateImageTo3DFallback(
            MultipartFile image,
            Path dest,
            String name,
            String engine,
            Double guidanceScale,
            Integer inferenceSteps,
            String format,
            Throwable t) {
        log.warn("AI Service generateImageTo3D fallback triggered: {}. Falling back to procedural CAD generator.", t.getMessage());
        String fallbackPrompt = (name != null && !name.isBlank()) ? name : "Procedural Mesh";

        // 1. Attempt FastAPI procedural CAD endpoint
        Map<String, Object> proceduralResult = generateGeometry(fallbackPrompt, dest);
        if (proceduralResult != null && Boolean.TRUE.equals(proceduralResult.get("success"))) {
            Map<String, Object> fallbackRes = new HashMap<>(proceduralResult);
            fallbackRes.put("fallback", true);
            fallbackRes.put("is_fallback", true);
            fallbackRes.put("fallback_reason", "Shap-E Vision unavailable: " + t.getMessage());
            return fallbackRes;
        }

        // 2. Direct local procedural CAD process execution fallback
        Map<String, Object> localCli = tryExecuteLocalProceduralCli(fallbackPrompt, dest, format);
        if (Boolean.TRUE.equals(localCli.get("success"))) {
            Map<String, Object> fallbackRes = new HashMap<>(localCli);
            fallbackRes.put("fallback", true);
            fallbackRes.put("is_fallback", true);
            fallbackRes.put("fallback_reason", "Shap-E Vision unavailable: " + t.getMessage());
            return fallbackRes;
        }

        return Map.of(
                "success", false,
                "fallback", true,
                "is_fallback", true,
                "error", "Shap-E Vision generation and procedural fallback failed: " + t.getMessage()
        );
    }

    /**
     * Executes local procedural CAD generator script as guaranteed fallback when FastAPI is unreachable.
     */
    public Map<String, Object> tryExecuteLocalProceduralCli(String prompt, Path dest, String format) {
        if (dest == null) {
            return Map.of("success", false);
        }
        try {
            Files.createDirectories(dest.getParent());
            File pyExe = null;
            for (String pyPath : List.of(
                    "c:/Projects01/AI_3D_Printer/venv/Scripts/python.exe",
                    "c:/Projects01/AI_3D_Printer/.venv/Scripts/python.exe",
                    "c:/Projects01/AI_3D_Printer/legacy/backend-django/venv/Scripts/python.exe",
                    "python"
            )) {
                File f = new File(pyPath);
                if (f.exists() || "python".equals(pyPath)) {
                    pyExe = f;
                    break;
                }
            }

            File script = null;
            for (String scriptPath : List.of(
                    "c:/Projects01/AI_3D_Printer/ai-service/geometry/chess_generator.py",
                    "ai-service/geometry/chess_generator.py",
                    "c:/Projects01/AI_3D_Printer/legacy/backend-django/geometry/chess_generator.py"
            )) {
                File f = new File(scriptPath);
                if (f.exists()) {
                    script = f;
                    break;
                }
            }

            if (pyExe != null && script != null && script.exists()) {
                String fmt = (format != null && !format.isBlank()) ? format : "obj";
                Process process = new ProcessBuilder(
                        pyExe.getAbsolutePath(),
                        script.getAbsolutePath(),
                        "--prompt", prompt,
                        "--output", dest.toAbsolutePath().toString(),
                        "--format", fmt
                ).redirectErrorStream(true).start();

                boolean done = process.waitFor(30, TimeUnit.SECONDS);
                if (done && process.exitValue() == 0 && Files.exists(dest) && Files.size(dest) > 100) {
                    Map<String, Object> res = new HashMap<>();
                    res.put("success", true);
                    res.put("fallback", true);
                    res.put("is_fallback", true);
                    res.put("piece_name", prompt);
                    res.put("generation_method", "procedural_cad");
                    res.put("metadata_description", "[PROCEDURAL CAD] Generated parametric watertight mesh for '" + prompt + "' via CAD geometry engine.");
                    return res;
                }
            }
        } catch (Exception ex) {
            log.warn("Local CLI procedural execution failed: {}", ex.getMessage());
        }
        return Map.of("success", false);
    }

    /**
     * Probes the AI microservice health.
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "checkHealthFallback")
    public Map<String, Object> checkHealth() {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = aiClient.get()
                .uri("/health")
                .retrieve()
                .body(Map.class);
        return response != null ? response : Map.of("status", "DOWN");
    }

    public Map<String, Object> checkHealthFallback(Throwable t) {
        log.debug("AI microservice health probe fallback: {}", t.getMessage());
        return Map.of("status", "DOWN", "error", t.getMessage());
    }
}
