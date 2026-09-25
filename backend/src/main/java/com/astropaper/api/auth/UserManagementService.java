package com.astropaper.api.auth;

import com.astropaper.api.domain.entity.RoleEntity;
import com.astropaper.api.domain.entity.UserEntity;
import com.astropaper.api.domain.repository.RoleRepository;
import com.astropaper.api.domain.repository.UserRepository;
import com.astropaper.api.publiccontent.dto.PageResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionChecker permissionChecker;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogWriter auditLogWriter;

    public UserManagementService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        PermissionChecker permissionChecker,
        AuditLogWriter auditLogWriter
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionChecker = permissionChecker;
        this.auditLogWriter = auditLogWriter;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionChecker.isAdministrator(authentication) and @permissionChecker.has(authentication, 'user:manage')")
    public PageResponseDto<UserSummaryDto> listUsers(int page, int size) {
        Page<UserEntity> results = userRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "username"))
        );
        Page<UserSummaryDto> mapped = results.map(UserSummaryDto::from);
        return new PageResponseDto<>(
            mapped.getContent(),
            mapped.getNumber(),
            mapped.getSize(),
            mapped.getTotalElements(),
            mapped.getTotalPages(),
            mapped.isFirst(),
            mapped.isLast()
        );
    }

    @Transactional
    @PreAuthorize("@permissionChecker.isAdministrator(authentication) and @permissionChecker.has(authentication, 'user:manage')")
    public UserSummaryDto createUser(CreateUserRequest request, Authentication actor) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException();
        }
        ensureBcryptInputLength(request.password());
        RoleEntity userRole = roleRepository.findByCode("USER")
            .orElseThrow(() -> new IllegalStateException("The USER role is missing from the database."));
        UserEntity user = new UserEntity(
            username,
            email,
            passwordEncoder.encode(request.password()),
            request.displayName().trim(),
            "ACTIVE"
        );
        user.addRole(userRole);
        UserEntity saved = userRepository.saveAndFlush(user);
        auditLogWriter.record(actor, "USER_CREATED", "USER", saved.getId(), Map.of(
            "username", saved.getUsername(),
            "status", saved.getStatus()
        ));
        return UserSummaryDto.from(saved);
    }

    @Transactional
    @PreAuthorize("@permissionChecker.isAdministrator(authentication) and @permissionChecker.has(authentication, 'role:manage')")
    public UserSummaryDto assignRoles(Long userId, RoleAssignmentRequest request, Authentication actor) {
        roleRepository.lockByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("The ADMIN role is missing from the database."));
        var roleCodes = request.roleCodes().stream()
            .map(code -> code.trim().toUpperCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        if (roleCodes.contains("ADMIN") && !permissionChecker.has(actor, "permission:manage")) {
            throw new ForbiddenRoleChangeException();
        }

        UserEntity user = userRepository.findByIdWithAuthorization(userId)
            .orElseThrow(() -> new AdminResourceNotFoundException("The requested account does not exist."));
        boolean losesLastActiveAdmin = "ACTIVE".equals(user.getStatus())
            && user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()))
            && !roleCodes.contains("ADMIN")
            && userRepository.countActiveAdministrators() <= 1;
        if (losesLastActiveAdmin) throw new LastAdministratorException();

        var roles = roleRepository.findAllByCodeIn(roleCodes);
        if (roles.size() != roleCodes.size()) throw new UnknownAccessCodeException("One or more requested role codes do not exist.");
        List<String> previousRoles = user.getRoles().stream().map(RoleEntity::getCode).sorted().toList();
        user.replaceRoles(roles);
        UserEntity saved = userRepository.saveAndFlush(user);
        auditLogWriter.record(actor, "USER_ROLES_CHANGED", "USER", saved.getId(), Map.of(
            "username", saved.getUsername(),
            "before", previousRoles,
            "after", roleCodes.stream().toList()
        ));
        return UserSummaryDto.from(saved);
    }

    @Transactional
    @PreAuthorize("@permissionChecker.isAdministrator(authentication) and @permissionChecker.has(authentication, 'user:manage')")
    public UserSummaryDto changeUserStatus(Long userId, ChangeUserStatusRequest request, Authentication actor) {
        roleRepository.lockByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("The ADMIN role is missing from the database."));
        UserEntity user = userRepository.findByIdWithAuthorization(userId)
            .orElseThrow(() -> new AdminResourceNotFoundException("The requested account does not exist."));
        String previousStatus = user.getStatus();
        boolean losesLastActiveAdmin = "ACTIVE".equals(user.getStatus())
            && !"ACTIVE".equals(request.status())
            && user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getCode()))
            && userRepository.countActiveAdministrators() <= 1;
        if (losesLastActiveAdmin) throw new LastAdministratorException();
        user.changeStatus(request.status());
        UserEntity saved = userRepository.saveAndFlush(user);
        auditLogWriter.record(actor, "USER_STATUS_CHANGED", "USER", saved.getId(), Map.of(
            "username", saved.getUsername(),
            "before", previousStatus,
            "after", saved.getStatus()
        ));
        return UserSummaryDto.from(saved);
    }

    static void ensureBcryptInputLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
        }
    }
}
