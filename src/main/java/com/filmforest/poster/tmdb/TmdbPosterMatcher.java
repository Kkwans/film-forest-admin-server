package com.filmforest.poster.tmdb;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class TmdbPosterMatcher {

    static final BigDecimal AUTO_ACCEPT_THRESHOLD = new BigDecimal("0.8500");

    private final TmdbGateway gateway;

    public TmdbPosterMatcher(TmdbGateway gateway) {
        this.gateway = gateway;
    }

    public TmdbPosterMatchResult match(TmdbMatchRequest request, TmdbCredential credential) {
        TmdbMediaType mediaType = TmdbMediaType.forContent(request.contentType());
        List<TmdbSearchCandidate> candidates = gateway.search(mediaType, request.title(),
                request.year(), credential);
        if (candidates.isEmpty()) {
            return result(TmdbPosterMatchResult.Status.NOT_FOUND, null, null, null,
                    BigDecimal.ZERO, Map.of("reason", "noCandidate"));
        }

        List<String> sourceTitles = new ArrayList<>();
        sourceTitles.add(request.title());
        sourceTitles.addAll(request.aliases());
        ScoredCandidate best = candidates.stream()
                .map(candidate -> score(candidate, mediaType, sourceTitles, request.year()))
                .max(Comparator.comparing(ScoredCandidate::score))
                .orElseThrow();

        Map<String, Object> diagnostics = diagnostics(best, request.year());
        if (best.score().compareTo(AUTO_ACCEPT_THRESHOLD) < 0) {
            return result(TmdbPosterMatchResult.Status.PENDING, best.candidate(), null, null,
                    best.score(), diagnostics);
        }

        List<TmdbPosterAsset> posters = gateway.posters(mediaType, best.candidate().id(), credential);
        TmdbPosterAsset poster = choosePoster(posters);
        if (poster == null && best.candidate().posterPath() != null) {
            poster = new TmdbPosterAsset(best.candidate().posterPath(), null, 0D, 0, 0, 0);
        }
        if (poster == null) {
            Map<String, Object> withoutPoster = new LinkedHashMap<>(diagnostics);
            withoutPoster.put("reason", "matchedWithoutPoster");
            return result(TmdbPosterMatchResult.Status.NOT_FOUND, best.candidate(), null, null,
                    best.score(), Map.copyOf(withoutPoster));
        }

        TmdbImageConfiguration configuration = gateway.configuration(credential);
        Map<String, Object> accepted = new LinkedHashMap<>(diagnostics);
        accepted.put("posterLanguage", poster.language() == null ? "null" : poster.language());
        accepted.put("posterSize", configuration.preferredPosterSize());
        return result(TmdbPosterMatchResult.Status.ACCEPTED, best.candidate(), poster, configuration,
                best.score(), Map.copyOf(accepted));
    }

    private static ScoredCandidate score(TmdbSearchCandidate candidate, TmdbMediaType expectedType,
                                         List<String> sourceTitles, Integer sourceYear) {
        double titleScore = titleScore(candidate, sourceTitles);
        double yearScore = yearScore(sourceYear, candidate.year());
        double typeScore = candidate.mediaType() == expectedType ? 0.15D : 0D;
        BigDecimal score = BigDecimal.valueOf(Math.min(1D, titleScore + yearScore + typeScore))
                .setScale(4, RoundingMode.HALF_UP);
        return new ScoredCandidate(candidate, score, titleScore, yearScore, typeScore);
    }

    private static double titleScore(TmdbSearchCandidate candidate, List<String> sourceTitles) {
        List<String> normalizedSource = sourceTitles.stream().map(TmdbPosterMatcher::normalizeTitle)
                .filter(value -> !value.isBlank()).toList();
        for (String candidateTitle : Stream.of(candidate.title(), candidate.originalTitle()).toList()) {
            String normalizedCandidate = normalizeTitle(candidateTitle);
            if (normalizedCandidate.isBlank()) continue;
            if (normalizedSource.contains(normalizedCandidate)) return 0.60D;
        }
        for (String candidateTitle : Stream.of(candidate.title(), candidate.originalTitle()).toList()) {
            String normalizedCandidate = normalizeTitle(candidateTitle);
            if (normalizedCandidate.isBlank()) continue;
            if (normalizedSource.stream().anyMatch(value -> value.contains(normalizedCandidate)
                    || normalizedCandidate.contains(value))) return 0.35D;
        }
        return 0D;
    }

    private static double yearScore(Integer sourceYear, Integer candidateYear) {
        if (sourceYear == null || candidateYear == null) return 0.05D;
        int difference = Math.abs(sourceYear - candidateYear);
        if (difference == 0) return 0.25D;
        return difference == 1 ? 0.15D : 0D;
    }

    static TmdbPosterAsset choosePoster(List<TmdbPosterAsset> posters) {
        return posters.stream()
                .filter(poster -> poster.filePath() != null && !poster.filePath().isBlank())
                .min(Comparator.comparingInt((TmdbPosterAsset poster) -> languageRank(poster.language()))
                        .thenComparing(Comparator.comparingDouble(TmdbPosterAsset::voteAverage).reversed())
                        .thenComparing(Comparator.comparingInt(TmdbPosterAsset::voteCount).reversed()))
                .orElse(null);
    }

    private static int languageRank(String language) {
        if ("zh".equalsIgnoreCase(language)) return 0;
        if ("en".equalsIgnoreCase(language)) return 1;
        if (language == null || language.isBlank()) return 2;
        return 3;
    }

    static String normalizeTitle(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[（(]?(?:19|20)\\d{2}[）)]?", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Map<String, Object> diagnostics(ScoredCandidate best, Integer sourceYear) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("candidateId", best.candidate().id());
        diagnostics.put("candidateTitle", best.candidate().title() == null
                ? "unknown" : best.candidate().title());
        diagnostics.put("sourceYear", sourceYear == null ? "unknown" : sourceYear);
        diagnostics.put("candidateYear", best.candidate().year() == null ? "unknown" : best.candidate().year());
        diagnostics.put("titleScore", best.titleScore());
        diagnostics.put("yearScore", best.yearScore());
        diagnostics.put("typeScore", best.typeScore());
        diagnostics.put("threshold", AUTO_ACCEPT_THRESHOLD);
        return Map.copyOf(diagnostics);
    }

    private static TmdbPosterMatchResult result(TmdbPosterMatchResult.Status status,
                                                TmdbSearchCandidate candidate,
                                                TmdbPosterAsset poster,
                                                TmdbImageConfiguration configuration,
                                                BigDecimal confidence,
                                                Map<String, Object> diagnostics) {
        return new TmdbPosterMatchResult(status, candidate, poster, configuration,
                confidence.setScale(4, RoundingMode.HALF_UP), diagnostics);
    }

    private record ScoredCandidate(TmdbSearchCandidate candidate, BigDecimal score,
                                   double titleScore, double yearScore, double typeScore) { }
}
