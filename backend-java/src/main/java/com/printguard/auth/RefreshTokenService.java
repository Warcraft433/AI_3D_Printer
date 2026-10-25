package com.printguard.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Service managing Refresh Token Rotation, token family tracking,
 * and reuse detection (automatic revocation if a compromised token is reused).
 */
@Service
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long refreshTokenValidityDays;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            @Value("${printguard.jwt.refresh-expiration-days:7}") long refreshTokenValidityDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenValidityDays = refreshTokenValidityDays > 0 ? refreshTokenValidityDays : 7;
    }

    /**
     * Creates a new cryptographically random refresh token for the user.
     */
    @Transactional
    public String createRefreshToken(UserEntity user) {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String tokenHash = hashToken(rawToken);

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUser(user);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(OffsetDateTime.now().plusDays(refreshTokenValidityDays));
        entity.setRevoked(false);

        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /**
     * Rotates a refresh token: revokes the current one and issues a new pair of access + refresh tokens.
     * If an already revoked token is used, revokes all tokens for that user (token theft defense).
     */
    @Transactional
    public RotationResult rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token required");
        }

        String hash = hashToken(rawRefreshToken.trim());
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenHash(hash)
                .orElse(null);

        if (existing == null) {
            log.warn("Attempt to rotate non-existent refresh token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        if (existing.isRevoked()) {
            log.warn("CRITICAL SECURITY ALERT: Revoked refresh token reuse detected for user {}. Revoking all user tokens.",
                    existing.getUser().getUsername());
            refreshTokenRepository.revokeAllByUserId(existing.getUser().getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Security alert: token reuse detected. Please log in again.");
        }

        if (existing.getExpiresAt().isBefore(OffsetDateTime.now())) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        // Mark old token as revoked (single-use rotation policy)
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        UserEntity user = existing.getUser();
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = createRefreshToken(user);

        return new RotationResult(newAccessToken, newRefreshToken, user);
    }

    /**
     * Revokes a refresh token (e.g., during logout).
     */
    @Transactional
    public void revokeToken(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String hash = hashToken(rawRefreshToken.trim());
            refreshTokenRepository.findByTokenHash(hash).ifPresent(entity -> {
                entity.setRevoked(true);
                refreshTokenRepository.save(entity);
            });
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RotationResult(String accessToken, String refreshToken, UserEntity user) {}
}
