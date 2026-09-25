package com.astropaper.api.auth;

import com.astropaper.api.domain.entity.PermissionEntity;
import com.astropaper.api.domain.entity.RoleEntity;
import com.astropaper.api.domain.repository.PermissionRepository;
import com.astropaper.api.domain.repository.RoleRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

@Service
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionChecker permissionChecker;

    public RoleManagementService(
        RoleRepository roleRepository,
        PermissionRepository permissionRepository,
        PermissionChecker permissionChecker
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionChecker.isAdministrator(authentication) and @permissionChecker.has(authentication, 'role:manage')")
    public List<RoleSummaryDto> listRoles() {
        return roleRepository.findAllByOrderByCodeAsc().stream().map(RoleSummaryDto::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionChecker.isAdministrator(authentication) and @permissionChecker.has(authentication, 'permission:manage')")
    public List<PermissionSummaryDto> listPermissions() {
        return permissionRepository.findAllByOrderByCodeAsc().stream().map(PermissionSummaryDto::from).toList();
    }

    @Transactional
    @PreAuthorize("@permissionChecker.isAdministrator(authentication) and @permissionChecker.has(authentication, 'permission:manage')")
    public RoleSummaryDto replacePermissions(String roleCode, PermissionAssignmentRequest request) {
        String normalizedCode = roleCode.trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(normalizedCode)) {
            throw new InvalidAccessConfigurationException("The ADMIN role must keep all permissions and cannot be edited.");
        }
        RoleEntity role = roleRepository.findByCode(normalizedCode)
            .orElseThrow(() -> new AdminResourceNotFoundException("The requested role does not exist."));
        TreeSet<String> codes = request.permissionCodes().stream()
            .map(code -> code.trim().toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        if (List.of("user:manage", "role:manage", "permission:manage").stream().anyMatch(codes::contains)) {
            throw new InvalidAccessConfigurationException("Account and access administration permissions are reserved for the ADMIN role.");
        }
        List<PermissionEntity> permissions = codes.isEmpty()
            ? List.of()
            : permissionRepository.findAllByCodeIn(codes);
        if (permissions.size() != codes.size()) {
            throw new UnknownAccessCodeException("One or more requested permission codes do not exist.");
        }
        role.replacePermissions(permissions);
        return RoleSummaryDto.from(roleRepository.saveAndFlush(role));
    }
}
