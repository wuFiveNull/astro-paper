package com.astropaper.api.auth;

import com.astropaper.api.domain.entity.RoleEntity;
import com.astropaper.api.domain.entity.UserEntity;
import com.astropaper.api.domain.repository.RoleRepository;
import com.astropaper.api.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AdminBootstrapService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean createFirstAdmin(String username, String email, String displayName, String password) {
        if (userRepository.count() > 0) return false;
        validate(username, email, displayName, password);
        RoleEntity adminRole = roleRepository.findByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("The ADMIN role is missing from the database."));
        UserEntity admin = new UserEntity(
            username.trim(),
            email.trim().toLowerCase(Locale.ROOT),
            passwordEncoder.encode(password),
            displayName.trim(),
            "ACTIVE"
        );
        admin.addRole(adminRole);
        userRepository.saveAndFlush(admin);
        return true;
    }

    private void validate(String username, String email, String displayName, String password) {
        if (username == null || !username.trim().matches("[A-Za-z0-9_.-]{3,50}")) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_USERNAME must be 3-50 letters, numbers, dots, underscores, or hyphens.");
        }
        if (email == null || email.isBlank() || email.trim().length() > 190 || !email.contains("@")) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL must be a valid email address.");
        }
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 100) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_DISPLAY_NAME is required and must not exceed 100 characters.");
        }
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD must be at least 12 characters and at most 72 UTF-8 bytes.");
        }
    }
}
