package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.ParsedResource;
import com.filmforest.crawler.model.SourceListItem;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SourceFingerprint {

    private static final Pattern TRAILING_YEAR = Pattern.compile(
            "[\\s\\[【(（]*(?:19|20)\\d{2}[\\s\\]】)）]*$");
    private static final Pattern TITLE_SEPARATORS = Pattern.compile("[\\p{P}\\p{S}\\s]+");

    private SourceFingerprint() {
    }

    public static String forListItem(SourceListItem item) {
        Digest digest = new Digest();
        digest.add(item.externalId());
        digest.add(item.sourceUrl());
        digest.add(item.title());
        digest.add(item.posterUrl());
        digest.add(item.sourceOrder());
        return digest.hex();
    }

    public static String forDetail(ParsedContent content) {
        Digest digest = new Digest();
        digest.add(content.externalId());
        digest.add(content.contentType().value());
        digest.add(content.sourceUrl());
        digest.add(content.title());
        digest.add(content.sourcePosterUrl());
        digest.add(content.year());
        digest.addList(content.regions());
        digest.addList(content.genres());
        digest.addList(content.directors());
        digest.addList(content.writers());
        digest.addList(content.actors());
        digest.addList(content.languages());
        digest.add(content.durationMinutes());
        digest.add(content.releaseDate());
        digest.add(content.rawReleaseDate());
        digest.addList(content.aliases());
        digest.add(content.doubanScore());
        digest.add(content.imdbScore());
        digest.add(content.rottenTomatoesScore());
        digest.add(content.storyline());
        digest.add(content.totalEpisodes());
        List<ParsedResource> resources = content.resources() == null ? List.of() : content.resources();
        digest.add(resources.size());
        for (ParsedResource resource : resources) {
            digest.add(resource.kind().name());
            digest.add(resource.title());
            digest.add(resource.url());
            digest.add(resource.diskType());
            digest.add(resource.password());
            digest.add(resource.resolution());
            digest.add(resource.hasSubtitle());
            digest.add(resource.specialSubtitle());
            digest.add(resource.season());
            digest.add(resource.episodeNumber());
            digest.add(resource.episodeTitle());
            digest.add(resource.sourceOrder());
            digest.add(resource.rawText());
        }
        return digest.hex();
    }

    public static String forCanonicalContent(ContentType contentType, String title, Integer year) {
        Digest digest = new Digest();
        digest.add(contentType.value());
        digest.add(normalizeTitle(title));
        digest.add(year);
        return digest.hex();
    }

    public static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim();
        String withoutTrailingYear = TRAILING_YEAR.matcher(normalized).replaceFirst("");
        if (!withoutTrailingYear.isBlank()) {
            normalized = withoutTrailingYear;
        }
        normalized = TITLE_SEPARATORS.matcher(normalized).replaceAll("");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("title has no canonical characters");
        }
        return normalized;
    }

    private static final class Digest {
        private final MessageDigest delegate;

        private Digest() {
            try {
                delegate = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is unavailable", impossible);
            }
        }

        private void addList(List<String> values) {
            List<String> safe = values == null ? List.of() : values;
            add(safe.size());
            safe.forEach(this::add);
        }

        private void add(Object value) {
            String normalized = normalize(value);
            byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
            delegate.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            delegate.update(bytes);
        }

        private String hex() {
            return java.util.HexFormat.of().formatHex(delegate.digest());
        }

        private static String normalize(Object value) {
            if (value == null) return "";
            if (value instanceof BigDecimal decimal) {
                return decimal.stripTrailingZeros().toPlainString();
            }
            if (value instanceof TemporalAccessor temporal) return temporal.toString();
            return value.toString().trim();
        }
    }
}
