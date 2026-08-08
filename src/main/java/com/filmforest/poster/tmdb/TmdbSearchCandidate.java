package com.filmforest.poster.tmdb;

public record TmdbSearchCandidate(
        long id,
        TmdbMediaType mediaType,
        String title,
        String originalTitle,
        Integer year,
        String posterPath,
        String originalLanguage
) {
}
