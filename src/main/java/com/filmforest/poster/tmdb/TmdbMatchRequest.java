package com.filmforest.poster.tmdb;

import com.filmforest.common.type.ContentType;

import java.util.List;

public record TmdbMatchRequest(
        ContentType contentType,
        String title,
        List<String> aliases,
        Integer year
) {
    public TmdbMatchRequest {
        if (contentType == null) throw new IllegalArgumentException("Content type is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
