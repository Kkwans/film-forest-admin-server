package com.filmforest.crawler.source.pkmp4;

import com.filmforest.crawler.model.SourceListItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Pkmp4ListParser {

    private static final Pattern DETAIL_PATH = Pattern.compile("/mv/(\\d+)\\.html");

    public List<SourceListItem> parse(String html, URI finalUri) {
        Document document = Jsoup.parse(html, finalUri.toString());
        Map<String, SourceListItem> items = new LinkedHashMap<>();
        int order = 0;
        for (Element link : document.select("a[href^=/mv/]")) {
            Matcher matcher = DETAIL_PATH.matcher(link.attr("href"));
            if (!matcher.matches()) {
                continue;
            }
            String externalId = matcher.group(1);
            if (items.containsKey(externalId)) {
                continue;
            }
            Element image = link.selectFirst("img");
            String title = image != null && !image.attr("alt").isBlank()
                    ? image.attr("alt").trim() : link.text().trim();
            String poster = image == null ? null : blankToNull(image.attr("abs:src"));
            items.put(externalId, new SourceListItem(externalId,
                    finalUri.resolve(link.attr("href")).toString(), title, poster, order++));
        }
        return List.copyOf(new ArrayList<>(items.values()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
