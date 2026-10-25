package com.printguard.storage;

import com.printguard.auth.UserEntity;
import com.printguard.auth.UserRepository;
import com.printguard.jobs.PrintJobEntity;
import com.printguard.jobs.PrintJobRepository;
import com.printguard.models.GeneratedModelEntity;
import com.printguard.models.GeneratedModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorageSecurityCrossUserTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeneratedModelRepository modelRepository;

    @Autowired
    private PrintJobRepository jobRepository;

    @Autowired
    private com.printguard.logs.ErrorLogRepository errorLogRepository;

    @Value("${printguard.media-dir:c:/Projects01/AI_3D_Printer/media}")
    private String mediaDir;

    private UserEntity alice;
    private UserEntity bob;
    private UserEntity admin;
    private GeneratedModelEntity aliceModel;
    private PrintJobEntity aliceJob;

    @BeforeEach
    void setUp() throws Exception {
        errorLogRepository.deleteAll();
        jobRepository.deleteAll();
        modelRepository.deleteAll();

        alice = userRepository.findByUsername("alice").orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setUsername("alice");
            u.setEmail("alice@printguard.local");
            u.setPassword("hashed_alice_pw");
            u.setIsSuperuser(false);
            u.setIsStaff(false);
            return userRepository.save(u);
        });

        bob = userRepository.findByUsername("bob").orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setUsername("bob");
            u.setEmail("bob@printguard.local");
            u.setPassword("hashed_bob_pw");
            u.setIsSuperuser(false);
            u.setIsStaff(false);
            return userRepository.save(u);
        });

        admin = userRepository.findByUsername("admin_user").orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setUsername("admin_user");
            u.setEmail("admin@printguard.local");
            u.setPassword("hashed_admin_pw");
            u.setIsSuperuser(true);
            u.setIsStaff(true);
            return userRepository.save(u);
        });

        // Create sample test file on disk
        String modelRel = "users/" + alice.getId() + "/models/alice_rook.obj";
        Path modelPath = Paths.get(mediaDir, modelRel);
        Files.createDirectories(modelPath.getParent());
        Files.writeString(modelPath, "v 0.0 0.0 0.0\nv 1.0 1.0 1.0\nf 1 2 1\n");

        aliceModel = new GeneratedModelEntity();
        aliceModel.setUser(alice);
        aliceModel.setPrompt("Chess Rook");
        aliceModel.setFilePath(modelRel);
        aliceModel.setStatus("ready");
        aliceModel.setSourceType("procedural");
        aliceModel.setErrorDetail("[PROCEDURAL CAD] Watertight mesh");
        aliceModel = modelRepository.save(aliceModel);

        String gcodeRel = "users/" + alice.getId() + "/gcode/job_alice.gcode";
        Path gcodePath = Paths.get(mediaDir, gcodeRel);
        Files.createDirectories(gcodePath.getParent());
        Files.writeString(gcodePath, "; G-Code generated for Alice Rook\nG28\nG1 X10 Y10 E1.0\n" + "G1 X20 Y20\n".repeat(60));

        aliceJob = new PrintJobEntity();
        aliceJob.setModel(aliceModel);
        aliceJob.setStatus("ready_to_print");
        aliceJob.setGcodePath(gcodeRel);
        aliceJob.setProgress(100);
        aliceJob = jobRepository.save(aliceJob);
    }

    @Test
    @DisplayName("Owner can download own 3D model asset")
    @WithMockUser(username = "alice")
    void testOwnerCanDownloadModel() throws Exception {
        mockMvc.perform(get("/api/models/" + aliceModel.getId() + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"alice_rook.obj\"")))
                .andExpect(content().string(containsString("v 0.0 0.0 0.0")));
    }

    @Test
    @DisplayName("Owner can stream own 3D model file for 3D canvas preview")
    @WithMockUser(username = "alice")
    void testOwnerCanStreamModelFile() throws Exception {
        mockMvc.perform(get("/api/models/" + aliceModel.getId() + "/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline; filename=\"alice_rook.obj\"")))
                .andExpect(content().string(containsString("v 0.0 0.0 0.0")));
    }

    @Test
    @DisplayName("Cross-user access blocked: Bob cannot download Alice's 3D model (403 Forbidden)")
    @WithMockUser(username = "bob")
    void testCrossUserCannotDownloadModel() throws Exception {
        mockMvc.perform(get("/api/models/" + aliceModel.getId() + "/download"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cross-user access blocked: Bob cannot stream Alice's 3D model file (403 Forbidden)")
    @WithMockUser(username = "bob")
    void testCrossUserCannotStreamModel() throws Exception {
        mockMvc.perform(get("/api/models/" + aliceModel.getId() + "/file"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to download model returns 401 Unauthorized")
    void testUnauthenticatedCannotDownloadModel() throws Exception {
        mockMvc.perform(get("/api/models/" + aliceModel.getId() + "/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Request for non-existent model returns 404 Not Found")
    @WithMockUser(username = "alice")
    void testNonExistentModelReturns404() throws Exception {
        mockMvc.perform(get("/api/models/999999/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Owner can download own sliced G-code file")
    @WithMockUser(username = "alice")
    void testOwnerCanDownloadGCode() throws Exception {
        mockMvc.perform(get("/api/print-jobs/" + aliceJob.getId() + "/download-gcode"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"job_alice.gcode\"")))
                .andExpect(content().string(containsString("G-Code generated for Alice Rook")));
    }

    @Test
    @DisplayName("Cross-user access blocked: Bob cannot download Alice's G-code (403 Forbidden)")
    @WithMockUser(username = "bob")
    void testCrossUserCannotDownloadGCode() throws Exception {
        mockMvc.perform(get("/api/print-jobs/" + aliceJob.getId() + "/download-gcode"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to download G-code returns 401 Unauthorized")
    void testUnauthenticatedCannotDownloadGCode() throws Exception {
        mockMvc.perform(get("/api/print-jobs/" + aliceJob.getId() + "/download-gcode"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin can download model and G-code across users")
    @WithMockUser(username = "admin_user")
    void testAdminCanAccessAcrossUsers() throws Exception {
        mockMvc.perform(get("/api/models/" + aliceModel.getId() + "/download"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/print-jobs/" + aliceJob.getId() + "/download-gcode"))
                .andExpect(status().isOk());
    }
}
