package com.printguard.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

@Service
public class PasswordService {
    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final String SALT_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public String encode(CharSequence rawPassword) {
        // Generate a 22-character alphanumeric salt matching Django's format
        StringBuilder sb = new StringBuilder(22);
        for (int i = 0; i < 22; i++) {
            sb.append(SALT_CHARS.charAt(random.nextInt(SALT_CHARS.length())));
        }
        String salt = sb.toString();
        byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);

        byte[] hash = pbkdf2(rawPassword.toString().toCharArray(), saltBytes, DEFAULT_ITERATIONS, KEY_LENGTH);
        String hashBase64 = Base64.getEncoder().encodeToString(hash);

        return "pbkdf2_sha256$" + DEFAULT_ITERATIONS + "$" + salt + "$" + hashBase64;
    }

    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }

        String raw = rawPassword.toString();

        // 1. Check Django PBKDF2 format: pbkdf2_sha256$<iter>$<salt>$<hash>
        if (encodedPassword.startsWith("pbkdf2_sha256$")) {
            String[] parts = encodedPassword.split("\\$");
            if (parts.length == 4) {
                try {
                    int iterations = Integer.parseInt(parts[1]);
                    byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
                    int keyLength = expectedHash.length * 8;

                    // 1a. Django standard format where salt is UTF-8 string bytes
                    byte[] djangoSalt = parts[2].getBytes(StandardCharsets.UTF_8);
                    byte[] actualHash = pbkdf2(raw.toCharArray(), djangoSalt, iterations, keyLength);
                    if (MessageDigest.isEqual(expectedHash, actualHash)) {
                        return true;
                    }

                    // 1b. Fallback: salt treated as base64-encoded bytes
                    try {
                        byte[] b64Salt = Base64.getDecoder().decode(parts[2]);
                        byte[] b64ActualHash = pbkdf2(raw.toCharArray(), b64Salt, iterations, keyLength);
                        if (MessageDigest.isEqual(expectedHash, b64ActualHash)) {
                            return true;
                        }
                    } catch (Exception ignored) {}
                } catch (Exception e) {
                    log.warn("PBKDF2 verification error: {}", e.getMessage());
                }
            }
        }

        // 2. Check BCrypt format ($2a$, $2b$, $2y$)
        if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
            try {
                if (bcrypt.matches(raw, encodedPassword)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // 3. Fallback: Direct plaintext match for local development / demo legacy users
        if (raw.equals(encodedPassword)) {
            log.info("Matched password via plaintext fallback for user authentication.");
            return true;
        }

        return false;
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to calculate PBKDF2 hash", e);
        }
    }
}
