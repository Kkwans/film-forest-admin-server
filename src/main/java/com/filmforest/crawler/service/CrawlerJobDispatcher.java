package com.filmforest.crawler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class CrawlerJobDispatcher {

    private final Executor crawlerJobExecutor;
    private final CrawlerJobRunner jobRunner;
    private final CrawlerJobCoordinator coordinator;

    public CrawlerJobDispatcher(@Qualifier("crawlerJobExecutor") Executor crawlerJobExecutor,
                                CrawlerJobRunner jobRunner,
                                CrawlerJobCoordinator coordinator) {
        this.crawlerJobExecutor = crawlerJobExecutor;
        this.jobRunner = jobRunner;
        this.coordinator = coordinator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterJobQueued(CrawlerJobQueuedEvent event) {
        dispatchExisting(event.jobId());
    }

    public void dispatchExisting(Long jobId) {
        if (!coordinator.reserveSubmission(jobId)) {
            return;
        }
        try {
            crawlerJobExecutor.execute(() -> {
                try {
                    jobRunner.run(jobId);
                } finally {
                    coordinator.releaseSubmission(jobId);
                }
            });
        } catch (RejectedExecutionException rejected) {
            coordinator.releaseSubmission(jobId);
            // Job 已持久化为 queued；维护线程会在容量释放后重新提交。
            log.debug("爬虫执行队列已满，Job 保持排队: jobId={}", jobId);
        } catch (RuntimeException error) {
            coordinator.releaseSubmission(jobId);
            throw error;
        }
    }
}
