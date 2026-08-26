package com.filmforest.crawler.source.pkmp4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/** 解析七味网公开播放器页中的 player_aaaa JSON。 */
@Component
public class Pkmp4PlaybackPageParser {

    private static final String ASSIGNMENT = "var player_aaaa=";
    private final ObjectMapper objectMapper;

    public Pkmp4PlaybackPageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<PlaybackSource> parse(String html, URI pageUri) {
        Document document = Jsoup.parse(html, pageUri.toString());
        String providerName = providerName(document, pageUri);
        for (Element script : document.select("script")) {
            String data = script.data();
            int assignment = data.indexOf(ASSIGNMENT);
            if (assignment < 0) continue;
            int objectStart = data.indexOf('{', assignment + ASSIGNMENT.length());
            int objectEnd = data.lastIndexOf('}');
            if (objectStart < 0 || objectEnd <= objectStart) continue;
            try {
                JsonNode player = objectMapper.readTree(data.substring(objectStart, objectEnd + 1));
                String rawUrl = player.path("url").asText("").trim();
                if (rawUrl.isEmpty()) return Optional.empty();
                URI playbackUri = pageUri.resolve(rawUrl);
                if (!isSafePublicHttp(playbackUri)) return Optional.empty();
                return Optional.of(new PlaybackSource(
                        playbackUri.toString(), playbackType(playbackUri),
                        player.path("from").asText(""), providerName));
            } catch (Exception invalidPlayerData) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static String playbackType(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8")) return "HLS";
        if (path.endsWith(".mp4") || path.endsWith(".webm")) return "VIDEO";
        return "EMBED";
    }

    private static boolean isSafePublicHttp(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!("https".equals(scheme) || "http".equals(scheme)) || host.isBlank()
                || uri.getUserInfo() != null) return false;
        return !host.equals("localhost")
                && !host.endsWith(".local")
                && !host.equals("127.0.0.1")
                && !host.equals("0.0.0.0")
                && !host.equals("::1");
    }

    private static String providerName(Document document, URI pageUri) {
        for (Element group : document.select("ul.showplayul")) {
            String provider = headingText(group.previousElementSibling());
            if (provider.isBlank()) continue;
            for (Element link : group.select("a[href]")) {
                if (samePage(pageUri, resolve(pageUri, link.attr("href")))) {
                    return provider;
                }
            }
        }
        for (Element link : document.select("ul.showplayul a.on")) {
            Element group = link.closest("ul.showplayul");
            String provider = headingText(group == null ? null : group.previousElementSibling());
            if (!provider.isBlank()) return provider;
        }
        return null;
    }

    private static String headingText(Element heading) {
        if (heading == null) return "";
        Element label = heading.select("span").stream()
                .filter(span -> !span.classNames().contains("right"))
                .findFirst()
                .orElse(null);
        return label == null ? heading.ownText().trim() : label.text().trim();
    }

    private static URI resolve(URI base, String href) {
        try {
            return base.resolve(href);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean samePage(URI expected, URI actual) {
        if (expected == null || actual == null) return false;
        return expected.getPath() != null && expected.getPath().equals(actual.getPath())
                && java.util.Objects.equals(expected.getRawQuery(), actual.getRawQuery());
    }

    public record PlaybackSource(String url, String playbackType, String providerCode,
                                 String providerName) {
        public PlaybackSource(String url, String playbackType, String providerCode) {
            this(url, playbackType, providerCode, null);
        }
    }
}
