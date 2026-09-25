package com.astropaper.api.articles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record ManagePostRequest(
    @NotBlank @Size(max = 180) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9/_-]*$") String slug,
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 500) String description,
    @NotBlank @Size(max = 10_000_000) String contentMarkdown,
    @Size(max = 2048) String coverImage,
    @Size(max = 100) String authorName,
    @Size(max = 64) String timezone,
    Boolean featured,
    @Size(max = 2048) String canonicalURL,
    @Size(max = 2048) String ogImage,
    Boolean hideEditPost,
    @Size(max = 25) List<@NotBlank @Size(max = 100) String> tags
) {

    public ManagePostRequest {
        tags = tags == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(tags));
    }
}
