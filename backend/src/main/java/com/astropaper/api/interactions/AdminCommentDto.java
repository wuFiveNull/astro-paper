package com.astropaper.api.interactions;

import java.time.Instant;

public record AdminCommentDto(
    Long id,
    String postSlug,
    String postTitle,
    Long parentId,
    String username,
    String displayName,
    String body,
    String status,
    Instant createdAt
) {
}
