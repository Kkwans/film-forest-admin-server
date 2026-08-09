package com.filmforest.crawler.source.pkmp4;

import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class Pkmp4PageClassifier {

    public PageKind classify(Document document) {
        if (document.selectFirst("h1") != null
                && document.selectFirst(".movie-introduce, .introduce, .desc, .summary, "
                + ".down-list3, a[href^=/py/], div.img img, .movie-cover img") != null) {
            return PageKind.DETAIL;
        }
        if (!document.select("a[href^=/mv/]").isEmpty()) {
            return PageKind.LIST;
        }
        return PageKind.UNKNOWN;
    }

    public enum PageKind {
        LIST,
        DETAIL,
        UNKNOWN
    }
}
