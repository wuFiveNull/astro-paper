package com.astropaper.api.interactions;

import java.time.Instant;

public record AdminMessageDto(
    Long id,
    Long userId,
    String username,
    String senderName,
    String senderEmail,
    String subject,
    String body,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
