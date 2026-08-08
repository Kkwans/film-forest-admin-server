package com.filmforest.crawler.service;

import com.filmforest.crawler.core.CrawlerCore;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Crawler Job Worker")
class CrawlerJobRunnerTest {

    @Mock private CrawlerTaskLogMapper jobMapper;
    @Mock private CrawlerScheduleMapper scheduleMapper;
    @Mock private CrawlerCore crawlerCore;
    @Mock private CrawlerJobCoordinator coordinator;
    @Mock private CrawlerJobLifecycleService lifecycleService;

    @Test
    @DisplayName("只有成功 claim QUEUED Job 的 Worker 才执行爬虫")
    void run_claimFailed_shouldNotExecute() {
        CrawlerJobRunner runner = runner();
        when(jobMapper.claimQueuedJob(eq(1L), any(LocalDateTime.class))).thenReturn(0);

        runner.run(1L);

        verifyNoInteractions(crawlerCore, coordinator, lifecycleService);
    }

    @Test
    @DisplayName("Worker 执行后统一由生命周期服务落终态")
    void run_success_shouldFinishLifecycle() {
        CrawlerJobRunner runner = runner();
        CrawlerTaskLog job = job();
        AtomicBoolean cancellation = new AtomicBoolean(false);
        CrawlExecutionSummary summary = new CrawlExecutionSummary(2, 2, 2, 1, 1, 0, 0, 0);
        when(jobMapper.claimQueuedJob(eq(1L), any(LocalDateTime.class))).thenReturn(1);
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(coordinator.register(1L)).thenReturn(cancellation);
        when(crawlerCore.executeCrawl(7L, 1L, cancellation)).thenReturn(summary);

        runner.run(1L);

        verify(lifecycleService).finish(1L, summary, false, null);
        verify(coordinator).unregister(1L);
        verify(scheduleMapper).recordJobStarted(eq(7L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("未知 contentType 等执行异常转为 failed 终态输入")
    void run_failure_shouldPassFailureToLifecycle() {
        CrawlerJobRunner runner = runner();
        CrawlerTaskLog job = job();
        AtomicBoolean cancellation = new AtomicBoolean(false);
        IllegalArgumentException failure = new IllegalArgumentException("Unknown contentType");
        when(jobMapper.claimQueuedJob(eq(1L), any(LocalDateTime.class))).thenReturn(1);
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(coordinator.register(1L)).thenReturn(cancellation);
        when(crawlerCore.executeCrawl(7L, 1L, cancellation)).thenThrow(failure);

        runner.run(1L);

        verify(lifecycleService).finish(eq(1L), any(CrawlExecutionSummary.class), eq(false), same(failure));
        verify(coordinator).unregister(1L);
    }

    private CrawlerJobRunner runner() {
        return new CrawlerJobRunner(jobMapper, scheduleMapper, crawlerCore, coordinator, lifecycleService);
    }

    private CrawlerTaskLog job() {
        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setId(1L);
        job.setScheduleId(7L);
        job.setCancelRequested(false);
        return job;
    }
}
