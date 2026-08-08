package com.filmforest.crawler.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlerCrawlModeTest {

    @Test
    void legacyIncrementalMapsToLatest() {
        assertThat(CrawlerCrawlMode.fromCode(null)).isEqualTo(CrawlerCrawlMode.LATEST);
        assertThat(CrawlerCrawlMode.fromCode("incremental")).isEqualTo(CrawlerCrawlMode.LATEST);
        assertThat(CrawlerCrawlMode.fromCode(" FULL ")).isEqualTo(CrawlerCrawlMode.FULL);
    }

    @Test
    void unknownModeIsRejected() {
        assertThatThrownBy(() -> CrawlerCrawlMode.fromCode("delta"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
