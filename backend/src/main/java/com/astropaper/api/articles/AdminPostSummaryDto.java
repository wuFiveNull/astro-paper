package com.astropaper.api.articles;

import java.time.Instant;
import java.util.List;

public record AdminPostSummaryDto(
    Long id,
    String slug,
    String title,
    String description,
    String status,
    Long authorId,
    String authorName,
    Instant publishedAt,
    Instant modifiedAt,
    Instant updatedAt,
    List<AdminTagDto> tags
) {
}
