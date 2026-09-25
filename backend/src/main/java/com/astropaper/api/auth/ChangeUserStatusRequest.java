package com.astropaper.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeUserStatusRequest(
    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED|LOCKED") String status
) {
}
