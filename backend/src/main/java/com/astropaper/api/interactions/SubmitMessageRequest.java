package com.astropaper.api.interactions;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record SubmitMessageRequest(
    @Size(max = 200) String subject,
    @NotBlank @Size(max = 10000) String body
) {
}
