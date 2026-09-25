package com.astropaper.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoleAssignmentRequest(
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 50) String> roleCodes
) {
}
