package com.filmforest.poster.tmdb;

public record TmdbCredential(Type type, String value) {

    public TmdbCredential {
        if (type == null) throw new IllegalArgumentException("TMDB credential type is required");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TMDB credential is required");
        value = value.trim();
    }

    @Override
    public String toString() {
        return "TmdbCredential[type=" + type + ", value=REDACTED]";
    }

    public enum Type {
        API_KEY,
        READ_ACCESS_TOKEN
    }
}
