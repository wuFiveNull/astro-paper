package com.astropaper.api.auth;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccessController {

    private final UserManagementService userManagementService;
    private final RoleManagementService roleManagementService;

    public AdminAccessController(UserManagementService userManagementService, RoleManagementService roleManagementService) {
        this.userManagementService = userManagementService;
        this.roleManagementService = roleManagementService;
    }

    @PutMapping("/users/{userId}/roles")
    public UserSummaryDto assignRoles(
        @PathVariable Long userId,
        @Valid @RequestBody RoleAssignmentRequest request,
        Authentication authentication
    ) {
        return userManagementService.assignRoles(userId, request, authentication);
    }

    @PutMapping("/users/{userId}/status")
    public UserSummaryDto changeStatus(@PathVariable Long userId, @Valid @RequestBody ChangeUserStatusRequest request) {
        return userManagementService.changeUserStatus(userId, request);
    }

    @GetMapping("/roles")
    public List<RoleSummaryDto> roles() {
        return roleManagementService.listRoles();
    }

    @GetMapping("/permissions")
    public List<PermissionSummaryDto> permissions() {
        return roleManagementService.listPermissions();
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public RoleSummaryDto replacePermissions(
        @PathVariable String roleCode,
        @Valid @RequestBody PermissionAssignmentRequest request
    ) {
        return roleManagementService.replacePermissions(roleCode, request);
    }
}
