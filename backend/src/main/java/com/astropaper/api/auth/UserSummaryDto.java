package com.astropaper.api.auth;

import com.astropaper.api.domain.entity.RoleEntity;
import com.astropaper.api.domain.entity.UserEntity;

import java.util.List;
import java.util.TreeSet;

public record UserSummaryDto(
    Long id,
    String username,
    String email,
    String displayName,
    String status,
    List<String> roles,
    List<String> permissions
) {

    public static UserSummaryDto from(BlogUserPrincipal principal) {
        return new UserSummaryDto(
            principal.getId(),
            principal.getUsername(),
            principal.getEmail(),
            principal.getDisplayName(),
            principal.getStatus(),
            principal.getRoleCodes(),
            principal.getPermissionCodes()
        );
    }

    public static UserSummaryDto from(UserEntity user) {
        TreeSet<String> roles = new TreeSet<>();
        TreeSet<String> permissions = new TreeSet<>();
        for (RoleEntity role : user.getRoles()) {
            roles.add(role.getCode());
            role.getPermissions().forEach(permission -> permissions.add(permission.getCode()));
        }
        return new UserSummaryDto(
            user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getStatus(),
            List.copyOf(roles), List.copyOf(permissions)
        );
    }
}
