package com.filmforest.poster.tmdb;

public record TmdbPosterAsset(
        String filePath,
        String language,
        double voteAverage,
        int voteCount,
        int width,
        int height
) {
}
