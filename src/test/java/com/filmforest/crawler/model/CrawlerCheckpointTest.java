package com.filmforest.crawler.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlerCheckpointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyPageOnlyCheckpointIsUpgradedWithoutLosingResumePage() throws Exception {
        CrawlerCheckpoint checkpoint = objectMapper.readValue(
                "{\"nextPage\":7}", CrawlerCheckpoint.class).normalized(1);

        assertThat(checkpoint.version()).isEqualTo(CrawlerCheckpoint.CURRENT_VERSION);
        assertThat(checkpoint.nextPage()).isEqualTo(7);
        assertThat(checkpoint.nextItemIndex()).isZero();
        assertThat(checkpoint.nextExternalId()).isNull();
    }

    @Test
    void futureCheckpointVersionIsRejectedForSafePageFallback() {
        CrawlerCheckpoint checkpoint = new CrawlerCheckpoint(2, 7, 35, "36", "35");

        assertThatThrownBy(() -> checkpoint.normalized(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported crawler checkpoint version");
    }

    @Test
    void resumePrefersStableExternalIdOverStaleItemIndex() {
        CrawlerCheckpoint checkpoint = new CrawlerCheckpoint(1, 7, 35, "b", "a");

        assertThat(checkpoint.resumeItemIndex(List.of(item("new"), item("a"), item("b"))))
                .isEqualTo(2);
    }

    @Test
    void resumeContinuesAfterLastCommittedItemWhenNextItemDisappeared() {
        CrawlerCheckpoint checkpoint = new CrawlerCheckpoint(1, 7, 35, "missing", "a");

        assertThat(checkpoint.resumeItemIndex(List.of(item("new"), item("a"), item("c"))))
                .isEqualTo(2);
    }

    @Test
    void resumeBoundsLegacyIndexWhenStableIdsAreUnavailable() {
        CrawlerCheckpoint checkpoint = new CrawlerCheckpoint(1, 7, 35, null, null);

        assertThat(checkpoint.resumeItemIndex(List.of(item("a"), item("b"))))
                .isEqualTo(2);
    }

    private static SourceListItem item(String externalId) {
        return new SourceListItem(externalId,
                "https://source.test/mv/" + externalId + ".html", externalId, null, 0);
    }
}
