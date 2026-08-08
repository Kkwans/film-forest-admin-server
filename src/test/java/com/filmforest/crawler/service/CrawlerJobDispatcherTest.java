package com.filmforest.crawler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Crawler Job 提交后派发")
class CrawlerJobDispatcherTest {

    @Test
    @DisplayName("Job 事件监听器必须在事务 AFTER_COMMIT 阶段运行")
    void afterJobQueued_shouldBeAfterCommitListener() throws Exception {
        TransactionalEventListener annotation = CrawlerJobDispatcher.class
                .getMethod("afterJobQueued", CrawlerJobQueuedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
    }

    @Test
    @DisplayName("执行队列满时释放内存占位，Job 保持数据库 queued")
    void dispatchExisting_queueFull_shouldReleaseReservation() {
        Executor executor = command -> {
            throw new RejectedExecutionException("full");
        };
        CrawlerJobRunner runner = mock(CrawlerJobRunner.class);
        CrawlerJobCoordinator coordinator = mock(CrawlerJobCoordinator.class);
        when(coordinator.reserveSubmission(9L)).thenReturn(true);
        CrawlerJobDispatcher dispatcher = new CrawlerJobDispatcher(executor, runner, coordinator);

        dispatcher.dispatchExisting(9L);

        verify(coordinator).releaseSubmission(9L);
        verifyNoInteractions(runner);
    }

    @Test
    @DisplayName("同一个 queued Job 已提交时不会重复进入执行器")
    void dispatchExisting_alreadySubmitted_shouldSkipDuplicate() {
        Executor executor = mock(Executor.class);
        CrawlerJobRunner runner = mock(CrawlerJobRunner.class);
        CrawlerJobCoordinator coordinator = mock(CrawlerJobCoordinator.class);
        when(coordinator.reserveSubmission(9L)).thenReturn(false);
        CrawlerJobDispatcher dispatcher = new CrawlerJobDispatcher(executor, runner, coordinator);

        dispatcher.dispatchExisting(9L);

        verifyNoInteractions(executor, runner);
    }
}
