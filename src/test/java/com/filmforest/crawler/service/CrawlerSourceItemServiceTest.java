package com.filmforest.crawler.service;

import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerSourceItem;
import com.filmforest.crawler.mapper.CrawlerSourceItemMapper;
import com.filmforest.crawler.model.SourceListItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerSourceItemServiceTest {

    @Mock
    private CrawlerSourceItemMapper mapper;

    @Test
    void observesExistingItemAndReportsWhetherListFingerprintChanged() {
        SourceListItem item = new SourceListItem("42", "https://source.test/mv/42.html",
                "片名", null, 0);
        String fingerprint = SourceFingerprint.forListItem(item);
        CrawlerSourceItem previous = sourceItem(7L, fingerprint, "detail-hash");
        CrawlerSourceItem current = sourceItem(7L, fingerprint, "detail-hash");
        when(mapper.selectBySourceKey("pkmp4", "movie", "42"))
                .thenReturn(previous, current);

        CrawlerSourceItemService service = new CrawlerSourceItemService(mapper);
        CrawlerSourceItemService.Observation observation = service.observeListItem(
                "pkmp4", ContentType.MOVIE, item);

        assertThat(observation.knownBefore()).isTrue();
        assertThat(observation.listChanged()).isFalse();
        assertThat(observation.previousDetailFingerprint()).isEqualTo("detail-hash");
        var ordered = inOrder(mapper);
        ordered.verify(mapper).selectBySourceKey("pkmp4", "movie", "42");
        ordered.verify(mapper).upsertListObservation(eq("pkmp4"), eq("movie"), eq("42"),
                eq(item.sourceUrl()), eq(fingerprint), any());
        ordered.verify(mapper).selectBySourceKey("pkmp4", "movie", "42");
    }

    @Test
    void failedFetchRecordsAttemptTimeWithoutChangingDetailFingerprint() {
        CrawlerSourceItemService service = new CrawlerSourceItemService(mapper);

        service.recordFetchFailure("pkmp4", ContentType.DRAMA, "9", "HTTP_503");

        org.mockito.Mockito.verify(mapper).recordOutcome(eq("pkmp4"), eq("drama"), eq("9"),
                isNull(), isNull(), isNull(), any(), eq("fetch_failed"), eq("HTTP_503"));
    }

    private static CrawlerSourceItem sourceItem(Long id, String listFingerprint,
                                                String detailFingerprint) {
        CrawlerSourceItem item = new CrawlerSourceItem();
        item.setId(id);
        item.setListFingerprint(listFingerprint);
        item.setDetailFingerprint(detailFingerprint);
        item.setInternalContentId(42L);
        item.setLastParseStatus("parsed");
        return item;
    }
}
