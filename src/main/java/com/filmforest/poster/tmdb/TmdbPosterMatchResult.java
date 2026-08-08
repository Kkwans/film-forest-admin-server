package com.filmforest.poster.tmdb;

import java.math.BigDecimal;
import java.util.Map;

public record TmdbPosterMatchResult(
        Status status,
        TmdbSearchCandidate candidate,
        TmdbPosterAsset poster,
        TmdbImageConfiguration imageConfiguration,
        BigDecimal confidence,
        Map<String, Object> diagnostics
) {
    public enum Status {
        PENDING,
        ACCEPTED,
        NOT_FOUND
    }
}
