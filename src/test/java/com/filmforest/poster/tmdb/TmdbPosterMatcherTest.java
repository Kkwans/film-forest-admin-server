package com.filmforest.poster.tmdb;

import com.filmforest.common.type.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbPosterMatcherTest {

    private static final TmdbCredential CREDENTIAL =
            new TmdbCredential(TmdbCredential.Type.API_KEY, "fixture-api-key");

    @Test
    void exactTitleYearAndTypeSelectChinesePosterBeforeHigherRatedEnglishPoster() {
        FakeGateway gateway = new FakeGateway();
        gateway.candidates = List.of(new TmdbSearchCandidate(101, TmdbMediaType.TV,
                "示例剧集 第二季", "Example Series", 2024, "/fallback.jpg", "zh"));
        gateway.posters = List.of(
                new TmdbPosterAsset("/english.jpg", "en", 9.9, 100, 1000, 1500),
                new TmdbPosterAsset("/chinese.jpg", "zh", 7.0, 5, 1000, 1500),
                new TmdbPosterAsset("/neutral.jpg", null, 10.0, 200, 1000, 1500));

        var result = new TmdbPosterMatcher(gateway).match(
                new TmdbMatchRequest(ContentType.DRAMA, "示例剧集 第二季 (2024)",
                        List.of("Example Series"), 2024), CREDENTIAL);

        assertThat(result.status()).isEqualTo(TmdbPosterMatchResult.Status.ACCEPTED);
        assertThat(result.confidence()).isEqualByComparingTo("1.0000");
        assertThat(result.poster().filePath()).isEqualTo("/chinese.jpg");
        assertThat(result.imageConfiguration().imageUrl(result.poster().filePath()))
                .isEqualTo("https://image.tmdb.org/t/p/w500/chinese.jpg");
    }

    @Test
    void lowConfidenceCandidateRemainsPendingAndDoesNotFetchImages() {
        FakeGateway gateway = new FakeGateway();
        gateway.candidates = List.of(new TmdbSearchCandidate(202, TmdbMediaType.MOVIE,
                "完全不同", "Different", 2024, "/wrong.jpg", "zh"));

        var result = new TmdbPosterMatcher(gateway).match(
                new TmdbMatchRequest(ContentType.MOVIE, "目标电影", List.of(), 2024), CREDENTIAL);

        assertThat(result.status()).isEqualTo(TmdbPosterMatchResult.Status.PENDING);
        assertThat(result.poster()).isNull();
        assertThat(gateway.posterRequests).isZero();
        assertThat(gateway.configurationRequests).isZero();
    }

    @Test
    void credentialStringRepresentationNeverEchoesSecret() {
        TmdbCredential credential = new TmdbCredential(TmdbCredential.Type.API_KEY,
                "do-not-print-this");

        assertThat(credential.toString()).contains("REDACTED").doesNotContain("do-not-print-this");
    }

    private static final class FakeGateway implements TmdbGateway {
        private List<TmdbSearchCandidate> candidates = List.of();
        private List<TmdbPosterAsset> posters = List.of();
        private int posterRequests;
        private int configurationRequests;

        @Override
        public List<TmdbSearchCandidate> search(TmdbMediaType mediaType, String query, Integer year,
                                                TmdbCredential credential) {
            return candidates;
        }

        @Override
        public List<TmdbPosterAsset> posters(TmdbMediaType mediaType, long tmdbId,
                                             TmdbCredential credential) {
            posterRequests++;
            return posters;
        }

        @Override
        public TmdbImageConfiguration configuration(TmdbCredential credential) {
            configurationRequests++;
            return new TmdbImageConfiguration("https://image.tmdb.org/t/p/",
                    List.of("w342", "w500", "original"));
        }
    }
}
