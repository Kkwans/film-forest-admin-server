package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.model.ParseDiagnostics;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.SourceListItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFingerprintTest {

    @Test
    void listFingerprintIsStableAndChangesWithSemanticFields() {
        SourceListItem original = new SourceListItem("42", "https://source.test/mv/42.html",
                "片名", "https://source.test/42.jpg", 0);
        SourceListItem copy = new SourceListItem("42", "https://source.test/mv/42.html",
                "片名", "https://source.test/42.jpg", 0);
        SourceListItem changed = new SourceListItem("42", "https://source.test/mv/42.html",
                "新片名", "https://source.test/42.jpg", 0);

        assertThat(SourceFingerprint.forListItem(original))
                .hasSize(64)
                .isEqualTo(SourceFingerprint.forListItem(copy))
                .isNotEqualTo(SourceFingerprint.forListItem(changed));
    }

    @Test
    void detailFingerprintNormalizesEquivalentDecimalScores() {
        ParsedContent first = content(new BigDecimal("8.50"), "剧情");
        ParsedContent equivalent = content(new BigDecimal("8.5"), "剧情");
        ParsedContent changed = content(new BigDecimal("8.5"), "科幻");

        assertThat(SourceFingerprint.forDetail(first))
                .isEqualTo(SourceFingerprint.forDetail(equivalent))
                .isNotEqualTo(SourceFingerprint.forDetail(changed));
    }

    @Test
    void canonicalFingerprintNormalizesWidthCasePunctuationAndTrailingYear() {
        String first = SourceFingerprint.forCanonicalContent(
                ContentType.MOVIE, "示例电影：Forest (2026)", 2026);
        String equivalent = SourceFingerprint.forCanonicalContent(
                ContentType.MOVIE, "示例电影 Forest（2026）", 2026);

        assertThat(SourceFingerprint.normalizeTitle("示例电影：Forest (2026)"))
                .isEqualTo("示例电影forest");
        assertThat(first).isEqualTo(equivalent).hasSize(64);
        assertThat(SourceFingerprint.normalizeTitle("2026")).isEqualTo("2026");
    }

    private static ParsedContent content(BigDecimal score, String genre) {
        return new ParsedContent("42", ContentType.MOVIE, "https://source.test/mv/42.html",
                "片名", null, 2026, List.of("中国"), List.of(genre), List.of(),
                List.of(), List.of(), List.of(), null, null, null, List.of(), score,
                null, null, "简介", null, List.of(),
                new ParseDiagnostics(List.of("h1"), List.of(), List.of(), "page", Map.of()));
    }
}
