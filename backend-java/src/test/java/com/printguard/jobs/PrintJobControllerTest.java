package com.printguard.jobs;

import com.printguard.auth.UserEntity;
import com.printguard.auth.UserRepository;
import com.printguard.models.GeneratedModelEntity;
import com.printguard.models.GeneratedModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
class PrintJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrintJobRepository jobRepository;

    @Autowired
    private GeneratedModelRepository modelRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername("testuser").orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setUsername("testuser");
            user.setEmail("testuser@printguard.local");
            user.setPassword("pbkdf2_sha256$hashed");
            return userRepository.save(user);
        });

        if (modelRepository.count() == 0) {
            GeneratedModelEntity sample = new GeneratedModelEntity();
            sample.setUser(testUser);
            sample.setPrompt("Sample Print Model");
            sample.setSourceType("procedural");
            sample.setStatus("ready");
            sample.setFilePath("models/sample.obj");
            sample = modelRepository.save(sample);

            PrintJobEntity sampleJob = new PrintJobEntity();
            sampleJob.setModel(sample);
            sampleJob.setStatus("queued");
            jobRepository.save(sampleJob);
        }
    }

    @Test
    void listJobsUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/print-jobs/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void listJobsReturnsOkWithExpectedFields() throws Exception {
        mockMvc.perform(get("/api/print-jobs/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].model_prompt").exists());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getActiveJobReturnsOk() throws Exception {
        mockMvc.perform(get("/api/print-jobs/active-job/"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "testuser")
    void createJobForDemoMeshRejectsPrinterReadyAndMarksFailed() throws Exception {
        GeneratedModelEntity demoModel = new GeneratedModelEntity();
        demoModel.setUser(testUser);
        demoModel.setPrompt("Demo Cube Mesh");
        demoModel.setSourceType("text");
        demoModel.setStatus("ready");
        demoModel.setFilePath("demo/test_cube.obj");
        demoModel.setErrorDetail("[DEMO FALLBACK] Generated static demo mesh (cube).");
        demoModel = modelRepository.save(demoModel);

        String json = "{\"model\": " + demoModel.getId() + "}";

        mockMvc.perform(post("/api/print-jobs/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("failed")))
                .andExpect(jsonPath("$.error_detail", containsString("Demo placeholder meshes cannot be sliced")))
                .andExpect(jsonPath("$.is_real_gcode", is(false)))
                .andExpect(jsonPath("$.printer_profile_verified", is(false)));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createJobWithRealSlicingSucceedsForProceduralMesh() throws Exception {
        File binary = new File("c:/Projects01/AI_3D_Printer/tools/PrusaSlicer/PrusaSlicer-2.9.6/prusa-slicer-console.exe");
        if (!binary.exists()) {
            return; // Skip if environment slicer is not present
        }

        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(testUser);
        model.setPrompt("Chess King Test Slice Model");
        model.setSourceType("procedural");
        model.setStatus("ready");
        model.setFilePath("test_models/test_king_slice.obj");
        model.setErrorDetail("[PROCEDURAL CAD] Generated parametric Staunton Chess King mesh using Trimesh engine.");

        // Write a valid test mesh on disk
        Path testObj = Paths.get("c:/Projects01/AI_3D_Printer/media/test_models/test_king_slice.obj");
        Files.createDirectories(testObj.getParent());
        Path sourceObj = Paths.get("c:/Projects01/AI_3D_Printer/artifacts/test_cad/cli_king.obj");
        if (Files.exists(sourceObj)) {
            Files.copy(sourceObj, testObj, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            return;
        }

        model = modelRepository.save(model);

        String json = "{\"model\": " + model.getId() + "}";

        mockMvc.perform(post("/api/print-jobs/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ready_to_print")))
                .andExpect(jsonPath("$.is_real_gcode", is(true)))
                .andExpect(jsonPath("$.printer_profile_verified", is(true)))
                .andExpect(jsonPath("$.gcode_url", not(emptyString())))
                .andExpect(jsonPath("$.estimated_time", greaterThan(0)));
    }

    private PrintJobEntity createTestJob(String status, String gcodePath) {
        GeneratedModelEntity model = new GeneratedModelEntity();
        model.setUser(testUser);
        model.setPrompt("Test Job Model for testuser");
        model.setSourceType("procedural");
        model.setStatus("ready");
        model.setFilePath("test_models/test.obj");
        model = modelRepository.save(model);

        PrintJobEntity job = new PrintJobEntity();
        job.setModel(model);
        job.setStatus(status);
        job.setGcodePath(gcodePath != null ? gcodePath : "");
        return jobRepository.save(job);
    }

    @Test
    @WithMockUser(username = "testuser")
    void downloadGCodeRejectsDemoGcode() throws Exception {
        PrintJobEntity demoJob = createTestJob("failed", "demo/demo_print.gcode");

        mockMvc.perform(get("/api/print-jobs/" + demoJob.getId() + "/download-gcode/"))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewGCodeUnauthenticatedReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/print-jobs/1/gcode/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void previewGCodeRejectsDemoGcode() throws Exception {
        PrintJobEntity demoJob = createTestJob("failed", "demo/demo_print.gcode");

        mockMvc.perform(get("/api/print-jobs/" + demoJob.getId() + "/gcode/"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "otheruser")
    void previewGCodeRejectsUnauthorizedUser() throws Exception {
        UserEntity otherUser = userRepository.findByUsername("otheruser").orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setUsername("otheruser");
            user.setEmail("otheruser@printguard.local");
            user.setPassword("pbkdf2_sha256$hashed");
            return userRepository.save(user);
        });

        PrintJobEntity job = createTestJob("ready_to_print", "gcode/test_cube.gcode");

        mockMvc.perform(get("/api/print-jobs/" + job.getId() + "/gcode/"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser")
    void previewGCodeReturnsInlineTextForOwner() throws Exception {
        // Ensure a genuine G-code file exists in local media folder
        Path target = Paths.get("c:/Projects01/AI_3D_Printer/media/gcode/test_cube.gcode");
        if (!Files.exists(target)) {
            return;
        }

        PrintJobEntity job = createTestJob("ready_to_print", "gcode/test_cube.gcode");

        mockMvc.perform(get("/api/print-jobs/" + job.getId() + "/gcode/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", containsString("inline")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("text/plain"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("; generated by PrusaSlicer")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void startPrintRejectsJobNotReadyToPrint() throws Exception {
        PrintJobEntity failedJob = createTestJob("failed", "");

        mockMvc.perform(post("/api/print-jobs/" + failedJob.getId() + "/start-print/"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser")
    void startPrintRejectsWithServiceUnavailableWhenOctoPrintIsOffline() throws Exception {
        PrintJobEntity job = createTestJob("ready_to_print", "gcode/valid_test.gcode");

        mockMvc.perform(post("/api/print-jobs/" + job.getId() + "/start-print/"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.hardware_confirmed", is(false)))
                .andExpect(jsonPath("$.limitation_note", containsString("offline")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void pausePrintRejectsWithServiceUnavailableWhenOctoPrintIsOffline() throws Exception {
        PrintJobEntity job = createTestJob("ready_to_print", "gcode/valid_test.gcode");
        mockMvc.perform(post("/api/print-jobs/" + job.getId() + "/pause/"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.hardware_confirmed", is(false)))
                .andExpect(jsonPath("$.limitation_note", containsString("offline")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void resumePrintRejectsWithServiceUnavailableWhenOctoPrintIsOffline() throws Exception {
        PrintJobEntity job = createTestJob("ready_to_print", "gcode/valid_test.gcode");
        mockMvc.perform(post("/api/print-jobs/" + job.getId() + "/resume/"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.hardware_confirmed", is(false)))
                .andExpect(jsonPath("$.limitation_note", containsString("offline")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void cancelPrintSucceedsForInactiveQueueJob() throws Exception {
        PrintJobEntity job = createTestJob("queued", "");

        mockMvc.perform(post("/api/print-jobs/" + job.getId() + "/cancel/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("cancelled")));
    }
}
