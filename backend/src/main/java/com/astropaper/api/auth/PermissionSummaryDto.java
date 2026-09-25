package com.astropaper.api.auth;

import com.astropaper.api.domain.entity.PermissionEntity;

public record PermissionSummaryDto(String code, String description) {

    public static PermissionSummaryDto from(PermissionEntity permission) {
        return new PermissionSummaryDto(permission.getCode(), permission.getDescription());
    }
}
