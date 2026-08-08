package com.filmforest.crawler.source.pkmp4;

import com.filmforest.common.type.ContentType;
import com.filmforest.common.util.StorylineCleaner;
import com.filmforest.crawler.model.ParseDiagnostics;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.ParsedResource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Pkmp4DetailParser {

    private static final Pattern EXTERNAL_ID = Pattern.compile("/mv/(\\d+)(?:\\.html)?");
    private static final Pattern YEAR = Pattern.compile("((?:19|20)\\d{2})");
    private static final Pattern DATE = Pattern.compile("((?:19|20)\\d{2}-\\d{2}-\\d{2})");
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*分钟");
    private static final Pattern TOTAL_EPISODES = Pattern.compile("(?:共|更新至)?\\s*(\\d+)\\s*[集期](?:全|完结)?");

    private final Pkmp4ResourceParser resourceParser;

    public Pkmp4DetailParser(Pkmp4ResourceParser resourceParser) {
        this.resourceParser = resourceParser;
    }

    public ParsedContent parse(ContentType contentType, String html, URI finalUri) {
        Document document = Jsoup.parse(html, finalUri.toString());
        Set<String> matchedSelectors = new LinkedHashSet<>();
        List<String> missingRequired = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String externalId = externalId(finalUri);
        if (externalId == null) missingRequired.add("externalId");
        String title = text(document, "h1", matchedSelectors);
        if (title == null) missingRequired.add("title");
        String posterUrl = poster(document, matchedSelectors);
        if (posterUrl == null) warnings.add("missingPoster");
        Integer year = firstInteger(title, YEAR);

        String rawReleaseDate = labelText(document, "上映", matchedSelectors);
        LocalDate releaseDate = parseDate(rawReleaseDate, warnings);
        String storyline = storyline(document, matchedSelectors);
        List<ParsedResource> resources = resourceParser.parse(document, finalUri);

        Map<String, Integer> resourceCounts = new LinkedHashMap<>();
        for (ParsedResource.Kind kind : ParsedResource.Kind.values()) {
            resourceCounts.put(kind.name().toLowerCase(),
                    (int) resources.stream().filter(resource -> resource.kind() == kind).count());
        }

        ParseDiagnostics diagnostics = new ParseDiagnostics(List.copyOf(matchedSelectors),
                List.copyOf(missingRequired), List.copyOf(warnings), fingerprint(html),
                Map.copyOf(resourceCounts));
        return new ParsedContent(externalId, contentType, finalUri.toString(), title, posterUrl, year,
                tagsByLabel(document, "地区", matchedSelectors), genres(document, matchedSelectors),
                tagsByLabel(document, "导演", matchedSelectors), tagsByLabel(document, "编剧", matchedSelectors),
                tagsByLabel(document, "主演", matchedSelectors), languages(document, matchedSelectors),
                firstInteger(labelText(document, "片长", matchedSelectors), DURATION), releaseDate,
                rawReleaseDate, splitValues(labelText(document, "又名", matchedSelectors)),
                score(document, "douban", "豆瓣", false), score(document, "imdb", "IMDB", false),
                score(document, "rottentomatoes", "烂番茄", true), storyline,
                totalEpisodes(document), resources, diagnostics);
    }

    private static String externalId(URI uri) {
        Matcher matcher = EXTERNAL_ID.matcher(uri.getPath());
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String poster(Document document, Set<String> matchedSelectors) {
        for (String selector : List.of("div.img img", "div.li-img img", ".movie-cover img")) {
            Element image = document.selectFirst(selector);
            if (image != null && !image.attr("abs:src").isBlank()) {
                matchedSelectors.add(selector);
                return image.attr("abs:src").trim();
            }
        }
        Element ogImage = document.selectFirst("meta[property=og:image]");
        if (ogImage != null && !ogImage.attr("content").isBlank()) {
            matchedSelectors.add("meta[property=og:image]");
            return document.baseUri().isBlank()
                    ? ogImage.attr("content").trim()
                    : URI.create(document.baseUri()).resolve(ogImage.attr("content")).toString();
        }
        return null;
    }

    private static String storyline(Document document, Set<String> matchedSelectors) {
        for (String selector : List.of(".movie-introduce", ".introduce", ".desc", ".summary")) {
            Element element = document.selectFirst(selector);
            if (element != null && !element.text().isBlank()) {
                matchedSelectors.add(selector);
                return StorylineCleaner.clean(element.text());
            }
        }
        Element description = document.selectFirst("meta[name=description]");
        if (description != null) {
            matchedSelectors.add("meta[name=description]");
            return StorylineCleaner.clean(description.attr("content"));
        }
        return "";
    }

    private static List<String> genres(Document document, Set<String> matchedSelectors) {
        List<String> values = new ArrayList<>();
        for (Element link : document.select("a[href*='/ms/'][href*='---']")) {
            if (link.attr("href").matches(".*/ms/\\d+---[^-].*")) {
                addValue(values, link.text());
            }
        }
        if (!values.isEmpty()) matchedSelectors.add("genreLinks");
        return List.copyOf(values);
    }

    private static List<String> languages(Document document, Set<String> matchedSelectors) {
        List<String> values = new ArrayList<>();
        for (Element link : document.select("a[href*='/ms/'][href*='----']")) {
            if (link.attr("href").matches(".*/ms/\\d+----[^-].*")) {
                addValue(values, link.text());
            }
        }
        if (!values.isEmpty()) {
            matchedSelectors.add("languageLinks");
            return List.copyOf(values);
        }
        return tagsByLabel(document, "语言", matchedSelectors);
    }

    private static List<String> tagsByLabel(Document document, String label,
                                             Set<String> matchedSelectors) {
        for (Element span : document.select("span")) {
            if (!isLabel(span, label)) continue;
            List<String> values = new ArrayList<>();
            Node sibling = span.nextSibling();
            while (sibling != null && !isAnotherLabel(sibling)) {
                if (sibling instanceof Element element) {
                    if (element.is("a")) addValue(values, element.text());
                    for (Element anchor : element.select("a")) addValue(values, anchor.text());
                }
                sibling = sibling.nextSibling();
            }
            if (!values.isEmpty()) {
                matchedSelectors.add("label:" + label);
                return List.copyOf(values);
            }
        }
        return List.of();
    }

    private static String labelText(Document document, String label, Set<String> matchedSelectors) {
        for (Element span : document.select("span")) {
            if (!isLabel(span, label)) continue;
            StringBuilder text = new StringBuilder();
            Node sibling = span.nextSibling();
            while (sibling != null && !isAnotherLabel(sibling)) {
                if (sibling instanceof TextNode textNode) {
                    text.append(' ').append(textNode.text());
                } else if (sibling instanceof Element element) {
                    if (!text.toString().isBlank()) {
                        break;
                    }
                    text.append(' ').append(element.text());
                    break;
                }
                sibling = sibling.nextSibling();
            }
            String value = text.toString().trim();
            if (!value.isBlank()) {
                matchedSelectors.add("label:" + label);
                return value;
            }
        }
        return null;
    }

    private static boolean isLabel(Element element, String label) {
        String normalized = element.text().trim().replace(':', '：');
        return normalized.equals(label + "：") || normalized.equals(label);
    }

    private static boolean isAnotherLabel(Node node) {
        return node instanceof Element element && element.is("span")
                && element.text().trim().matches(".{1,8}[：:]$");
    }

    private static Integer totalEpisodes(Document document) {
        for (String text : List.of(document.select(".otherbox").text(),
                document.select(".total, .episode, [class*=episode]").text(), document.body().text())) {
            Integer number = firstInteger(text, TOTAL_EPISODES);
            if (number != null) return number;
        }
        return null;
    }

    private static BigDecimal score(Document document, String hrefMarker, String label, boolean percentage) {
        for (Element link : document.select("a[href*=" + hrefMarker + "]")) {
            Matcher decimal = Pattern.compile(Pattern.quote(label) + "[\\s:：]*(\\d+(?:\\.\\d+)?)")
                    .matcher(link.text());
            if (decimal.find()) {
                BigDecimal value = new BigDecimal(decimal.group(1));
                if (percentage && link.text().contains("%")) {
                    return value.divide(BigDecimal.TEN, 1, java.math.RoundingMode.HALF_UP);
                }
                return value;
            }
        }
        return null;
    }

    private static LocalDate parseDate(String raw, List<String> warnings) {
        if (raw == null) return null;
        Matcher matcher = DATE.matcher(raw);
        if (!matcher.find()) return null;
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (DateTimeParseException invalid) {
            warnings.add("invalidReleaseDate");
            return null;
        }
    }

    private static Integer firstInteger(String text, Pattern pattern) {
        if (text == null) return null;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static String text(Document document, String selector, Set<String> matchedSelectors) {
        Element element = document.selectFirst(selector);
        if (element == null || element.text().isBlank()) return null;
        matchedSelectors.add(selector);
        return element.text().trim();
    }

    private static List<String> splitValues(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> values = new ArrayList<>();
        for (String part : value.split("[/／,，、]")) addValue(values, part);
        return List.copyOf(values);
    }

    private static void addValue(List<String> values, String candidate) {
        if (candidate == null) return;
        String trimmed = candidate.trim();
        if (!trimmed.isEmpty() && !values.contains(trimmed)) values.add(trimmed);
    }

    private static String fingerprint(String html) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(html.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
