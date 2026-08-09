package com.filmforest.crawler.service;

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
import static org.mockito.Mockito.verify;

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
}
