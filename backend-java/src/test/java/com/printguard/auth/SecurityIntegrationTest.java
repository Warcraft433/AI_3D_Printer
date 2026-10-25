package com.printguard.auth;

import com.printguard.models.GeneratedModelEntity;
import com.printguard.models.GeneratedModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeneratedModelRepository modelRepository;

    @Autowired
    private com.printguard.jobs.PrintJobRepository jobRepository;

    @Autowired
    private com.printguard.logs.ErrorLogRepository errorLogRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordService passwordService;

    private UserEntity userA;
    private UserEntity userB;
    private UserEntity adminUser;

    @BeforeEach
    void setUp() {
        errorLogRepository.deleteAll();
        jobRepository.deleteAll();
        modelRepository.deleteAll();

        userA = userRepository.findByUsername("user_alice").orElseGet(UserEntity::new);
        userA.setUsername("user_alice");
        userA.setEmail("alice@test.local");
        userA.setPassword(passwordService.encode("alicePass123!"));
        userA.setIsStaff(false);
        userA.setIsSuperuser(false);
        userA.setIsActive(true);
        userA = userRepository.save(userA);

        userB = userRepository.findByUsername("user_bob").orElseGet(UserEntity::new);
        userB.setUsername("user_bob");
        userB.setEmail("bob@test.local");
        userB.setPassword(passwordService.encode("bobPass123!"));
        userB.setIsStaff(false);
        userB.setIsSuperuser(false);
        userB.setIsActive(true);
        userB = userRepository.save(userB);

        adminUser = userRepository.findByUsername("user_admin").orElseGet(UserEntity::new);
        adminUser.setUsername("user_admin");
        adminUser.setEmail("admin@test.local");
        adminUser.setPassword(passwordService.encode("adminPass123!"));
        adminUser.setIsStaff(true);
        adminUser.setIsSuperuser(true);
        adminUser.setIsActive(true);
        adminUser = userRepository.save(adminUser);
    }

    @Test
    void publicEndpointsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"user_alice","password":"alicePass123!"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").exists());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/models/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithMalformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/models/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithInvalidSchemeReturns401() throws Exception {
        mockMvc.perform(get("/api/models/")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithExpiredTokenReturns401() throws Exception {
        // Generate token expired 1 hour ago
        String expiredToken = jwtService.generateTokenWithExpiration(
                userA.getId(), userA.getUsername(), userA.getEmail(), false, false, -3600000L);

        assertFalse(jwtService.validateToken(expiredToken));

        mockMvc.perform(get("/api/models/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithValidJwtTokenReturns200() throws Exception {
        String token = jwtService.generateToken(userA);
        assertTrue(jwtService.validateToken(token));

        mockMvc.perform(get("/api/models/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateUserRegistrationReturns409Conflict() throws Exception {
        String json = """
            {
                "username": "user_alice",
                "email": "another_alice@test.local",
                "password": "newSecurePassword123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void crossUserResourceAccessForbidden() throws Exception {
        // Alice owns a generated model
        GeneratedModelEntity aliceModel = new GeneratedModelEntity();
        aliceModel.setUser(userA);
        aliceModel.setPrompt("Alice's Private Model");
        aliceModel.setSourceType("procedural");
        aliceModel.setStatus("ready");
        aliceModel.setFilePath("models/alice_model.obj");
        aliceModel = modelRepository.save(aliceModel);

        // Bob gets a valid JWT
        String bobToken = jwtService.generateToken(userB);

        // Bob attempts to view Alice's model
        mockMvc.perform(get("/api/models/" + aliceModel.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        // Bob attempts to delete Alice's model
        mockMvc.perform(delete("/api/models/" + aliceModel.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isForbidden());

        // Verify model was not deleted
        assertTrue(modelRepository.existsById(aliceModel.getId()));
    }

    @Test
    void userCanAccessAndAdminCanDeleteResource() throws Exception {
        // Alice owns a generated model
        GeneratedModelEntity aliceModel = new GeneratedModelEntity();
        aliceModel.setUser(userA);
        aliceModel.setPrompt("Alice's Shared Model");
        aliceModel.setSourceType("procedural");
        aliceModel.setStatus("ready");
        aliceModel.setFilePath("models/alice_model_2.obj");
        aliceModel = modelRepository.save(aliceModel);

        // Alice views her own model -> 200 OK
        String aliceToken = jwtService.generateToken(userA);
        mockMvc.perform(get("/api/models/" + aliceModel.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceModel.getId()));

        // Admin deletes Alice's model -> 204 No Content
        String adminToken = jwtService.generateToken(adminUser);
        mockMvc.perform(delete("/api/models/" + aliceModel.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(modelRepository.existsById(aliceModel.getId()));
    }

    @Test
    void productionProfileRejectsDefaultJwtSecret() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("prod");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
            new JwtService(JwtService.DEFAULT_DEV_SECRET, 86400000L, env)
        );
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
            new JwtService("", 86400000L, env)
        );
    }
}
