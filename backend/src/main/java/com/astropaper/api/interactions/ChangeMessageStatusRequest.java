package com.astropaper.api.interactions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeMessageStatusRequest(
    @NotBlank @Pattern(regexp = "IN_PROGRESS|RESOLVED|SPAM") String status
) {
}
