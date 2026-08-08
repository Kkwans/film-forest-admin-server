package com.filmforest.crawler.http;

public enum FetchCategory {
    SUCCESS,
    NOT_FOUND,
    FORBIDDEN,
    RATE_LIMITED,
    SERVER_ERROR,
    CHALLENGE_PAGE,
    INVALID_CONTENT_TYPE,
    EMPTY_BODY,
    NETWORK_ERROR,
    CANCELLED
}
