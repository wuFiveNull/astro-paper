package com.astropaper.api.articles;

import java.time.Instant;
import java.util.List;

public record AdminPostDetailDto(
    Long id,
    String slug,
    String title,
    String description,
    String contentMarkdown,
    String coverImage,
    String status,
    Long authorId,
    String authorName,
    Instant publishedAt,
    Instant modifiedAt,
    String timezone,
    boolean featured,
    String canonicalURL,
    String ogImage,
    boolean hideEditPost,
    Instant createdAt,
    Instant updatedAt,
    List<AdminTagDto> tags
) {
}
