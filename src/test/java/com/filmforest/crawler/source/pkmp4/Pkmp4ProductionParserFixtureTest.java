package com.filmforest.crawler.source.pkmp4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.model.ParsedResource;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
        assertThat(parsed.resources()).filteredOn(resource -> resource.kind() == ParsedResource.Kind.CLOUD)
                .singleElement().satisfies(resource -> {
                    assertThat(resource.diskType()).isEqualTo("quark");
                    assertThat(resource.password()).isNotBlank();
                });
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
    void resourceParserKeepsUnknownCloudLinksAndExtractsQueryCode() {
        var document = Jsoup.parse("""
                <p class="down-list3">
                  <a href="https://cloud.example.test/share?id=1&amp;code=xy%2B9">未知网盘</a>
                </p>
                """, "https://www.pkmp4.xyz/mv/42.html");

        var resource = resourceParser.parse(document,
                        URI.create("https://www.pkmp4.xyz/mv/42.html")).stream()
                .findFirst().orElseThrow();

        assertThat(resource.kind()).isEqualTo(ParsedResource.Kind.CLOUD);
        assertThat(resource.diskType()).isEqualTo("other");
        assertThat(resource.url()).isEqualTo("https://cloud.example.test/share?id=1&code=xy%2B9");
        assertThat(resource.password()).isNotBlank();
    }

    @Test
    void resourceParserExtractsChinesePasswordQueryKeys() {
        var document = Jsoup.parse("""
                <p class="down-list3">
                  <a href="https://cloud.example.test/share?id=1&amp;分享密码=中文+码">未知网盘</a>
                </p>
                """, "https://www.pkmp4.xyz/mv/42.html");

        var resource = resourceParser.parse(document,
                        URI.create("https://www.pkmp4.xyz/mv/42.html")).stream()
                .findFirst().orElseThrow();

        assertThat(resource.password()).isEqualTo("中文+码");
    }

    @Test
    void resourceParserSeparatesSiblingCodesAndPreservesQuerySymbols() {
        var document = Jsoup.parse("""
                <p class="down-list3">
                  <a href="https://pan.baidu.com/s/one?pw=ab+cd">百度网盘</a>
                  <a href="https://pan.quark.cn/s/two">夸克网盘</a>
                  <br>
                  <span>提取码：</span>
                  <span>
                    q-9_+=
                  </span>
                  <a href="https://cloud.example.test/s/three">未知网盘</a>
                  <span>密码：</span>
                  <span>p@ss-1</span>
                </p>
                """, "https://www.pkmp4.xyz/mv/42.html");

        var resources = resourceParser.parse(document,
                URI.create("https://www.pkmp4.xyz/mv/42.html"));

        assertThat(resources).filteredOn(resource -> resource.url().contains("/one"))
                .singleElement().satisfies(resource -> {
                    assertThat(resource.diskType()).isEqualTo("baidu");
                    assertThat(resource.password()).isEqualTo("ab+cd");
                });
        assertThat(resources).filteredOn(resource -> resource.url().contains("/two"))
                .singleElement().satisfies(resource -> {
                    assertThat(resource.diskType()).isEqualTo("quark");
                    assertThat(resource.password()).isEqualTo("q-9_+=");
                });
        assertThat(resources).filteredOn(resource -> resource.url().contains("/three"))
                .singleElement().satisfies(resource -> {
                    assertThat(resource.diskType()).isEqualTo("other");
                    assertThat(resource.password()).isEqualTo("p@ss-1");
                });
    }

    @Test
    void resourceParserAssociatesSiblingAndMultilineCodesWithTheAdjacentCloudLink() {
        var document = Jsoup.parse("""
                <div class="down-list3">
                  <a href="https://pan.baidu.com/s/baidu?pwd=query+code">百度网盘</a>
                  <span>提取码：</span><em>baidu_1</em>
                  <a href="https://pan.quark.cn/s/quark">夸克网盘</a>
                  <span>分享密码
                    ：quark-2+9</span>
                </div>
                """, "https://www.pkmp4.xyz/mv/42.html");

        var cloud = resourceParser.parse(document,
                        URI.create("https://www.pkmp4.xyz/mv/42.html")).stream()
                .filter(resource -> resource.kind() == ParsedResource.Kind.CLOUD)
                .toList();

        assertThat(cloud).extracting(ParsedResource::diskType)
                .containsExactly("baidu", "quark");
        assertThat(cloud).extracting(ParsedResource::password)
                .containsExactly("query+code", "quark-2+9");
    }

    @Test
    void resourceParserDoesNotInferProviderFromAnUnrelatedHostOrQueryText() {
        var document = Jsoup.parse("""
                <p class="down-list3">
                  <a href="https://files.example.test/share?ref=quark">未知供应商</a>
                </p>
                """, "https://www.pkmp4.xyz/mv/42.html");

        var resource = resourceParser.parse(document,
                        URI.create("https://www.pkmp4.xyz/mv/42.html")).stream()
                .findFirst().orElseThrow();

        assertThat(resource.diskType()).isEqualTo("other");
    }

    @Test
    void resourceStatusesBecomePartialWhenKnownResourceContainersAreAbsent() {
        var parsed = detailParser.parse(ContentType.MOVIE,
                "<h1>示例电影</h1><div class=\"movie-introduce\">简介</div>",
                URI.create("https://www.pkmp4.xyz/mv/42.html"));

        assertThat(parsed.diagnostics().resourceStatuses())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        ParsedResource.Kind.MAGNET, com.filmforest.crawler.model.ResourceParseStatus.PARTIAL,
                        ParsedResource.Kind.CLOUD, com.filmforest.crawler.model.ResourceParseStatus.PARTIAL,
                        ParsedResource.Kind.ONLINE, com.filmforest.crawler.model.ResourceParseStatus.PARTIAL));
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
