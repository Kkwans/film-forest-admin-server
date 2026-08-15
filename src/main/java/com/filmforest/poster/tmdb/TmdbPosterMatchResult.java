package com.filmforest.poster.tmdb;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public record TmdbPosterMatchResult(
        Status status,
        TmdbSearchCandidate candidate,
        TmdbPosterAsset poster,
        TmdbImageConfiguration imageConfiguration,
        BigDecimal confidence,
        Map<String, Object> diagnostics
) {
    /** TMDB content vote_average, never the poster image vote fields. */
    public BigDecimal tmdbScore() {
        return candidate == null || candidate.voteAverage() == null ? null
                : BigDecimal.valueOf(candidate.voteAverage()).setScale(1, RoundingMode.HALF_UP);
    }

    /** TMDB content vote_count, never inferred from another rating source. */
    public Integer tmdbVoteCount() {
        return candidate == null ? null : candidate.voteCount();
    }

    public enum Status {
        PENDING,
        ACCEPTED,
        NOT_FOUND
    }
}
