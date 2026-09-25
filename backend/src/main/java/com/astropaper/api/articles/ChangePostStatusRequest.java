package com.astropaper.api.articles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePostStatusRequest(
    @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") String status
) {
}
