package com.printguard.ai;

import com.printguard.slicer.SlicerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiBridgeResilienceTest {

    @Test
    @DisplayName("AiBridgeService returns fallback response gracefully when FastAPI service is unreachable")
    void testAiBridgeDetectFrameFallback() {
        // Point to an unavailable endpoint
        AiBridgeService bridge = new AiBridgeService("http://127.0.0.1:59999", "secret-test-token");
        byte[] dummyFrame = new byte[]{1, 2, 3};

        Map<String, Object> result = bridge.detectFrame(dummyFrame);

        assertNotNull(result);
        assertTrue(result.containsKey("fallback") || result.containsKey("detections"));
    }

    @Test
    @DisplayName("AiBridgeService returns fallback when geometry generation fails")
    void testAiBridgeGenerateGeometryFallback() {
        AiBridgeService bridge = new AiBridgeService("http://127.0.0.1:59999", "secret-test-token");
        Map<String, Object> result = bridge.generateGeometry("test prompt", Path.of("target/test.obj"));

        assertNotNull(result);
        assertEquals(false, result.get("success"));
        assertEquals(true, result.get("fallback"));
    }

    @Test
    @DisplayName("SlicerService fallback returns graceful degradation on failure")
    void testSlicerFallback() {
        SlicerService slicer = new SlicerService("c:/invalid/path/slicer.exe", "Original Prusa i3 MK3S & MK3S+");
        SlicerService.SlicingResult fallback = slicer.sliceFallback(
                Path.of("model.obj"),
                Path.of("out.gcode"),
                "Original Prusa i3 MK3S & MK3S+",
                new RuntimeException("Simulated timeout or process crash")
        );

        assertNotNull(fallback);
        assertFalse(fallback.success());
        assertTrue(fallback.errorDetail().contains("Graceful degradation"));
    }

    @Test
    @DisplayName("SlicerService rate limiter fallback returns system busy")
    void testSlicerRateLimitFallback() {
        SlicerService slicer = new SlicerService("c:/invalid/path/slicer.exe", "Original Prusa i3 MK3S & MK3S+");
        SlicerService.SlicingResult rateLimitResult = slicer.sliceRateLimitFallback(
                Path.of("model.obj"),
                Path.of("out.gcode"),
                "Original Prusa i3 MK3S & MK3S+",
                new RuntimeException("Rate limit reached")
        );

        assertNotNull(rateLimitResult);
        assertFalse(rateLimitResult.success());
        assertTrue(rateLimitResult.warnings().contains("rate limit exceeded"));
    }

    @Test
    @DisplayName("AiBridgeService returns fallback response when generateTextTo3D FastAPI is unreachable")
    void testAiBridgeGenerateTextTo3DFallback() {
        AiBridgeService bridge = new AiBridgeService("http://127.0.0.1:59999", "secret-test-token");
        Map<String, Object> result = bridge.generateTextTo3D("Chess King", Path.of("target/test_text.obj"), "shap-e", 15.0, 32, "obj");

        assertNotNull(result);
        assertTrue(result.containsKey("fallback") || result.containsKey("is_fallback") || result.containsKey("success"));
        assertEquals(true, result.get("fallback"));
    }

    @Test
    @DisplayName("AiBridgeService returns fallback response when generateImageTo3D FastAPI is unreachable")
    void testAiBridgeGenerateImageTo3DFallback() {
        AiBridgeService bridge = new AiBridgeService("http://127.0.0.1:59999", "secret-test-token");
        org.springframework.mock.web.MockMultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile(
                "image", "test.png", "image/png", new byte[]{1, 2, 3, 4}
        );
        Map<String, Object> result = bridge.generateImageTo3D(mockFile, Path.of("target/test_img.obj"), "test model");

        assertNotNull(result);
        assertTrue(result.containsKey("fallback") || result.containsKey("is_fallback") || result.containsKey("success"));
        assertEquals(true, result.get("fallback"));
    }
}

