package com.astropaper.api.publiccontent.dto;

import java.util.List;

public record PageResponseDto<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
}
