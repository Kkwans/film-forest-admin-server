package com.filmforest.crawler.source.pkmp4;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class Pkmp4PageClassifier {

    public PageKind classify(Document document) {
        if (!document.select("a[href^=/mv/]").isEmpty()) {
            return PageKind.LIST;
        }
        if (document.selectFirst("h1") != null) {
            return PageKind.DETAIL;
        }
        return PageKind.UNKNOWN;
    }

    public enum PageKind {
        LIST,
        DETAIL,
        UNKNOWN
    }
}
