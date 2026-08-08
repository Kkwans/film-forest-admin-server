package com.filmforest.poster.tmdb;

import com.filmforest.crawler.http.FetchCategory;

public class TmdbApiException extends RuntimeException {

    private final FetchCategory category;
    private final int statusCode;

    public TmdbApiException(FetchCategory category, int statusCode) {
        super("TMDB request failed: category=" + category + ", status=" + statusCode);
        this.category = category;
        this.statusCode = statusCode;
    }

    public FetchCategory getCategory() {
        return category;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
