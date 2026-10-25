package com.printguard.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        userRepository.findByUsername("newuser").ifPresent(u -> {
            refreshTokenRepository.deleteByUserId(u.getId());
            userRepository.delete(u);
        });
        userRepository.findByUsername("badpassuser").ifPresent(u -> {
            refreshTokenRepository.deleteByUserId(u.getId());
            userRepository.delete(u);
        });

        UserEntity admin = userRepository.findByUsername("testadmin").orElseGet(UserEntity::new);
        admin.setUsername("testadmin");
        admin.setEmail("testadmin@printguard.local");
        admin.setPassword(passwordService.encode("secretPassword123"));
        admin.setIsStaff(true);
        userRepository.save(admin);
    }

    @Test
    void passwordServiceEncodesAndMatches() {
        String encoded = passwordService.encode("mySecretPass");
        assertTrue(encoded.startsWith("pbkdf2_sha256$"));
        assertTrue(passwordService.matches("mySecretPass", encoded));
        assertFalse(passwordService.matches("wrongPass", encoded));
    }

    @Test
    void passwordServiceMatchesDjangoGeneratedHash() {
        String djangoHash = "pbkdf2_sha256$1000000$HKtmNoYW8YYBe2AbgSgG5a$dMHNLbkHKeiy5S7UPagYV29yftb9Qz1byLSA4VP4V+c=";
        assertTrue(passwordService.matches("adminpassword123", djangoHash));
        assertFalse(passwordService.matches("wrongPassword", djangoHash));
    }

    @Test
    void loginWithValidCredentialsSucceeds() throws Exception {
        String json = """
            {
                "username": "testadmin",
                "password": "secretPassword123"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").exists())
                .andExpect(jsonPath("$.user.username").value("testadmin"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        String json = """
            {
                "username": "testadmin",
                "password": "wrongpassword"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithNonexistentUserReturnsUnauthorized() throws Exception {
        String json = """
            {
                "username": "nosuchuser",
                "password": "somepassword"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerNewUserHashesPasswordAndSucceeds() throws Exception {
        String json = """
            {
                "username": "newuser",
                "email": "newuser@test.local",
                "password": "newSecurePassword123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.password").doesNotExist());

        UserEntity saved = userRepository.findByUsername("newuser").orElseThrow();
        assertTrue(saved.getPassword().startsWith("pbkdf2_sha256$"));
        assertTrue(passwordService.matches("newSecurePassword123", saved.getPassword()));
        assertFalse(saved.getPassword().contains("newSecurePassword123")); // not plain text!
    }

    @Test
    void registerWithShortPasswordFails() throws Exception {
        String json = """
            {
                "username": "badpassuser",
                "email": "bad@test.local",
                "password": "123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isBadRequest());
    }
}
