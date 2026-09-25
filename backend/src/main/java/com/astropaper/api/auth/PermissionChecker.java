package com.astropaper.api.auth;

import com.astropaper.api.domain.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class PermissionChecker {

    private final UserRepository userRepository;

    public PermissionChecker(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean has(Authentication authentication, String permissionCode) {
        return currentUser(authentication)
            .filter(user -> user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permissionCode.equals(permission.getCode())))
            .isPresent();
    }

    public boolean isAdministrator(Authentication authentication) {
        return currentUser(authentication)
            .filter(user -> user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode())))
            .isPresent();
    }

    private java.util.Optional<com.astropaper.api.domain.entity.UserEntity> currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return java.util.Optional.empty();
        if (!(authentication.getPrincipal() instanceof BlogUserPrincipal principal)) return java.util.Optional.empty();
        return userRepository.findByIdWithAuthorization(principal.getId())
            .filter(user -> "ACTIVE".equals(user.getStatus()));
    }
}
