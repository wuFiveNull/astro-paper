package com.astropaper.api.articles;

import com.astropaper.api.domain.entity.TagEntity;

public record AdminTagDto(String slug, String name) {

    public static AdminTagDto from(TagEntity tag) {
        return new AdminTagDto(tag.getSlug(), tag.getName());
    }
}
