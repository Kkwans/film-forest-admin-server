package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerStatus;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.entity.CrawlerTriggerType;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Crawler Job 生命周期")
class CrawlerJobLifecycleServiceTest {

    @Mock private CrawlerScheduleMapper scheduleMapper;
    @Mock private CrawlerTaskLogMapper jobMapper;
    @Mock private CrawlerJobCoordinator coordinator;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CrawlerJobLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new CrawlerJobLifecycleService(
                scheduleMapper, jobMapper, coordinator, eventPublisher);
    }

    @Test
    @DisplayName("事务内创建 QUEUED Job，并发布提交后派发事件")
    void enqueue_shouldCreateQueuedJobAndPublishEvent() {
        CrawlerSchedule schedule = schedule(1L);
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule);
        when(jobMapper.selectActiveByScheduleId(1L)).thenReturn(null);
        when(jobMapper.insert(any(CrawlerTaskLog.class))).thenAnswer(invocation -> {
            invocation.<CrawlerTaskLog>getArgument(0).setId(101L);
            return 1;
        });

        CrawlerTaskLog createdJob = lifecycleService.enqueueJob(1L, CrawlerTriggerType.MANUAL, null);

        assertThat(createdJob.getId()).isEqualTo(101L);
        assertThat(createdJob.getStatus()).isEqualTo("queued");
        assertThat(createdJob.getQueuedAt()).isNotNull();
        ArgumentCaptor<CrawlerTaskLog> jobCaptor = ArgumentCaptor.forClass(CrawlerTaskLog.class);
        verify(jobMapper).insert(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo("queued");
        assertThat(jobCaptor.getValue().getTriggerType()).isEqualTo("manual");
        assertThat(jobCaptor.getValue().getCrawlMode()).isEqualTo("latest");
        assertThat(jobCaptor.getValue().getCurrentPage()).isEqualTo(1);
        verify(eventPublisher).publishEvent(new CrawlerJobQueuedEvent(101L));
    }

    @Test
    @DisplayName("兼容旧生命周期入口仍返回实际 Job ID")
    void enqueue_legacyEntryPoint_shouldReturnJobId() {
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule(1L));
        when(jobMapper.selectActiveByScheduleId(1L)).thenReturn(null);
        when(jobMapper.insert(any(CrawlerTaskLog.class))).thenAnswer(invocation -> {
            invocation.<CrawlerTaskLog>getArgument(0).setId(111L);
            return 1;
        });

        Long jobId = lifecycleService.enqueue(1L, CrawlerTriggerType.MANUAL, null);

        assertThat(jobId).isEqualTo(111L);
        verify(eventPublisher).publishEvent(new CrawlerJobQueuedEvent(111L));
    }

    @Test
    @DisplayName("同一 schedule 已有活动 Job 时不创建第二个 Job")
    void enqueue_activeJobExists_shouldReject() {
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule(1L));
        CrawlerTaskLog active = job(101L, 1L, "running");
        when(jobMapper.selectActiveByScheduleId(1L)).thenReturn(active);

        assertThat(lifecycleService.enqueue(1L, CrawlerTriggerType.SCHEDULED, null)).isNull();

        verify(jobMapper, never()).insert(any(CrawlerTaskLog.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("定时触发在锁内发现 Schedule 已禁用时不创建 Job")
    void enqueue_scheduledTriggerDisabledAfterSelection_shouldReject() {
        CrawlerSchedule schedule = schedule(1L);
        schedule.setEnabled(0);
        schedule.setNextRunTime(CrawlerTime.nowUtc().minusMinutes(1));
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule);

        assertThat(lifecycleService.enqueue(1L, CrawlerTriggerType.SCHEDULED, null)).isNull();

        verify(jobMapper, never()).insert(any(CrawlerTaskLog.class));
    }

    @Test
    @DisplayName("重试 Job 复制安全检查点并记录 retryOfJobId")
    void enqueue_retry_shouldCopyCheckpoint() {
        CrawlerSchedule schedule = schedule(1L);
        schedule.setCrawlMode("full");
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule);
        when(jobMapper.selectActiveByScheduleId(1L)).thenReturn(null);
        CrawlerTaskLog previous = job(88L, 1L, "interrupted");
        previous.setCurrentPage(7);
        previous.setCheckpoint("{\"nextPage\":7}");
        previous.setCrawlMode("full");
        when(jobMapper.selectById(88L)).thenReturn(previous);
        when(jobMapper.insert(any(CrawlerTaskLog.class))).thenAnswer(invocation -> {
            invocation.<CrawlerTaskLog>getArgument(0).setId(102L);
            return 1;
        });

        CrawlerTaskLog createdJob = lifecycleService.enqueueJob(1L, CrawlerTriggerType.RETRY, 88L);

        assertThat(createdJob.getId()).isEqualTo(102L);
        ArgumentCaptor<CrawlerTaskLog> captor = ArgumentCaptor.forClass(CrawlerTaskLog.class);
        verify(jobMapper).insert(captor.capture());
        assertThat(captor.getValue().getCurrentPage()).isEqualTo(7);
        assertThat(captor.getValue().getCheckpoint()).isEqualTo("{\"nextPage\":7}");
        assertThat(captor.getValue().getRetryOfJobId()).isEqualTo(88L);
        assertThat(captor.getValue().getCrawlMode()).isEqualTo("full");
    }

    @Test
    @DisplayName("LATEST 重试从第一页重新回查，不继承旧 checkpoint")
    void enqueue_latestRetry_shouldRestartFromFirstPage() {
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule(1L));
        when(jobMapper.selectActiveByScheduleId(1L)).thenReturn(null);
        CrawlerTaskLog previous = job(88L, 1L, "interrupted");
        previous.setCrawlMode("latest");
        previous.setCurrentPage(7);
        previous.setCheckpoint("{\"nextPage\":7}");
        when(jobMapper.selectById(88L)).thenReturn(previous);
        when(jobMapper.insert(any(CrawlerTaskLog.class))).thenAnswer(invocation -> {
            invocation.<CrawlerTaskLog>getArgument(0).setId(103L);
            return 1;
        });

        lifecycleService.enqueue(1L, CrawlerTriggerType.RETRY, 88L);

        ArgumentCaptor<CrawlerTaskLog> captor = ArgumentCaptor.forClass(CrawlerTaskLog.class);
        verify(jobMapper).insert(captor.capture());
        assertThat(captor.getValue().getCurrentPage()).isEqualTo(1);
        assertThat(captor.getValue().getCheckpoint()).isNull();
    }

    @Test
    @DisplayName("FULL 模式拒绝定时触发")
    void enqueue_scheduledFull_shouldReject() {
        CrawlerSchedule schedule = schedule(1L);
        schedule.setCrawlMode("full");
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule);
        when(jobMapper.selectActiveByScheduleId(1L)).thenReturn(null);

        assertThat(lifecycleService.enqueue(1L, CrawlerTriggerType.SCHEDULED, null)).isNull();

        verify(jobMapper, never()).insert(any(CrawlerTaskLog.class));
    }

    @Test
    @DisplayName("排队中取消直接进入 cancelled，不产生运行时取消标记")
    void cancelQueued_shouldNotRegisterRuntimeCancellation() {
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule(1L));
        CrawlerTaskLog queued = job(101L, 1L, "queued");
        when(jobMapper.selectActiveByScheduleId(1L)).thenReturn(queued);
        when(jobMapper.requestCancel(eq(101L), any(LocalDateTime.class))).thenReturn(1);

        assertThat(lifecycleService.requestCancelBySchedule(1L)).isTrue();

        verify(coordinator, never()).requestCancellation(any());
    }

    @Test
    @DisplayName("运行中取消同时持久化请求并通知 Worker")
    void cancelRunning_shouldSignalWorker() {
        when(scheduleMapper.selectByIdForUpdate(1L)).thenReturn(schedule(1L));
        CrawlerTaskLog running = job(101L, 1L, "running");
        when(jobMapper.selectActiveByScheduleId(1L)).thenReturn(running);
        when(jobMapper.requestCancel(eq(101L), any(LocalDateTime.class))).thenReturn(1);

        assertThat(lifecycleService.requestCancelBySchedule(1L)).isTrue();

        verify(coordinator).requestCancellation(101L);
    }

    @Test
    @DisplayName("部分条目失败时终态为 partial_success 并同步兼容统计")
    void finish_partialFailure_shouldPersistPartialSuccess() {
        CrawlerTaskLog running = job(101L, 1L, "running");
        running.setStartedAt(CrawlerTime.nowUtc().minusSeconds(2));
        when(jobMapper.selectById(101L)).thenReturn(running);
        CrawlExecutionSummary summary = new CrawlExecutionSummary(5, 5, 4, 2, 1, 1, 0, 1);

        lifecycleService.finish(101L, summary, false, null);

        ArgumentCaptor<CrawlerTaskLog> captor = ArgumentCaptor.forClass(CrawlerTaskLog.class);
        verify(jobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("partial_success");
        assertThat(captor.getValue().getItemsCrawled()).isEqualTo(5);
        assertThat(captor.getValue().getItemsAdded()).isEqualTo(2);
        assertThat(captor.getValue().getDurationMs()).isPositive();
        verify(scheduleMapper).recordJobFinished(1L, 5);
        ArgumentCaptor<CrawlerJobTerminalEvent> eventCaptor =
                ArgumentCaptor.forClass(CrawlerJobTerminalEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo(CrawlerStatus.PARTIAL_SUCCESS);
        assertThat(eventCaptor.getValue().failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("取消请求优先于普通成功结果，最终状态为 cancelled")
    void finish_cancelRequested_shouldPersistCancelled() {
        CrawlerTaskLog running = job(101L, 1L, "cancel_requested");
        running.setCancelRequested(true);
        when(jobMapper.selectById(101L)).thenReturn(running);

        lifecycleService.finish(101L,
                new CrawlExecutionSummary(1, 1, 1, 1, 0, 0, 0, 0), true, null);

        ArgumentCaptor<CrawlerTaskLog> captor = ArgumentCaptor.forClass(CrawlerTaskLog.class);
        verify(jobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("cancelled");
    }

    private CrawlerSchedule schedule(Long id) {
        CrawlerSchedule schedule = new CrawlerSchedule();
        schedule.setId(id);
        schedule.setName("电影同步");
        schedule.setContentType("movie");
        schedule.setSourceSite("pkmp4");
        schedule.setCrawlMode("incremental");
        schedule.setCronExpression("0 0 2 * * *");
        schedule.setEnabled(1);
        schedule.setNextRunTime(CrawlerTime.nowUtc().minusMinutes(1));
        return schedule;
    }

    private CrawlerTaskLog job(Long id, Long scheduleId, String status) {
        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setId(id);
        job.setScheduleId(scheduleId);
        job.setStatus(status);
        job.setCancelRequested(false);
        return job;
    }
}
