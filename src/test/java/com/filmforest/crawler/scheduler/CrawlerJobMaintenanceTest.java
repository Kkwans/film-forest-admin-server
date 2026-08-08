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
    @DisplayName("心跳过期的 running/cancel_requested Job 会迁移为 interrupted")
    void recoverStaleJobs_shouldInterruptExpiredJobs() {
        when(jobMapper.interruptStaleJobs(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(2);

        maintenance.recoverStaleJobs();

        verify(jobMapper).interruptStaleJobs(any(LocalDateTime.class), any(LocalDateTime.class));
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
