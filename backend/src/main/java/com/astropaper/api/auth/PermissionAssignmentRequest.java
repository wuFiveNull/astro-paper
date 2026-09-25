package com.astropaper.api.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PermissionAssignmentRequest(
    @NotNull @Size(max = 100) List<@jakarta.validation.constraints.NotBlank @Size(max = 100) String> permissionCodes
) {
}
