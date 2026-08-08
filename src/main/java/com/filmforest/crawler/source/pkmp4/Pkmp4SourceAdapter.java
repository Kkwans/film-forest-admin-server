package com.filmforest.crawler.source.pkmp4;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.model.ParsedContent;
import com.filmforest.crawler.model.SourceListItem;
import com.filmforest.crawler.source.CrawlerSourceAdapter;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Component
public class Pkmp4SourceAdapter implements CrawlerSourceAdapter {

    private final Pkmp4UrlBuilder urlBuilder;
    private final Pkmp4ListParser listParser;
    private final Pkmp4DetailParser detailParser;
    private final Pkmp4PageClassifier pageClassifier;

    public Pkmp4SourceAdapter(Pkmp4UrlBuilder urlBuilder, Pkmp4ListParser listParser,
                              Pkmp4DetailParser detailParser, Pkmp4PageClassifier pageClassifier) {
        this.urlBuilder = urlBuilder;
        this.listParser = listParser;
        this.detailParser = detailParser;
        this.pageClassifier = pageClassifier;
    }

    @Override
    public String sourceCode() {
        return "pkmp4";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("七味网", "pkmp4.xyz", "www.pkmp4.xyz");
    }

    @Override
    public URI listUri(ContentType contentType, int page) {
        return urlBuilder.listUri(contentType, page);
    }

    @Override
    public List<SourceListItem> parseList(String html, URI finalUri) {
        requirePageKind(html, finalUri, Pkmp4PageClassifier.PageKind.LIST);
        return listParser.parse(html, finalUri);
    }

    @Override
    public ParsedContent parseDetail(ContentType contentType, String html, URI finalUri) {
        requirePageKind(html, finalUri, Pkmp4PageClassifier.PageKind.DETAIL);
        return detailParser.parse(contentType, html, finalUri);
    }

    private void requirePageKind(String html, URI finalUri, Pkmp4PageClassifier.PageKind expected) {
        var actual = pageClassifier.classify(Jsoup.parse(html, finalUri.toString()));
        if (actual != expected) {
            throw new IllegalArgumentException("Unexpected pkmp4 page kind: expected="
                    + expected + ", actual=" + actual);
        }
    }
}
