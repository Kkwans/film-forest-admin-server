package com.filmforest.crawler.source.pkmp4;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class Pkmp4PlaybackPageParserTest {

    private final Pkmp4PlaybackPageParser parser =
            new Pkmp4PlaybackPageParser(new ObjectMapper());

    @Test
    void readsPublicHlsUrlFromPlayerData() {
        String html = """
                <script>var player_aaaa={"url":"https:\\/\\/cdn.example.test\\/movie\\/index.m3u8","from":"line1"}</script>
                """;

        var source = parser.parse(html,
                URI.create("https://www.pkmp4.xyz/py/42-1-1.html"));

        assertThat(source).isPresent();
        assertThat(source.orElseThrow().url())
                .isEqualTo("https://cdn.example.test/movie/index.m3u8");
        assertThat(source.orElseThrow().playbackType()).isEqualTo("HLS");
    }

    @Test
    void readsDynamicProviderDisplayNameFromTheSelectedPlaybackLink() {
        String html = """
                <div class="type"><span>非凡</span><span class="right">HD中字</span></div>
                <ul class="showplayul"><li><a href="/py/42-1-1.html">HD中字</a></li></ul>
                <script>var player_aaaa={"url":"https:\\/\\/cdn.example.test\\/movie\\/index.m3u8","from":"ffm3u8"}</script>
                """;

        var source = parser.parse(html,
                URI.create("https://www.pkmp4.xyz/py/42-1-1.html"));

        assertThat(source).isPresent();
        assertThat(source.orElseThrow().providerName()).isEqualTo("非凡");
        assertThat(source.orElseThrow().providerCode()).isEqualTo("ffm3u8");
    }

    @Test
    void rejectsNonHttpAndLocalPlaybackTargets() {
        assertThat(parser.parse("<script>var player_aaaa={\"url\":\"javascript:alert(1)\"}</script>",
                URI.create("https://www.pkmp4.xyz/py/42.html"))).isEmpty();
        assertThat(parser.parse("<script>var player_aaaa={\"url\":\"http://127.0.0.1/private.m3u8\"}</script>",
                URI.create("https://www.pkmp4.xyz/py/42.html"))).isEmpty();
    }
}
