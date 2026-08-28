package com.filmforest.crawler.entity;

import com.filmforest.crawler.model.ParsedResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerResourceScopeTest {

    @Test
    void defaultsToDownloadResourcesAndSeparatesOnlineOnlyScope() {
        assertThat(CrawlerResourceScope.fromCode(null)).isEqualTo(CrawlerResourceScope.ALL);
        assertThat(CrawlerResourceScope.DOWNLOADS.includes(ParsedResource.Kind.MAGNET)).isTrue();
        assertThat(CrawlerResourceScope.DOWNLOADS.includes(ParsedResource.Kind.CLOUD)).isTrue();
        assertThat(CrawlerResourceScope.DOWNLOADS.includes(ParsedResource.Kind.ONLINE)).isFalse();
        assertThat(CrawlerResourceScope.ONLINE.includes(ParsedResource.Kind.ONLINE)).isTrue();
        assertThat(CrawlerResourceScope.ONLINE.includes(ParsedResource.Kind.MAGNET)).isFalse();
        assertThat(CrawlerResourceScope.ALL.includes(ParsedResource.Kind.ONLINE)).isTrue();
    }
}
