package com.filmforest.crawler.scheduler;

import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerJobCoordinator;
import com.filmforest.crawler.service.CrawlerJobDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Crawler Job 心跳与重启恢复")
class CrawlerJobMaintenanceTest {

    @Mock private CrawlerTaskLogMapper jobMapper;
    @Mock private CrawlerJobCoordinator coordinator;
    @Mock private CrawlerJobDispatcher dispatcher;

    private CrawlerJobMaintenance maintenance;

    @BeforeEach
    void setUp() {
        CrawlerExecutionProperties properties = new CrawlerExecutionProperties();
        properties.setStaleHeartbeatMs(120_000);
        properties.setStalledProgressMs(300_000);
        maintenance = new CrawlerJobMaintenance(jobMapper, coordinator, dispatcher, properties);
    }

    @Test
    @DisplayName("运行中 Job 周期性刷新 heartbeat")
    void heartbeat_shouldTouchEveryRegisteredJob() {
        when(coordinator.runningJobIds()).thenReturn(Set.of(11L, 12L));

        maintenance.heartbeatRunningJobs();

        verify(jobMapper).touchHeartbeat(eq(11L), any(LocalDateTime.class));
        verify(jobMapper).touchHeartbeat(eq(12L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("心跳过期 Job 中断，进度停滞 Job 安全请求取消")
    void recoverStaleJobs_shouldInterruptExpiredAndStalledJobs() {
        when(coordinator.runningJobIds()).thenReturn(Set.of(32L));
        when(jobMapper.selectHeartbeatExpiredJobIds(any(LocalDateTime.class)))
                .thenReturn(List.of(31L));
        when(jobMapper.interruptHeartbeatExpiredJob(eq(31L), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(1);
        when(jobMapper.selectProgressStalledJobIds(any(LocalDateTime.class)))
                .thenReturn(List.of(32L));
        when(jobMapper.requestProgressStalledCancellation(eq(32L), any(LocalDateTime.class)))
                .thenReturn(1);

        maintenance.recoverStaleJobs();

        verify(jobMapper).interruptHeartbeatExpiredJob(eq(31L), any(LocalDateTime.class),
                any(LocalDateTime.class));
        verify(jobMapper).requestProgressStalledCancellation(eq(32L), any(LocalDateTime.class));
        verify(coordinator).requestCancellation(32L);
        verify(coordinator, never()).requestCancellation(31L);
    }

    @Test
    @DisplayName("应用启动后恢复持久化的 queued Job")
    void recoverAfterStartup_shouldDispatchQueuedJobs() {
        CrawlerTaskLog queued = new CrawlerTaskLog();
        queued.setId(21L);
        when(jobMapper.selectQueuedJobs(1000)).thenReturn(List.of(queued));

        maintenance.recoverAfterStartup();

        verify(dispatcher).dispatchExisting(21L);
    }

    @Test
    @DisplayName("周期补位持久化的 queued Job")
    void dispatchQueuedJobs_shouldDispatchPersistedJobs() {
        CrawlerTaskLog queued = new CrawlerTaskLog();
        queued.setId(22L);
        when(jobMapper.selectQueuedJobs(1000)).thenReturn(List.of(queued));

        maintenance.dispatchQueuedJobs();

        verify(dispatcher).dispatchExisting(22L);
    }
}
