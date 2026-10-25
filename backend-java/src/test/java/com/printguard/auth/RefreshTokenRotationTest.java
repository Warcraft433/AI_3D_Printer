package com.printguard.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenRotationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        String username = "rotation_user_" + UUID.randomUUID().toString().substring(0, 8);
        testUser = new UserEntity();
        testUser.setUsername(username);
        testUser.setEmail(username + "@printguard.io");
        testUser.setPassword("hashed_pass");
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Create refresh token persists hashed token and returns raw secret")
    void testCreateRefreshToken() {
        String rawToken = refreshTokenService.createRefreshToken(testUser);

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        // Verify token is NOT stored in plain text
        assertTrue(refreshTokenRepository.findAll().stream()
                .anyMatch(t -> t.getUser().getId().equals(testUser.getId()) && !t.getTokenHash().equals(rawToken)));
    }

    @Test
    @DisplayName("Rotate refresh token revokes prior token and issues new valid pair")
    void testRotateRefreshTokenSuccess() {
        String initialToken = refreshTokenService.createRefreshToken(testUser);

        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(initialToken);

        assertNotNull(result);
        assertNotNull(result.accessToken());
        assertNotNull(result.refreshToken());
        assertNotEquals(initialToken, result.refreshToken());

        // Initial token must now be marked revoked
        assertThrows(ResponseStatusException.class, () -> refreshTokenService.rotateRefreshToken(initialToken));
    }

    @Test
    @DisplayName("Compromised token reuse detection triggers full user token family revocation")
    void testTokenReuseDetectionRevokesAllTokens() {
        String token1 = refreshTokenService.createRefreshToken(testUser);

        // Legitimate rotation (token1 -> token2)
        RefreshTokenService.RotationResult result1 = refreshTokenService.rotateRefreshToken(token1);
        String token2 = result1.refreshToken();

        // Attacker attempts to replay already-revoked token1
        assertThrows(ResponseStatusException.class, () -> refreshTokenService.rotateRefreshToken(token1));

        // Legitimate token2 should also now be revoked due to family reuse detection
        assertThrows(ResponseStatusException.class, () -> refreshTokenService.rotateRefreshToken(token2));
    }

    @Test
    @DisplayName("Revoke token marks refresh token revoked on logout")
    void testRevokeTokenOnLogout() {
        String token = refreshTokenService.createRefreshToken(testUser);

        refreshTokenService.revokeToken(token);

        assertThrows(ResponseStatusException.class, () -> refreshTokenService.rotateRefreshToken(token));
    }
}
