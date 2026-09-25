package com.astropaper.api.auth;

import com.astropaper.api.domain.entity.RoleEntity;

import java.util.List;
import java.util.TreeSet;

public record RoleSummaryDto(String code, String name, String description, List<String> permissions) {

    public static RoleSummaryDto from(RoleEntity role) {
        TreeSet<String> permissions = new TreeSet<>();
        role.getPermissions().forEach(permission -> permissions.add(permission.getCode()));
        return new RoleSummaryDto(role.getCode(), role.getName(), role.getDescription(), List.copyOf(permissions));
    }
}
