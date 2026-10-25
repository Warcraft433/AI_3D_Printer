package com.printguard.slicer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SlicerServiceTest {

    private final String realSlicerPath = "c:/Projects01/AI_3D_Printer/tools/PrusaSlicer/PrusaSlicer-2.9.6/prusa-slicer-console.exe";
    private final String printerProfile = "Original Prusa i3 MK3S & MK3S+";

    @Test
    void slicerIsAvailableWhenBinaryExists() {
        SlicerService service = new SlicerService(realSlicerPath, printerProfile);
        if (new File(realSlicerPath).exists()) {
            assertTrue(service.isAvailable(), "Slicer should be available if binary exists");
            String version = service.getSlicerVersion();
            assertTrue(version.contains("PrusaSlicer") || version.contains("2.9.6"),
                    "Version should report PrusaSlicer: " + version);
        }
    }

    @Test
    void slicerExecutableDiscoveryWithAutoDetection() {
        // When configured path is blank or null, auto-discovery should discover local binary if present
        SlicerService autoService = new SlicerService("", printerProfile);
        assertNotNull(autoService.getSlicerPath());
        assertFalse(autoService.getSlicerPath().isBlank());

        if (new File(realSlicerPath).exists()) {
            assertTrue(autoService.isAvailable(), "Auto-discovery should locate real slicer on local Windows development");
            assertEquals(new File(realSlicerPath).getAbsolutePath(), new File(autoService.getSlicerPath()).getAbsolutePath());
        }
    }

    @Test
    void slicerIsUnavailableWhenBinaryPathIsInvalid() {
        SlicerService service = new SlicerService("c:/invalid/path/slicer.exe", printerProfile);
        assertFalse(service.isAvailable());
        assertEquals("Unavailable", service.getSlicerVersion());

        SlicerService.SlicingResult result = service.slice(Path.of("model.obj"), Path.of("out.gcode"), printerProfile);
        assertFalse(result.success());
        assertEquals(-1, result.exitCode());
        assertTrue(result.errorDetail().contains("unavailable"));
    }

    @Test
    void slicerRejectsMissingOrNullInputModel(@TempDir Path tempDir) {
        SlicerService service = new SlicerService(realSlicerPath, printerProfile);
        Path outGcode = tempDir.resolve("out.gcode");

        // Null input path
        SlicerService.SlicingResult nullResult = service.slice(null, outGcode, printerProfile);
        assertFalse(nullResult.success());
        assertTrue(nullResult.errorDetail().contains("does not exist or is invalid"));
        assertFalse(Files.exists(outGcode));

        // Non-existent file path
        Path nonExistent = tempDir.resolve("missing_model.obj");
        SlicerService.SlicingResult missingResult = service.slice(nonExistent, outGcode, printerProfile);
        assertFalse(missingResult.success());
        assertTrue(missingResult.errorDetail().contains("does not exist or is invalid"));
        assertFalse(Files.exists(outGcode));
    }

    @Test
    void slicerRejectsEmptyOrCorruptedModelFile(@TempDir Path tempDir) throws Exception {
        SlicerService service = new SlicerService(realSlicerPath, printerProfile);
        Path emptyObj = tempDir.resolve("corrupt_tiny.obj");
        Files.writeString(emptyObj, "v 0 0"); // 5 bytes (< 10 bytes)

        Path outGcode = tempDir.resolve("out.gcode");
        SlicerService.SlicingResult result = service.slice(emptyObj, outGcode, printerProfile);

        assertFalse(result.success());
        assertTrue(result.errorDetail().contains("empty or corrupted"));
        assertFalse(Files.exists(outGcode));
    }

    @Test
    void slicerRejectsDemoFallbackMesh(@TempDir Path tempDir) throws Exception {
        SlicerService service = new SlicerService(realSlicerPath, printerProfile);
        Path demoObj = tempDir.resolve("demo_cube.obj");
        Files.writeString(demoObj, """
            # PrintGuard 3D Model Output [DEMO FALLBACK]
            v -1.0 -1.0 1.0
            v 1.0 -1.0 1.0
            f 1 2 1
            """);

        Path outGcode = tempDir.resolve("out.gcode");
        SlicerService.SlicingResult result = service.slice(demoObj, outGcode, printerProfile);

        assertFalse(result.success(), "Should reject demo fallback mesh");
        assertTrue(result.errorDetail().contains("Demo fallback meshes cannot be sliced"));
        assertFalse(Files.exists(outGcode));
    }

    @Test
    void slicerHandlesProcessFailureNonZeroExit(@TempDir Path tempDir) throws Exception {
        // Create an executable batch file/script that exits with code 2
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        Path failScript = tempDir.resolve(isWindows ? "fail_slicer.bat" : "fail_slicer.sh");
        if (isWindows) {
            Files.writeString(failScript, "@echo Error: Mesh is non-manifold & exit /b 2");
        } else {
            Files.writeString(failScript, "#!/bin/sh\necho 'Error: Mesh is non-manifold'\nexit 2");
            failScript.toFile().setExecutable(true);
        }

        SlicerService service = new SlicerService(failScript.toAbsolutePath().toString(), printerProfile, 10);
        Path validObj = tempDir.resolve("model.obj");
        Files.writeString(validObj, "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n# valid dummy model header");

        Path outGcode = tempDir.resolve("out.gcode");
        SlicerService.SlicingResult result = service.slice(validObj, outGcode, printerProfile);

        assertFalse(result.success(), "Process failure must return success = false");
        assertEquals(2, result.exitCode(), "Should capture non-zero exit code");
        assertTrue(result.errorDetail().contains("Slicing failed with exit code 2"), "Should report error detail: " + result.errorDetail());
        assertFalse(Files.exists(outGcode), "Failed slicing must never generate G-code");
    }

    @Test
    void slicerHandlesProcessTimeoutEnforcement(@TempDir Path tempDir) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        Path hangScript = tempDir.resolve(isWindows ? "hang_slicer.bat" : "hang_slicer.sh");
        if (isWindows) {
            // Ping localhost 6 times (~5 seconds delay)
            Files.writeString(hangScript, "@ping 127.0.0.1 -n 6 >nul");
        } else {
            Files.writeString(hangScript, "#!/bin/sh\nsleep 5");
            hangScript.toFile().setExecutable(true);
        }

        // Configure SlicerService with 1-second timeout
        SlicerService service = new SlicerService(hangScript.toAbsolutePath().toString(), printerProfile, 1);
        assertEquals(1, service.getTimeoutSeconds());

        Path validObj = tempDir.resolve("model.obj");
        Files.writeString(validObj, "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n# valid dummy model header");

        Path outGcode = tempDir.resolve("out.gcode");
        SlicerService.SlicingResult result = service.slice(validObj, outGcode, printerProfile);

        assertFalse(result.success(), "Timed out slicing must return success = false");
        assertEquals(-1, result.exitCode());
        assertTrue(result.errorDetail().contains("timed out after 1 seconds"), "Error detail must report timeout: " + result.errorDetail());
        assertFalse(Files.exists(outGcode), "Timed out slicing must not generate G-code");
    }

    @Test
    void slicerProducesGenuineGCodeForValidMesh(@TempDir Path tempDir) throws Exception {
        File binary = new File(realSlicerPath);
        if (!binary.exists()) {
            return; // Skip if binary not in environment
        }

        SlicerService service = new SlicerService(realSlicerPath, printerProfile);

        // Path to validated procedural King or Knight OBJ
        Path kingObj = Path.of("c:/Projects01/AI_3D_Printer/artifacts/test_cad/cli_king.obj");
        if (!Files.exists(kingObj)) {
            return;
        }

        Path outGcode = tempDir.resolve("king_test.gcode");
        SlicerService.SlicingResult result = service.slice(kingObj, outGcode, printerProfile);

        assertTrue(result.success(), "Slicing should succeed: " + result.errorDetail());
        assertEquals(0, result.exitCode());
        assertTrue(result.fileSize() > 10000, "G-code should be > 10KB, got: " + result.fileSize());
        assertTrue(Files.exists(outGcode));
        assertTrue(result.printerProfileVerified());
        assertNotNull(result.estimatedTime());

        // Check genuine G-code contents
        String gcodeContent = Files.readString(outGcode);
        assertTrue(gcodeContent.contains("PrusaSlicer"), "G-code must have slicer header");
        assertTrue(gcodeContent.contains("G1 ") || gcodeContent.contains("M104"), "G-code must have motion/extrusion commands");
        assertFalse(gcodeContent.contains("PrintGuard Demo GCode"), "Must not be demo G-code");
    }
}
