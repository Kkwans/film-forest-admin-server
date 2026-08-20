package com.filmforest.crawler.source.pkmp4;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerSourceSort;
import com.filmforest.crawler.model.CrawlerSourceQuery;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pkmp4UrlBuilderTest {

    private final Pkmp4UrlBuilder builder = new Pkmp4UrlBuilder();

    @Test
    void buildsVerifiedSortUrlsAndKeepsPageOneCanonical() {
        assertThat(builder.listUri(new CrawlerSourceQuery(
                ContentType.MOVIE, CrawlerSourceSort.TIME, Map.of(), 1)))
                .isEqualTo(URI.create("https://www.pkmp4.xyz/ms/1--time---------.html"));
        assertThat(builder.listUri(new CrawlerSourceQuery(
                ContentType.MOVIE, CrawlerSourceSort.RATING, Map.of(), 2)))
                .isEqualTo(URI.create("https://www.pkmp4.xyz/ms/1--score------2---.html"));
        assertThat(builder.listUri(new CrawlerSourceQuery(
                ContentType.MOVIE, CrawlerSourceSort.POPULARITY, Map.of(), 3)))
                .isEqualTo(URI.create("https://www.pkmp4.xyz/ms/1--hits------3---.html"));
    }

    @Test
    void buildsSourceFiltersInTheSourceFieldSlots() {
        URI uri = builder.listUri(new CrawlerSourceQuery(
                ContentType.MOVIE,
                CrawlerSourceSort.RATING,
                Map.of("genre", "科幻", "year", "2024", "region", "美国", "language", "英语"),
                1));

        assertThat(uri.toString())
                .isEqualTo("https://www.pkmp4.xyz/ms/1-%E7%BE%8E%E5%9B%BD-score-%E7%A7%91%E5%B9%BB-%E8%8B%B1%E8%AF%AD-------2024.html");
    }

    @Test
    void rejectsUnknownSourceFilterInsteadOfSilentlyIgnoringIt() {
        assertThatThrownBy(() -> builder.listUri(new CrawlerSourceQuery(
                ContentType.MOVIE, CrawlerSourceSort.TIME, Map.of("standardGenre", "科幻"), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持来源筛选字段");
    }
}
