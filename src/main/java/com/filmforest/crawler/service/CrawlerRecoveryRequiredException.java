package com.filmforest.crawler.service;

public class CrawlerRecoveryRequiredException extends RuntimeException {
    public CrawlerRecoveryRequiredException(String message) {
        super(message);
    }
}
