package com.filmforest.crawler.source.pkmp4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.model.ParsedResource;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Pkmp4ProductionParserFixtureTest {

    private final Pkmp4ResourceParser resourceParser = new Pkmp4ResourceParser();
    private final Pkmp4DetailParser detailParser = new Pkmp4DetailParser(resourceParser);

    @Test
    void productionParserReadsTypedContentAndDiagnosticsFromFixture() throws IOException {
        String html = fixture("/fixtures/pkmp4/detail-drama-475547.html");

        var parsed = detailParser.parse(ContentType.DRAMA, html,
                URI.create("https://www.pkmp4.xyz/mv/475547.html"));

        assertThat(parsed.valid()).isTrue();
        assertThat(parsed.externalId()).isEqualTo("475547");
        assertThat(parsed.title()).isEqualTo("示例剧集 第二季");
        assertThat(parsed.sourcePosterUrl()).isEqualTo("https://www.pkmp4.xyz/images/475547.jpg");
        assertThat(parsed.year()).isEqualTo(2024);
        assertThat(parsed.directors()).containsExactly("导演甲", "导演乙");
        assertThat(parsed.writers()).containsExactly("编剧甲");
        assertThat(parsed.actors()).containsExactly("演员甲", "演员乙");
        assertThat(parsed.regions()).containsExactly("中国大陆");
        assertThat(parsed.languages()).containsExactly("普通话");
        assertThat(parsed.genres()).containsExactly("剧情", "悬疑");
        assertThat(parsed.aliases()).containsExactly("示例别名", "Example Series");
        assertThat(parsed.releaseDate()).hasToString("2024-06-01");
        assertThat(parsed.durationMinutes()).isEqualTo(45);
        assertThat(parsed.totalEpisodes()).isEqualTo(12);
        assertThat(parsed.storyline()).isEqualTo("这是一段剧情简介");
        assertThat(parsed.diagnostics().pageFingerprint()).hasSize(64);
        assertThat(parsed.diagnostics().resourceCounts())
                .containsEntry("magnet", 1).containsEntry("cloud", 1).containsEntry("online", 7);
    }

    @Test
    void detailTitleSeparatesTrailingReleaseYearFromCanonicalTitle() {
        String html = """
                <h1>2001太空漫游（1968）</h1>
                <div class="movie-introduce">经典科幻片。</div>
                """;

        var parsed = detailParser.parse(ContentType.MOVIE, html,
                URI.create("https://www.pkmp4.xyz/mv/42.html"));

        assertThat(parsed.title()).isEqualTo("2001太空漫游");
        assertThat(parsed.year()).isEqualTo(1968);
    }

    @Test
    void detailPageWithRelatedContentLinksIsStillClassifiedAsDetail() throws IOException {
        String html = fixture("/fixtures/pkmp4/detail-drama-475547.html");
        var adapter = new Pkmp4SourceAdapter(new Pkmp4UrlBuilder(), new Pkmp4ListParser(),
                detailParser, new Pkmp4PageClassifier(),
                new Pkmp4PlaybackEnricher(new Pkmp4PlaybackPageParser(new ObjectMapper())));

        var parsed = adapter.parseDetail(ContentType.DRAMA, html,
                URI.create("https://www.pkmp4.xyz/mv/475547.html"));

        assertThat(parsed.externalId()).isEqualTo("475547");
    }

    @Test
    void listingHeadingAndOpenGraphImageDoNotMasqueradeAsDetailPage() {
        var document = Jsoup.parse("""
                <head><meta property="og:image" content="/site.jpg"></head>
                <body><h1>电影列表</h1><a href="/mv/42.html">影片</a></body>
                """, "https://www.pkmp4.xyz/vt/1.html");

        assertThat(new Pkmp4PageClassifier().classify(document))
                .isEqualTo(Pkmp4PageClassifier.PageKind.LIST);
    }

    @Test
    void productionResourceParserPreservesSpecialAndDatedEpisodesInSourceOrder() throws IOException {
        String html = fixture("/fixtures/pkmp4/detail-drama-475547.html");
        var parsed = detailParser.parse(ContentType.DRAMA, html,
                URI.create("https://www.pkmp4.xyz/mv/475547.html"));
        var online = parsed.resources().stream()
                .filter(resource -> resource.kind() == ParsedResource.Kind.ONLINE)
                .toList();

        assertThat(online).extracting(ParsedResource::sourceOrder)
                .containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(online).extracting(ParsedResource::episodeNumber)
                .containsExactly(1, 2, 12, null, null, null, null);
        assertThat(online).extracting(ParsedResource::episodeTitle)
                .contains("正片", "先导片", "特别篇 上集", "2024-06-01期");
    }

    @Test
    void listParserDeduplicatesDetailLinksAndKeepsPageOrder() {
        String html = """
                <a href="/mv/2.html"><img src="data:image/gif;base64,placeholder" data-src="/2.jpg" alt="第二部"></a>
                <a href="/mv/2.html">重复</a>
                <a href="/mv/3?from=list">第三部</a>
                """;

        var items = new Pkmp4ListParser().parse(html,
                URI.create("https://www.pkmp4.xyz/vt/1.html"));

        assertThat(items).extracting(item -> item.externalId()).containsExactly("2", "3");
        assertThat(items).extracting(item -> item.sourceOrder()).containsExactly(0, 1);
        assertThat(items.get(0).posterUrl()).isEqualTo("https://www.pkmp4.xyz/2.jpg");
    }

    private static String fixture(String resource) throws IOException {
        try (var input = Pkmp4ProductionParserFixtureTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing fixture: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
