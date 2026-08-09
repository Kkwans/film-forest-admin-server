package com.filmforest.notification.service;

import com.filmforest.crawler.entity.CrawlerStatus;
import com.filmforest.crawler.service.CrawlerJobTerminalEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CrawlerJobNotificationListenerTest {

    @Mock private AdminNotificationService notificationService;

    @Test
    void failedJob_shouldPublishFailureWithoutRawException() {
        CrawlerJobNotificationListener listener = new CrawlerJobNotificationListener(notificationService);

        listener.afterJobFinished(event(CrawlerStatus.FAILED, null));

        ArgumentCaptor<AdminNotificationService.NotificationEvent> captor =
                ArgumentCaptor.forClass(AdminNotificationService.NotificationEvent.class);
        verify(notificationService).publishToAdmins(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("CRAWLER_FAILED");
        assertThat(captor.getValue().severity()).isEqualTo("ERROR");
        assertThat(captor.getValue().message()).contains("失败 1 条");
        assertThat(captor.getValue().link()).isEqualTo("/crawler?jobId=101");
    }

    @Test
    void successfulRetry_shouldPublishRecovery() {
        CrawlerJobNotificationListener listener = new CrawlerJobNotificationListener(notificationService);

        listener.afterJobFinished(event(CrawlerStatus.SUCCESS, 88L));

        ArgumentCaptor<AdminNotificationService.NotificationEvent> captor =
                ArgumentCaptor.forClass(AdminNotificationService.NotificationEvent.class);
        verify(notificationService).publishToAdmins(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("CRAWLER_RECOVERED");
        assertThat(captor.getValue().title()).contains("已恢复");
    }

    @Test
    void notificationFailure_shouldNeverEscapeAfterCommitListener() {
        CrawlerJobNotificationListener listener = new CrawlerJobNotificationListener(notificationService);
        doThrow(new IllegalStateException("notification storage unavailable"))
                .when(notificationService).publishToAdmins(any());

        assertThatCode(() -> listener.afterJobFinished(event(CrawlerStatus.FAILED, null)))
                .doesNotThrowAnyException();
    }

    private CrawlerJobTerminalEvent event(CrawlerStatus status, Long retryOfJobId) {
        return new CrawlerJobTerminalEvent(
                101L, 1L, "电影同步", "pkmp4", "movie", status,
                retryOfJobId, 5, 2, 1, status == CrawlerStatus.SUCCESS ? 0 : 1);
    }
}
