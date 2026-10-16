package com.printguard.dashboard;

import com.printguard.auth.SecurityUtils;
import com.printguard.jobs.OctoPrintService;
import com.printguard.jobs.PrintJobRepository;
import com.printguard.logs.ErrorLogRepository;
import com.printguard.models.GeneratedModelRepository;
import com.printguard.models.GenerationController;
import com.printguard.storage.StorageService;
import com.printguard.websocket.WebSocketEventPublisher;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceSecurityTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastReceivedToken = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        lastReceivedToken.set(null);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/inference/detect-frame", exchange -> {
            lastReceivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            byte[] resp = "{\"detections\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/api/geometry/generate", exchange -> {
            lastReceivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            byte[] resp = "{\"success\":true,\"piece_name\":\"Test\",\"metadata_description\":\"desc\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void dashboardControllerPropagatesInternalTokenHeaderWhenConfigured() {
        String testApiKey = "pg-ai-sec-token-998877";
        DashboardController controller = new DashboardController(
                Mockito.mock(GeneratedModelRepository.class),
                Mockito.mock(PrintJobRepository.class),
                Mockito.mock(ErrorLogRepository.class),
                Mockito.mock(SecurityUtils.class),
                Mockito.mock(WebSocketEventPublisher.class),
                Mockito.mock(OctoPrintService.class),
                "http://127.0.0.1:" + port,
                testApiKey
        );

        MockMultipartFile file = new MockMultipartFile("frame", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});
        ResponseEntity<Map<String, Object>> response = controller.detectFrame(file, 0.35);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(testApiKey, lastReceivedToken.get(), "X-Internal-Token header must match configured API key");
    }

    @Test
    void dashboardControllerOmitsTokenHeaderWhenNotConfigured() {
        DashboardController controller = new DashboardController(
                Mockito.mock(GeneratedModelRepository.class),
                Mockito.mock(PrintJobRepository.class),
                Mockito.mock(ErrorLogRepository.class),
                Mockito.mock(SecurityUtils.class),
                Mockito.mock(WebSocketEventPublisher.class),
                Mockito.mock(OctoPrintService.class),
                "http://127.0.0.1:" + port,
                "" // Empty / not configured
        );

        MockMultipartFile file = new MockMultipartFile("frame", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});
        ResponseEntity<Map<String, Object>> response = controller.detectFrame(file, 0.35);

        assertEquals(200, response.getStatusCode().value());
        assertNull(lastReceivedToken.get(), "X-Internal-Token header must be omitted when not configured");
    }

    @Test
    void generationControllerPropagatesInternalTokenHeaderWhenConfigured() throws Exception {
        String testApiKey = "pg-ai-sec-token-112233";
        GenerationController controller = new GenerationController(
                Mockito.mock(GeneratedModelRepository.class),
                Mockito.mock(SecurityUtils.class),
                Mockito.mock(WebSocketEventPublisher.class),
                Mockito.mock(StorageService.class),
                "http://127.0.0.1:" + port,
                testApiKey
        );

        Path tempDest = Files.createTempFile("gen_test", ".obj");
        try {
            // Write some dummy content to satisfy Files.exists
            Files.writeString(tempDest, "# dummy obj header");

            // Invoke tryGenerateProceduralModel via reflection or indirectly
            java.lang.reflect.Method method = GenerationController.class.getDeclaredMethod("tryGenerateProceduralModel", String.class, Path.class);
            method.setAccessible(true);
            method.invoke(controller, "Knight", tempDest);

            assertEquals(testApiKey, lastReceivedToken.get(), "GenerationController must propagate X-Internal-Token");
        } finally {
            Files.deleteIfExists(tempDest);
        }
    }
}
