package com.filmforest.poster.tmdb;

public record TmdbSearchCandidate(
        long id,
        TmdbMediaType mediaType,
        String title,
        String originalTitle,
        Integer year,
        String posterPath,
        String originalLanguage,
        Double voteAverage,
        Integer voteCount
) {
    public TmdbSearchCandidate(long id, TmdbMediaType mediaType, String title,
                               String originalTitle, Integer year, String posterPath,
                               String originalLanguage) {
        this(id, mediaType, title, originalTitle, year, posterPath, originalLanguage,
                null, null);
    }
}
