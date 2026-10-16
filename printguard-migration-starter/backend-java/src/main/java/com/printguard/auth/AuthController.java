package com.printguard.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping({"/api/auth", "/api/auth/"})
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordService passwordService, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    @PostMapping({"/login", "/login/"})
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.getOrDefault("username", "").trim();
        String password = credentials.getOrDefault("password", "");

        if (username.isBlank() || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordService.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is disabled");
        }

        user.setLastLogin(OffsetDateTime.now());
        userRepository.save(user);

        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getIsStaff()),
                Boolean.TRUE.equals(user.getIsSuperuser())
        );

        Map<String, Object> userData = Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "is_staff", Boolean.TRUE.equals(user.getIsStaff()),
            "date_joined", user.getDateJoined().toString()
        );

        return ResponseEntity.ok(Map.of(
            "access", accessToken,
            "refresh", refreshToken,
            "user", userData
        ));
    }

    @PostMapping({"/register", "/register/"})
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        if (username.isBlank() || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and valid password (min 6 characters) required");
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        UserEntity newUser = new UserEntity();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setPassword(passwordService.encode(password));
        newUser.setIsActive(true);
        newUser.setIsStaff(false);
        newUser.setIsSuperuser(false);
        newUser.setDateJoined(OffsetDateTime.now());
        UserEntity saved = userRepository.save(newUser);

        String accessToken = jwtService.generateToken(saved);
        String refreshToken = jwtService.generateToken(saved);

        Map<String, Object> userData = Map.of(
            "id", saved.getId(),
            "username", saved.getUsername(),
            "email", saved.getEmail(),
            "is_staff", false,
            "date_joined", saved.getDateJoined().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "id", saved.getId(),
            "username", saved.getUsername(),
            "email", saved.getEmail(),
            "access", accessToken,
            "refresh", refreshToken,
            "user", userData
        ));
    }

    @GetMapping({"/me", "/me/"})
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        UserEntity user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "is_staff", Boolean.TRUE.equals(user.getIsStaff()),
            "date_joined", user.getDateJoined().toString()
        ));
    }

    @PostMapping({"/refresh", "/refresh/"})
    public ResponseEntity<Map<String, String>> refresh(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body) {

        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (body != null && body.containsKey("refresh")) {
            token = body.get("refresh");
        }

        if (token == null || !jwtService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        Long userId = jwtService.extractUserId(token);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        String newAccess = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of("access", newAccess));
    }
}
