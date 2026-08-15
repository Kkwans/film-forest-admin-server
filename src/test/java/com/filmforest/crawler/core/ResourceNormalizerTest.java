package com.filmforest.crawler.core;

import com.filmforest.crawler.model.ParsedResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNormalizerTest {

    private final ResourceNormalizer normalizer = new ResourceNormalizer();

    @Test
    void magnetUsesInfoHashInsteadOfDisplayParameters() {
        var first = normalizer.normalize("pkmp4", magnet(
                "magnet:?xt=urn:btih:ABCDEF123&dn=First"));
        var second = normalizer.normalize("pkmp4", magnet(
                "magnet:?dn=Second&xt=urn:btih:abcdef123"));

        assertThat(first.resourceKey()).isEqualTo(second.resourceKey());
    }

    @Test
    void cloudKeyIgnoresExtractionCodeAndTrackingOrder() {
        var first = normalizer.normalize("pkmp4", cloud(
                "https://PAN.BAIDU.COM:443/s/share/?pwd=abcd&utm_source=test&a=1"));
        var second = normalizer.normalize("pkmp4", cloud(
                "https://pan.baidu.com/s/share?a=1&pwd=efgh"));

        assertThat(first.normalizedUrl()).isEqualTo("https://pan.baidu.com/s/share?a=1");
        assertThat(first.resourceKey()).isEqualTo(second.resourceKey());
    }

    @Test
    void onlineKeyKeepsUnknownEpisodeDistinctFromNumberedEpisode() {
        ParsedResource unknown = online(null, "https://source.test/py/42");
        ParsedResource numbered = online(1, "https://source.test/py/42");

        assertThat(normalizer.normalize("pkmp4", unknown).resourceKey())
                .isNotEqualTo(normalizer.normalize("pkmp4", numbered).resourceKey());
    }

    @Test
    void unknownCloudProviderIsRetainedAsOtherAndCredentialQueryIsExcludedFromIdentity() {
        ParsedResource resource = new ParsedResource(ParsedResource.Kind.CLOUD, "网盘",
                "https://cloud.example.test/share?id=1&pwd=opaque-value", null, null,
                null, false, false, null, null, null, 0, "网盘", null, null);

        var normalized = normalizer.normalize("pkmp4", resource);

        assertThat(normalized.resource().diskType()).isEqualTo("other");
        assertThat(normalized.resource().url()).isEqualTo(resource.url());
        assertThat(normalized.normalizedUrl()).isEqualTo("https://cloud.example.test/share?id=1");
    }

    private static ParsedResource magnet(String url) {
        return new ParsedResource(ParsedResource.Kind.MAGNET, "资源", url, null, null,
                "1080P", false, false, null, null, null, 0, "资源", null, null);
    }

    private static ParsedResource cloud(String url) {
        return new ParsedResource(ParsedResource.Kind.CLOUD, "网盘", url, "baidu", "abcd",
                null, false, false, null, null, null, 0, "网盘", null, null);
    }

    private static ParsedResource online(Integer episode, String url) {
        return new ParsedResource(ParsedResource.Kind.ONLINE, "线路", url, null, null,
                null, false, false, 1, episode, "剧集", 0, "剧集", url, "EXTERNAL_PAGE");
    }
}
