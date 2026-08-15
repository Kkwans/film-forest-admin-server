package com.filmforest.crawler.model;

/**
 * A per-resource-kind result status used to distinguish an empty result from
 * an incomplete or unsupported crawl.
 */
public enum ResourceParseStatus {
    COMPLETE,
    PARTIAL,
    FAILED,
    NOT_SUPPORTED
}
