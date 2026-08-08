package com.filmforest.crawler.source.pkmp4;

import com.filmforest.common.type.ContentType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

@Component
public class Pkmp4UrlBuilder {

    static final URI BASE_URI = URI.create("https://www.pkmp4.xyz");
    private static final Map<ContentType, String> TYPE_CODES = Map.of(
            ContentType.MOVIE, "1",
            ContentType.DRAMA, "2",
            ContentType.VARIETY, "3",
            ContentType.ANIME, "4",
            ContentType.SHORT_DRAMA, "30"
    );

    public URI listUri(ContentType contentType, int page) {
        if (page < 1) {
            throw new IllegalArgumentException("Page must be at least 1");
        }
        String typeCode = TYPE_CODES.get(contentType);
        if (typeCode == null) {
            throw new IllegalArgumentException("Unsupported pkmp4 content type: " + contentType);
        }
        String path = "/vt/" + typeCode + (page == 1 ? "" : "-" + page) + ".html";
        return BASE_URI.resolve(path);
    }

    public URI resolve(URI pageUri, String href) {
        return pageUri.resolve(href);
    }
}
