package com.printguard.slicer;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SlicerService {
    private static final Logger log = LoggerFactory.getLogger(SlicerService.class);

    private final String slicerPath;
    private final String printerProfile;
    private final int timeoutSeconds;

    @Autowired
    public SlicerService(
            @Value("${printguard.slicer-path:${SLICER_PATH:${PRUSA_SLICER_PATH:}}}") String configuredPath,
            @Value("${printguard.slicer-profile:Original Prusa i3 MK3S & MK3S+}") String printerProfile,
            @Value("${printguard.slicer-timeout-seconds:90}") int timeoutSeconds) {
        this.slicerPath = resolveSlicerPath(configuredPath);
        this.printerProfile = printerProfile;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 90;
        log.info("Initialized SlicerService with binary: '{}', profile: '{}', timeout: {}s (available: {})",
                this.slicerPath, this.printerProfile, this.timeoutSeconds, isAvailable());
    }

    public SlicerService(String slicerPath, String printerProfile) {
        this(slicerPath, printerProfile, 90);
    }

    public static String resolveSlicerPath(String configuredPath) {
        // 1. Explicit configured path
        if (configuredPath != null && !configuredPath.isBlank()) {
            File f = new File(configuredPath);
            if (f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
            String onPath = findExecutableOnPath(configuredPath);
            if (onPath != null) {
                return onPath;
            }
            return configuredPath;
        }

        // 2. Standard Linux package locations
        for (String candidate : List.of("/usr/bin/prusa-slicer", "/usr/local/bin/prusa-slicer", "/usr/bin/prusa-slicer-console")) {
            File f = new File(candidate);
            if (f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        // 3. Search system PATH for common binary names
        for (String binaryName : List.of("prusa-slicer", "prusa-slicer-console.exe", "prusa-slicer.exe")) {
            String onPath = findExecutableOnPath(binaryName);
            if (onPath != null) {
                return onPath;
            }
        }

        // 4. Windows project-local locations
        List<String> windowsCandidates = List.of(
                "c:/Projects01/AI_3D_Printer/tools/PrusaSlicer/PrusaSlicer-2.9.6/prusa-slicer-console.exe",
                "tools/PrusaSlicer/PrusaSlicer-2.9.6/prusa-slicer-console.exe",
                "tools/PrusaSlicer/prusa-slicer-console.exe"
        );
        for (String candidate : windowsCandidates) {
            File f = new File(candidate);
            if (f.isFile() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }

        return (configuredPath != null && !configuredPath.isBlank()) ? configuredPath : "/usr/bin/prusa-slicer";
    }

    public static String findExecutableOnPath(String executableName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        String[] dirs = pathEnv.split(Pattern.quote(File.pathSeparator));
        for (String dir : dirs) {
            if (dir.isBlank()) continue;
            File file = new File(dir.trim(), executableName);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    public boolean isAvailable() {
        if (slicerPath == null || slicerPath.isBlank()) {
            return false;
        }
        File binary = new File(slicerPath);
        if (binary.isFile() && binary.canExecute()) {
            return true;
        }
        return findExecutableOnPath(slicerPath) != null;
    }

    public String getSlicerPath() {
        return slicerPath;
    }

    public String getPrinterProfile() {
        return printerProfile;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getSlicerVersion() {
        if (!isAvailable()) {
            return "Unavailable";
        }
        try {
            Process process = new ProcessBuilder(slicerPath, "--help")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("PrusaSlicer-")) {
                        return line.trim();
                    }
                }
            }
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to probe slicer version: {}", e.getMessage());
        }
        return "PrusaSlicer (Version unknown)";
    }

    @CircuitBreaker(name = "slicerService", fallbackMethod = "sliceFallback")
    @RateLimiter(name = "slicerService", fallbackMethod = "sliceRateLimitFallback")
    @Retry(name = "slicerService", fallbackMethod = "sliceFallback")
    public SlicingResult slice(Path modelFilePath, Path outputGcodePath, String targetProfile) {
        if (!isAvailable()) {
            return new SlicingResult(
                    false,
                    -1,
                    0L,
                    "",
                    "",
                    "Slicer engine unavailable: No valid slicer binary found at configured path [" + slicerPath + "]",
                    false,
                    null
            );
        }

        if (modelFilePath == null || !Files.isRegularFile(modelFilePath)) {
            return new SlicingResult(
                    false,
                    -1,
                    0L,
                    "",
                    "",
                    "Input 3D model file does not exist or is invalid: " + modelFilePath,
                    false,
                    null
            );
        }

        try {
            if (Files.size(modelFilePath) < 10) {
                return new SlicingResult(
                        false,
                        -1,
                        0L,
                        "",
                        "",
                        "Input 3D model file is empty or corrupted: " + modelFilePath,
                        false,
                        null
                );
            }

            // Reject demo cube OBJ files from slicing (safely read up to 2KB without charset encoding exceptions)
            byte[] header = new byte[Math.min(2048, (int) Files.size(modelFilePath))];
            try (java.io.InputStream is = Files.newInputStream(modelFilePath)) {
                int read = is.read(header);
                if (read > 0) {
                    String sample = new String(header, 0, read, java.nio.charset.StandardCharsets.ISO_8859_1);
                    if (sample.contains("[DEMO FALLBACK]")) {
                        return new SlicingResult(
                                false,
                                -1,
                                0L,
                                "",
                                "",
                                "Demo fallback meshes cannot be sliced into production G-code. Generate a genuine 3D model first.",
                                false,
                                null
                        );
                    }
                }
            }
        } catch (Exception e) {
            return new SlicingResult(
                    false,
                    -1,
                    0L,
                    "",
                    "",
                    "Failed to read model file: " + e.getMessage(),
                    false,
                    null
            );
        }

        try {
            if (outputGcodePath.getParent() != null) {
                Files.createDirectories(outputGcodePath.getParent());
            }

            List<String> command = new ArrayList<>();
            command.add(slicerPath);
            command.add("--export-gcode");
            command.add("--output");
            command.add(outputGcodePath.toAbsolutePath().toString());
            command.add(modelFilePath.toAbsolutePath().toString());

            log.info("Executing slicing command: {}", command);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder outputLog = new StringBuilder();
            StringBuilder warnings = new StringBuilder();

            CompletableFuture<Void> streamReader = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputLog.append(line).append("\n");
                        String trimmed = line.trim();
                        // PrusaSlicer prints progress milestones to stdout in the format: "<percentage> => <stage description>"
                        boolean isProgress = trimmed.matches("^\\d+\\s*=>.*");
                        if (!isProgress && !trimmed.isBlank()) {
                            String lower = trimmed.toLowerCase();
                            if (lower.contains("warning:") || lower.contains("[warning]") || lower.startsWith("warning")
                                    || lower.contains("error:") || lower.startsWith("error")
                                    || (lower.contains("alert") && !lower.contains("=>"))) {
                                warnings.append(trimmed).append("; ");
                            }
                        }
                    }
                } catch (Exception ignored) {}
            });

            boolean finished = process.waitFor(this.timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                streamReader.cancel(true);
                return new SlicingResult(
                        false,
                        -1,
                        0L,
                        "",
                        warnings.toString(),
                        "Slicing process timed out after " + this.timeoutSeconds + " seconds",
                        false,
                        null
                );
            }

            try {
                streamReader.get(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {}

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return new SlicingResult(
                        false,
                        exitCode,
                        0L,
                        "",
                        warnings.toString(),
                        "Slicing failed with exit code " + exitCode + ": " + outputLog,
                        false,
                        null
                );
            }

            if (!Files.isRegularFile(outputGcodePath) || Files.size(outputGcodePath) < 1024) {
                return new SlicingResult(
                        false,
                        exitCode,
                        Files.exists(outputGcodePath) ? Files.size(outputGcodePath) : 0L,
                        "",
                        warnings.toString(),
                        "Slicer generated an incomplete or empty G-code file (< 1 KB)",
                        false,
                        null
                );
            }

            long gcodeSize = Files.size(outputGcodePath);

            // Verify real G-code commands
            boolean hasExtrusion = false;
            boolean hasHeader = false;
            Integer estimatedSeconds = null;

            try (BufferedReader gReader = Files.newBufferedReader(outputGcodePath)) {
                String line;
                int linesRead = 0;
                Pattern timePattern = Pattern.compile("estimated printing time.*?=\\s*(\\d+h)?\\s*(\\d+m)?\\s*(\\d+s)?", Pattern.CASE_INSENSITIVE);
                while ((line = gReader.readLine()) != null && linesRead < 500) {
                    linesRead++;
                    if (line.contains("PrusaSlicer") || line.contains("G-code")) {
                        hasHeader = true;
                    }
                    if (line.startsWith("G1 ") || line.startsWith("G0 ") || line.contains("E")) {
                        hasExtrusion = true;
                    }
                    Matcher m = timePattern.matcher(line);
                    if (m.find()) {
                        int sec = 0;
                        if (m.group(1) != null) sec += Integer.parseInt(m.group(1).replace("h", "")) * 3600;
                        if (m.group(2) != null) sec += Integer.parseInt(m.group(2).replace("m", "")) * 60;
                        if (m.group(3) != null) sec += Integer.parseInt(m.group(3).replace("s", ""));
                        if (sec > 0) estimatedSeconds = sec;
                    }
                }
            }

            if (!hasExtrusion && !hasHeader) {
                return new SlicingResult(
                        false,
                        exitCode,
                        gcodeSize,
                        "",
                        warnings.toString(),
                        "Generated file does not contain valid 3D print G-code instructions",
                        false,
                        null
                );
            }

            if (estimatedSeconds == null) {
                estimatedSeconds = (int) Math.max(300, Math.min(7200, gcodeSize / 150));
            }

            log.info("Slicing succeeded: output={}, size={} bytes, estimatedTime={}s", outputGcodePath, gcodeSize, estimatedSeconds);

            return new SlicingResult(
                    true,
                    0,
                    gcodeSize,
                    outputGcodePath.toString(),
                    warnings.toString().trim(),
                    "",
                    true,
                    estimatedSeconds
            );

        } catch (Exception e) {
            log.error("Slicing exception: {}", e.getMessage(), e);
            return new SlicingResult(
                    false,
                    -1,
                    0L,
                    "",
                    "",
                    "Slicing exception: " + e.getMessage(),
                    false,
                    null
            );
        }
    }

    public SlicingResult sliceFallback(Path modelFilePath, Path outputGcodePath, String targetProfile, Throwable t) {
        log.error("SlicerService CircuitBreaker/Retry fallback triggered for model {}: {}", modelFilePath, t.getMessage());
        return new SlicingResult(
                false,
                -1,
                0L,
                "",
                "Slicer execution circuit open: " + t.getMessage(),
                "Graceful degradation: Slicing engine unavailable or temporarily failed. Please retry shortly.",
                false,
                null
        );
    }

    public SlicingResult sliceRateLimitFallback(Path modelFilePath, Path outputGcodePath, String targetProfile, Throwable t) {
        log.warn("SlicerService RateLimiter throttled request for model {}: {}", modelFilePath, t.getMessage());
        return new SlicingResult(
                false,
                -1,
                0L,
                "",
                "Slicer rate limit exceeded",
                "System busy: Concurrent slicing requests exceeded threshold. Please try again in a few moments.",
                false,
                null
        );
    }

    public record SlicingResult(
            boolean success,
            int exitCode,
            long fileSize,
            String gcodePath,
            String warnings,
            String errorDetail,
            boolean printerProfileVerified,
            Integer estimatedTime
    ) {}
}
