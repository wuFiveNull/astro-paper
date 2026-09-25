package com.astropaper.api.publiccontent.dto;

import java.time.Instant;
import java.util.List;

public record PostDetailDto(
    String slug,
    String title,
    String description,
    String author,
    Instant pubDatetime,
    Instant modDatetime,
    String timezone,
    List<String> tags,
    boolean featured,
    String coverImage,
    String canonicalURL,
    String ogImage,
    boolean hideEditPost,
    String contentMarkdown
) {
}
