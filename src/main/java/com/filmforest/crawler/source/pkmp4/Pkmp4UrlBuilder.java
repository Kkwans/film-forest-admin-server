package com.filmforest.crawler.source.pkmp4;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerSourceSort;
import com.filmforest.crawler.model.CrawlerSourceQuery;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class Pkmp4UrlBuilder {

    static final URI BASE_URI = URI.create("https://www.pkmp4.xyz");
    private static final Set<String> FILTER_KEYS = Set.of("genre", "year", "region", "language");
    private static final Map<ContentType, String> TYPE_CODES = Map.of(
            ContentType.MOVIE, "1",
            ContentType.DRAMA, "2",
            ContentType.VARIETY, "3",
            ContentType.ANIME, "4",
            ContentType.SHORT_DRAMA, "30"
    );

    public URI listUri(ContentType contentType, int page) {
        return listUri(new CrawlerSourceQuery(contentType, CrawlerSourceSort.TIME, Map.of(), page));
    }

    public URI listUri(CrawlerSourceQuery query) {
        String typeCode = TYPE_CODES.get(query.contentType());
        if (typeCode == null) {
            throw new IllegalArgumentException("Unsupported pkmp4 content type: " + query.contentType());
        }
        query.sourceFilters().keySet().forEach(key -> {
            if (!FILTER_KEYS.contains(key)) {
                throw new IllegalArgumentException("pkmp4 不支持来源筛选字段: " + key);
            }
        });

        List<String> fields = new ArrayList<>(List.of(
                typeCode,
                encoded(query.filter("region")),
                sortCode(query.sort()),
                encoded(query.filter("genre")),
                encoded(query.filter("language")),
                "",
                "",
                "",
                query.page() == 1 ? "" : Integer.toString(query.page()),
                "",
                "",
                encoded(query.filter("year"))
        ));
        return BASE_URI.resolve("/ms/" + String.join("-", fields) + ".html");
    }

    public URI resolve(URI pageUri, String href) {
        return pageUri.resolve(href);
    }

    private static String sortCode(CrawlerSourceSort sort) {
        return switch (sort) {
            case TIME -> "time";
            case POPULARITY -> "hits";
            case RATING -> "score";
        };
    }

    private static String encoded(String value) {
        if (value == null || value.isBlank()) return "";
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
