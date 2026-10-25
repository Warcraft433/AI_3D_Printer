package com.printguard.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {
    public static final String DEFAULT_DEV_SECRET = "PrintGuardAI-SuperSecretKey-ForJWTTokenSigning-2026-BTechFYP!";
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${printguard.jwt.secret:PrintGuardAI-SuperSecretKey-ForJWTTokenSigning-2026-BTechFYP!}") String secretKey,
            @Value("${printguard.jwt.expiration-ms:86400000}") long expirationMs,
            org.springframework.core.env.Environment env) {
        this.secretKey = secretKey;
        this.expirationMs = expirationMs;

        boolean isProduction = env != null && java.util.Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        if (isProduction && (secretKey == null || secretKey.isBlank() || DEFAULT_DEV_SECRET.equals(secretKey))) {
            throw new IllegalStateException("FATAL: In production profile, PRINTGUARD_JWT_SECRET must be configured with a secure non-default secret key.");
        }
    }

    public String generateToken(UserEntity user) {
        return generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getIsStaff()),
                Boolean.TRUE.equals(user.getIsSuperuser())
        );
    }

    public String generateToken(Long userId, String username, String email, boolean isStaff, boolean isSuperuser) {
        return generateTokenWithExpiration(userId, username, email, isStaff, isSuperuser, expirationMs);
    }

    public String generateTokenWithExpiration(Long userId, String username, String email, boolean isStaff, boolean isSuperuser, long customExpirationMs) {
        long now = System.currentTimeMillis();
        long exp = now + customExpirationMs;

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", username);
        claims.put("userId", userId);
        claims.put("email", email != null ? email : "");
        claims.put("isStaff", isStaff);
        claims.put("isSuperuser", isSuperuser);
        claims.put("iat", now / 1000L);
        claims.put("exp", exp / 1000L);

        try {
            String encodedHeader = base64UrlEncode(MAPPER.writeValueAsBytes(header));
            String encodedPayload = base64UrlEncode(MAPPER.writeValueAsBytes(claims));
            String dataToSign = encodedHeader + "." + encodedPayload;
            String signature = sign(dataToSign);

            return dataToSign + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT token", e);
        }
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        try {
            String dataToSign = parts[0] + "." + parts[1];
            String expectedSignature = sign(dataToSign);

            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            Map<String, Object> claims = extractClaims(token);
            if (claims == null) {
                return false;
            }

            Number exp = (Number) claims.get("exp");
            if (exp == null) {
                return false;
            }

            long expTimeMs = exp.longValue() * 1000L;
            return System.currentTimeMillis() < expTimeMs;
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> extractClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            return MAPPER.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    public String extractUsername(String token) {
        Map<String, Object> claims = extractClaims(token);
        return claims != null ? (String) claims.get("sub") : null;
    }

    public Long extractUserId(String token) {
        Map<String, Object> claims = extractClaims(token);
        if (claims == null) return null;
        Object userIdObj = claims.get("userId");
        if (userIdObj instanceof Number num) {
            return num.longValue();
        } else if (userIdObj instanceof String str) {
            return Long.parseLong(str);
        }
        return null;
    }

    public boolean isStaff(String token) {
        Map<String, Object> claims = extractClaims(token);
        if (claims == null) return false;
        return Boolean.TRUE.equals(claims.get("isStaff")) || Boolean.TRUE.equals(claims.get("isSuperuser"));
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(keySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(rawHmac);
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
