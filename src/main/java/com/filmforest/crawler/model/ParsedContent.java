package com.filmforest.crawler.model;

import com.filmforest.common.type.ContentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ParsedContent(
        String externalId,
        ContentType contentType,
        String sourceUrl,
        String title,
        String sourcePosterUrl,
        Integer year,
        List<String> regions,
        List<String> genres,
        List<String> directors,
        List<String> writers,
        List<String> actors,
        List<String> languages,
        Integer durationMinutes,
        LocalDate releaseDate,
        String rawReleaseDate,
        List<String> aliases,
        BigDecimal doubanScore,
        BigDecimal imdbScore,
        BigDecimal rottenTomatoesScore,
        String storyline,
        Integer totalEpisodes,
        List<ParsedResource> resources,
        ParseDiagnostics diagnostics
) {
    public boolean valid() {
        return diagnostics.missingRequiredFields().isEmpty();
    }

    public ParsedContent withResources(List<ParsedResource> nextResources) {
        return new ParsedContent(externalId, contentType, sourceUrl, title, sourcePosterUrl, year,
                regions, genres, directors, writers, actors, languages, durationMinutes,
                releaseDate, rawReleaseDate, aliases, doubanScore, imdbScore,
                rottenTomatoesScore, storyline, totalEpisodes, List.copyOf(nextResources), diagnostics);
    }
}
