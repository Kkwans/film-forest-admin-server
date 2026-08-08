package com.filmforest.poster.tmdb;

import com.filmforest.common.type.ContentType;

public enum TmdbMediaType {
    MOVIE("movie"),
    TV("tv");

    private final String apiValue;

    TmdbMediaType(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static TmdbMediaType forContent(ContentType contentType) {
        return contentType == ContentType.MOVIE ? MOVIE : TV;
    }
}
