package com.filmforest.common.type;

import java.util.Arrays;
import java.util.Optional;

public enum ContentType {
    MOVIE("movie"),
    DRAMA("drama"),
    VARIETY("variety"),
    ANIME("anime"),
    SHORT_DRAMA("short_drama");

    private final String value;

    ContentType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<ContentType> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst();
    }
}
