package com.printguard.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Component
public class SecurityUtils {
    private final UserRepository userRepository;

    public SecurityUtils(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserEntity> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return userRepository.findById(up.getId());
        }
        String username = auth.getName();
        if (username != null && !username.isBlank() && !"anonymousUser".equalsIgnoreCase(username)) {
            return userRepository.findByUsername(username);
        }
        return Optional.empty();
    }

    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    public UserEntity requireCurrentUser() {
        return getCurrentUser().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public boolean isAdmin(UserEntity user) {
        if (user == null) return false;
        return Boolean.TRUE.equals(user.getIsSuperuser()) || Boolean.TRUE.equals(user.getIsStaff());
    }

    public void verifyOwnershipOrAdmin(UserEntity resourceOwner, String actionDescription) {
        UserEntity current = requireCurrentUser();
        if (isAdmin(current)) {
            return;
        }
        if (resourceOwner == null || resourceOwner.getId() == null || !resourceOwner.getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: you do not have permission to " + actionDescription);
        }
    }
}
