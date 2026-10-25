package com.printguard.models;

import com.jayway.jsonpath.JsonPath;
import com.printguard.auth.UserEntity;
import com.printguard.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("testuser").isEmpty()) {
            UserEntity user = new UserEntity();
            user.setUsername("testuser");
            user.setEmail("testuser@printguard.local");
            user.setPassword("pbkdf2_sha256$hashed");
            userRepository.save(user);
        }
    }

    @Test
    void generateTextUnauthenticatedReturnsUnauthorized() throws Exception {
        String json = """
            {
                "prompt": "Test Cube Model",
                "steps": 32
            }
            """;
        mockMvc.perform(post("/api/generate/text/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getStatusHonestReportingShapeUnavailable() throws Exception {
        mockMvc.perform(get("/api/generate/status/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shape_available", is(false)))
                .andExpect(jsonPath("$.fallback_mode", is(true)))
                .andExpect(jsonPath("$.limitation_note", containsString("YOLO defect detection only")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateTextIdentifiesModelAsDemoFallback() throws Exception {
        String json = """
            {
                "prompt": "Test Cube Model",
                "steps": 32,
                "force_demo": true
            }
            """;

        mockMvc.perform(post("/api/generate/text/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.is_fallback", is(true)))
                .andExpect(jsonPath("$.error_detail", containsString("[DEMO FALLBACK]")))
                .andExpect(jsonPath("$.file_url", not(emptyString())));
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateTextProceduralChessKingProducesRecognizableNonDemoGeometry() throws Exception {
        String json = """
            {
                "prompt": "Staunton Chess King with cross finial and pedestal base",
                "steps": 32
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/generate/text/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.is_fallback", is(false)))
                .andExpect(jsonPath("$.generation_method", is("procedural_cad")))
                .andExpect(jsonPath("$.source_type", is("procedural")))
                .andExpect(jsonPath("$.error_detail", containsString("[PROCEDURAL CAD]")))
                .andExpect(jsonPath("$.file_url", not(emptyString())))
                .andReturn();

        String filePath = JsonPath.read(result.getResponse().getContentAsString(), "$.file_path");
        assertNotNull(filePath);

        Path fullPath = Paths.get("c:/Projects01/AI_3D_Printer/media", filePath);
        assertTrue(Files.exists(fullPath), "King OBJ file must exist on disk");
        long size = Files.size(fullPath);
        assertTrue(size > 20000, "King OBJ should be a rich mesh (> 20KB), got: " + size);

        String objContent = Files.readString(fullPath);
        assertFalse(objContent.contains("[DEMO FALLBACK]"), "Should not contain demo fallback tag");
        assertTrue(objContent.contains("v "), "OBJ must contain vertex definitions");
        assertTrue(objContent.contains("f "), "OBJ must contain face definitions");
    }

    @Test
    @WithMockUser(username = "testuser")
    void generateTextProceduralChessKnightProducesRecognizableNonDemoGeometry() throws Exception {
        String json = """
            {
                "prompt": "Staunton Chess Knight with horse head and arched neck",
                "steps": 32
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/generate/text/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.is_fallback", is(false)))
                .andExpect(jsonPath("$.generation_method", is("procedural_cad")))
                .andExpect(jsonPath("$.source_type", is("procedural")))
                .andExpect(jsonPath("$.error_detail", containsString("[PROCEDURAL CAD]")))
                .andExpect(jsonPath("$.file_url", not(emptyString())))
                .andReturn();

        String filePath = JsonPath.read(result.getResponse().getContentAsString(), "$.file_path");
        assertNotNull(filePath);

        Path fullPath = Paths.get("c:/Projects01/AI_3D_Printer/media", filePath);
        assertTrue(Files.exists(fullPath), "Knight OBJ file must exist on disk");
        long size = Files.size(fullPath);
        assertTrue(size > 15000, "Knight OBJ should be a rich mesh (> 15KB), got: " + size);
    }

    @Test
    @WithMockUser(username = "testuser")
    void verifyKingAndKnightHaveDistinctNonIdenticalGeometry() throws Exception {
        String kingJson = """
            {
                "prompt": "Chess King with royal crown",
                "steps": 32
            }
            """;
        MvcResult kingResult = mockMvc.perform(post("/api/generate/text/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(kingJson))
                .andExpect(status().isCreated())
                .andReturn();

        String knightJson = """
            {
                "prompt": "Chess Knight with horse head profile",
                "steps": 32
            }
            """;
        MvcResult knightResult = mockMvc.perform(post("/api/generate/text/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(knightJson))
                .andExpect(status().isCreated())
                .andReturn();

        String kingFilePath = JsonPath.read(kingResult.getResponse().getContentAsString(), "$.file_path");
        String knightFilePath = JsonPath.read(knightResult.getResponse().getContentAsString(), "$.file_path");

        Path kingPath = Paths.get("c:/Projects01/AI_3D_Printer/media", kingFilePath);
        Path knightPath = Paths.get("c:/Projects01/AI_3D_Printer/media", knightFilePath);

        assertTrue(Files.exists(kingPath));
        assertTrue(Files.exists(knightPath));

        long kingSize = Files.size(kingPath);
        long knightSize = Files.size(knightPath);

        assertNotEquals(kingSize, knightSize, "King and Knight must have different mesh file sizes");

        long kingVertCount = Files.lines(kingPath).filter(l -> l.startsWith("v ")).count();
        long knightVertCount = Files.lines(knightPath).filter(l -> l.startsWith("v ")).count();

        assertNotEquals(kingVertCount, knightVertCount, "King and Knight must have different vertex counts");
        assertTrue(kingVertCount > 500, "King should have > 500 vertices, got " + kingVertCount);
        assertTrue(knightVertCount > 300, "Knight should have > 300 vertices, got " + knightVertCount);
    }
}
