package com.filmforest.crawler.service;

import com.filmforest.common.dto.PageResult;
import com.filmforest.common.type.ContentType;
import com.filmforest.crawler.entity.CrawlerFailureStage;
import com.filmforest.crawler.entity.CrawlerJobItemFailure;
import com.filmforest.crawler.mapper.CrawlerJobItemFailureMapper;
import com.filmforest.crawler.model.SourceListItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrawlerItemFailureServiceTest {

    @Mock
    private CrawlerJobItemFailureMapper mapper;

    @Test
    void recordsBoundedFinalFailureWithoutRawPageContent() {
        CrawlerItemFailureService service = new CrawlerItemFailureService(mapper);
        SourceListItem item = new SourceListItem("42", "https://source.test/mv/42.html",
                "标题", null, 0);

        service.record(9L, "pkmp4", ContentType.MOVIE, item,
                CrawlerFailureStage.PERSISTENCE, "TransientDataAccessException",
                2, true, "  transaction   rolled back  ");

        ArgumentCaptor<CrawlerJobItemFailure> captor =
                ArgumentCaptor.forClass(CrawlerJobItemFailure.class);
        verify(mapper).upsertFailure(captor.capture());
        CrawlerJobItemFailure failure = captor.getValue();
        assertThat(failure.getJobId()).isEqualTo(9L);
        assertThat(failure.getExternalId()).isEqualTo("42");
        assertThat(failure.getFailureStage()).isEqualTo("persistence");
        assertThat(failure.getAttemptCount()).isEqualTo(2);
        assertThat(failure.getRetryExhausted()).isTrue();
        assertThat(failure.getDiagnostic()).isEqualTo("transaction rolled back");
        assertThat(failure.getFailedAt()).isNotNull();
    }

    @Test
    void listsOnlyRequestedJobWithFiltersAndPagination() {
        CrawlerJobItemFailure first = new CrawlerJobItemFailure();
        first.setId(12L);
        first.setJobId(9L);
        when(mapper.countFailures(9L, "fetch", "SERVER_ERROR", true)).thenReturn(2L);
        when(mapper.selectFailurePage(9L, "fetch", "SERVER_ERROR", true, 1, 1L))
                .thenReturn(java.util.List.of(first));

        CrawlerItemFailureService service = new CrawlerItemFailureService(mapper);
        PageResult<CrawlerJobItemFailure> result = service.listFailures(
                9L, " FETCH ", " SERVER_ERROR ", true, 2, 1);

        assertThat(result.records()).containsExactly(first);
        assertThat(result.total()).isEqualTo(2L);
        assertThat(result.current()).isEqualTo(2L);
        assertThat(result.size()).isEqualTo(1L);
        assertThat(result.pages()).isEqualTo(2L);
        verify(mapper).countFailures(9L, "fetch", "SERVER_ERROR", true);
        verify(mapper).selectFailurePage(9L, "fetch", "SERVER_ERROR", true, 1, 1L);
    }

    @Test
    void emptyPageDoesNotQueryRecords() {
        when(mapper.countFailures(9L, null, null, null)).thenReturn(0L);

        CrawlerItemFailureService service = new CrawlerItemFailureService(mapper);
        PageResult<CrawlerJobItemFailure> result = service.listFailures(9L, null, null, null, 1, 20);

        assertThat(result.records()).isEmpty();
        assertThat(result.pages()).isZero();
        verify(mapper, never()).selectFailurePage(
                eq(9L), eq(null), eq(null), eq(null), eq(20), eq(0L));
    }

    @Test
    void rejectsInvalidPageAndSizeBoundaries() {
        CrawlerItemFailureService service = new CrawlerItemFailureService(mapper);

        assertThatThrownBy(() -> service.listFailures(9L, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("页码");
        assertThatThrownBy(() -> service.listFailures(9L, null, null, null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("每页");
        assertThatThrownBy(() -> service.listFailures(9L, null, null, null, 1, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("每页");
        verify(mapper, never()).countFailures(anyLong(), any(), any(), any());
    }
}
