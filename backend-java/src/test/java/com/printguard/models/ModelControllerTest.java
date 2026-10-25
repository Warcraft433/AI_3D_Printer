package com.printguard.models;

import com.jayway.jsonpath.JsonPath;
import com.printguard.auth.UserEntity;
import com.printguard.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeneratedModelRepository modelRepository;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("modeltestuser").isEmpty()) {
            UserEntity user = new UserEntity();
            user.setUsername("modeltestuser");
            user.setEmail("modeltestuser@printguard.local");
            user.setPassword("pbkdf2_sha256$hashed");
            userRepository.save(user);
        }
    }

    @Test
    @DisplayName("POST /api/models/generate unauthenticated returns 401")
    void generateUnauthenticatedReturnsUnauthorized() throws Exception {
        String json = """
            {
                "prompt": "Staunton Chess King",
                "engine": "shap-e",
                "guidanceScale": 15.0,
                "inferenceSteps": 32
            }
            """;
        mockMvc.perform(post("/api/models/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/models/generate-from-image unauthenticated returns 401")
    void generateFromImageUnauthenticatedReturnsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image", "sample.png", "image/png", new byte[]{1, 2, 3, 4}
        );
        mockMvc.perform(multipart("/api/models/generate-from-image")
                .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "modeltestuser")
    @DisplayName("POST /api/models/generate rejects empty prompt with 400")
    void generateRejectsEmptyPrompt() throws Exception {
        String json = """
            {
                "prompt": ""
            }
            """;
        mockMvc.perform(post("/api/models/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "modeltestuser")
    @DisplayName("POST /api/models/generate-from-image rejects empty image with 400")
    void generateFromImageRejectsEmptyImage() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.png", "image/png", new byte[0]
        );
        mockMvc.perform(multipart("/api/models/generate-from-image")
                .file(emptyFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "modeltestuser")
    @DisplayName("POST /api/models/generate creates valid 3D model with prompt and engine parameters")
    void generateTextTo3DSuccess() throws Exception {
        String json = """
            {
                "prompt": "Staunton Chess Knight horse head",
                "engine": "procedural",
                "guidanceScale": 15.0,
                "inferenceSteps": 32,
                "format": "obj"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/models/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.prompt", containsString("Chess Knight")))
                .andExpect(jsonPath("$.status", is("ready")))
                .andExpect(jsonPath("$.file_url", not(emptyString())))
                .andReturn();

        Number modelId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertNotNull(modelId);

        GeneratedModelEntity entity = modelRepository.findById(modelId.longValue()).orElse(null);
        assertNotNull(entity, "Model entity must be persisted in database");
        assertEquals("modeltestuser", entity.getUser().getUsername());
        assertTrue(entity.getFilePath().endsWith(".obj"));

        Path fullPath = Paths.get("c:/Projects01/AI_3D_Printer/media", entity.getFilePath());
        assertTrue(Files.exists(fullPath), "Model file must be stored on disk");
        assertTrue(Files.size(fullPath) > 500, "Model file size must be non-trivial");
    }

    @Test
    @WithMockUser(username = "modeltestuser")
    @DisplayName("POST /api/models/generate-from-image creates 3D model from uploaded image")
    void generateFromImageSuccess() throws Exception {
        byte[] dummyPng = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89
        };

        MockMultipartFile file = new MockMultipartFile(
                "image", "coffee_cup_photo.png", "image/png", dummyPng
        );

        MvcResult result = mockMvc.perform(multipart("/api/models/generate-from-image")
                .file(file)
                .param("name", "Coffee Cup")
                .param("engine", "procedural")
                .param("guidanceScale", "3.0")
                .param("inferenceSteps", "32")
                .param("format", "obj"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.source_type", is("image")))
                .andExpect(jsonPath("$.status", is("ready")))
                .andExpect(jsonPath("$.file_url", not(emptyString())))
                .andReturn();

        Number modelId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertNotNull(modelId);

        GeneratedModelEntity entity = modelRepository.findById(modelId.longValue()).orElse(null);
        assertNotNull(entity, "Model must be saved in repository");
        assertEquals("image", entity.getSourceType());
    }

    @Test
    @WithMockUser(username = "modeltestuser")
    @DisplayName("GET /api/models returns user's generated models")
    void listModelsReturnsCollection() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
