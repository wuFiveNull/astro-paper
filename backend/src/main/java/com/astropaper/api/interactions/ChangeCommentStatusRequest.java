package com.astropaper.api.interactions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeCommentStatusRequest(
    @NotBlank @Pattern(regexp = "PUBLISHED|REJECTED") String status
) {
}
