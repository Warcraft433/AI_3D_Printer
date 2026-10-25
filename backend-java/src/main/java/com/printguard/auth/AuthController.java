package com.printguard.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

@Tag(name = "Authentication", description = "User registration, authentication, JWT token issuance, and sessions")
@RestController
@RequestMapping({"/api/auth", "/api/auth/"})
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(
            UserRepository userRepository,
            PasswordService passwordService,
            JwtService jwtService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthController(UserRepository userRepository, PasswordService passwordService, JwtService jwtService) {
        this(userRepository, passwordService, jwtService, null);
    }

    @Operation(summary = "User Login", description = "Authenticates user credentials and returns JWT access and refresh tokens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or account disabled")
    })
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
        String refreshToken = (refreshTokenService != null)
                ? refreshTokenService.createRefreshToken(user)
                : jwtService.generateToken(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        Boolean.TRUE.equals(user.getIsStaff()),
                        Boolean.TRUE.equals(user.getIsSuperuser())
                );

        java.util.Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail() != null ? user.getEmail() : "");
        userData.put("is_staff", Boolean.TRUE.equals(user.getIsStaff()));
        userData.put("date_joined", user.getDateJoined() != null ? user.getDateJoined().toString() : OffsetDateTime.now().toString());

        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("access", accessToken);
        res.put("refresh", refreshToken);
        res.put("user", userData);
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "Register User", description = "Creates a new user account with encrypted password hashing")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid registration parameters"),
            @ApiResponse(responseCode = "409", description = "Username already exists")
    })
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
        newUser.setEmail(email != null ? email : "");
        newUser.setFirstName("");
        newUser.setLastName("");
        newUser.setPassword(passwordService.encode(password));
        newUser.setIsActive(true);
        newUser.setIsStaff(false);
        newUser.setIsSuperuser(false);
        newUser.setDateJoined(OffsetDateTime.now());
        UserEntity saved = userRepository.save(newUser);

        String accessToken = jwtService.generateToken(saved);
        String refreshToken = (refreshTokenService != null)
                ? refreshTokenService.createRefreshToken(saved)
                : jwtService.generateToken(saved);

        java.util.Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("id", saved.getId());
        userData.put("username", saved.getUsername());
        userData.put("email", saved.getEmail() != null ? saved.getEmail() : "");
        userData.put("is_staff", false);
        userData.put("date_joined", saved.getDateJoined() != null ? saved.getDateJoined().toString() : OffsetDateTime.now().toString());

        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("id", saved.getId());
        res.put("username", saved.getUsername());
        res.put("email", saved.getEmail() != null ? saved.getEmail() : "");
        res.put("access", accessToken);
        res.put("refresh", refreshToken);
        res.put("user", userData);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "Get Current User Profile", description = "Retrieves the user profile and role claims of the authenticated user")
    @GetMapping({"/me", "/me/"})
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        UserEntity user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        java.util.Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("email", user.getEmail() != null ? user.getEmail() : "");
        userData.put("first_name", user.getFirstName() != null ? user.getFirstName() : "");
        userData.put("last_name", user.getLastName() != null ? user.getLastName() : "");
        userData.put("is_staff", Boolean.TRUE.equals(user.getIsStaff()));
        userData.put("is_superuser", Boolean.TRUE.equals(user.getIsSuperuser()));
        userData.put("date_joined", user.getDateJoined() != null ? user.getDateJoined().toString() : OffsetDateTime.now().toString());
        return ResponseEntity.ok(userData);
    }

    @Operation(summary = "Refresh Access Token", description = "Issues a refreshed JWT access token with Refresh Token Rotation")
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

        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing refresh token");
        }

        // 1. Try persistent Refresh Token Rotation
        if (refreshTokenService != null) {
            try {
                RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(token);
                return ResponseEntity.ok(Map.of(
                        "access", result.accessToken(),
                        "refresh", result.refreshToken()
                ));
            } catch (ResponseStatusException e) {
                // If it is a stateless JWT fallback token, fall through to JWT validation
                if (!jwtService.validateToken(token)) {
                    throw e;
                }
            }
        }

        // 2. Stateless JWT fallback validation
        if (!jwtService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        Long userId = jwtService.extractUserId(token);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        String newAccess = jwtService.generateToken(user);
        return ResponseEntity.ok(Map.of("access", newAccess, "refresh", token));
    }

    @Operation(summary = "Logout User", description = "Revokes refresh token and terminates authenticated session")
    @PostMapping({"/logout", "/logout/"})
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (body != null && body.containsKey("refresh")) {
            token = body.get("refresh");
        }
        if (refreshTokenService != null && token != null) {
            refreshTokenService.revokeToken(token);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
