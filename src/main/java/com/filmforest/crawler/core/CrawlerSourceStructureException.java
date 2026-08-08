package com.filmforest.crawler.core;

public class CrawlerSourceStructureException extends RuntimeException {

    public CrawlerSourceStructureException(String sourceCode, int consecutiveFailures,
                                           String limitedDiagnostic) {
        super("Crawler source structure may have changed: source=" + sourceCode
                + ", consecutiveFailures=" + consecutiveFailures
                + ", sample={" + limit(limitedDiagnostic) + "}");
    }

    private static String limit(String value) {
        if (value == null) return "unavailable";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
