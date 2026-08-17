package com.filmforest.crawler.core;

/** 来源被挑战、访问控制或其他不可用状态阻断时的显式失败。 */
public class CrawlerSourceUnavailableException extends RuntimeException {

    public CrawlerSourceUnavailableException(String message) {
        super(message);
    }
}
