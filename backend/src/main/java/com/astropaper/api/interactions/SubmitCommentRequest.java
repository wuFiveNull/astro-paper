package com.astropaper.api.interactions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubmitCommentRequest(
    @NotBlank @Size(max = 180) String postSlug,
    @NotBlank @Size(max = 5000) String body,
    @Positive Long parentId
) {
}
