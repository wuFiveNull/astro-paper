package com.astropaper.api.interactions;

import java.time.Instant;

public record CommentDto(
    Long id,
    Long parentId,
    String authorName,
    String body,
    Instant createdAt
) {
}
