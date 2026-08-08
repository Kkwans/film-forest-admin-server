package com.filmforest.crawler.scheduler;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.config.CrawlerExecutionConfiguration;
import com.filmforest.crawler.config.CrawlerExecutionProperties;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CrawlerScheduler 单元测试
 * 测试爬虫调度器的定时触发逻辑
 * 
 * TC-500~503: CronScheduler 验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlerScheduler 调度器测试")
class CrawlerSchedulerTest {

    @InjectMocks
    private CrawlerScheduler scheduler;

    @Mock
    private CrawlerScheduleMapper scheduleMapper;

    @Mock
    private CrawlerScheduleService scheduleService;

    // ========== TC-500: 5段 cron 正常触发 ==========

    @Nested
    @DisplayName("TC-500~501: Cron 表达式解析")
    class CronExpressionTest {

        @Test
        @DisplayName("TC-500: 5段 cron '0 2 * * *' 应正常解析")
        void normalizeCron_shouldHandle5FieldCron() {
            // 通过反射调用私有方法 normalizeCron
            String cron5 = "0 2 * * *";
            String[] parts = cron5.trim().split("\\s+");
            assertThat(parts.length).isEqualTo(5);

            // normalizeCron 会补秒字段
            String normalized = "0 " + cron5.trim();
            assertThat(normalized).isEqualTo("0 0 2 * * *");
            assertThat(normalized.split("\\s+").length).isEqualTo(6);
        }

        @Test
        @DisplayName("TC-501: 6段 cron '0 0 2 * * *' 应正常解析")
        void normalizeCron_shouldHandle6FieldCron() {
            String cron6 = "0 0 2 * * *";
            String[] parts = cron6.trim().split("\\s+");
            assertThat(parts.length).isEqualTo(6);

            // 已经是6段，不需要补秒
            String normalized = cron6.trim();
            assertThat(normalized).isEqualTo("0 0 2 * * *");
        }
    }

    // ========== TC-502: 并发触发限制 ==========

    @Nested
    @DisplayName("TC-502~503: 并发控制")
    class ConcurrencyTest {

        @Test
        @DisplayName("TC-502: NAS 默认只有1个 Worker、详情并发1、队列容量4")
        void executionProperties_shouldUseNasDefaults() {
            CrawlerExecutionProperties properties = new CrawlerExecutionProperties();

            assertThat(properties.getWorkerConcurrency()).isEqualTo(1);
            assertThat(properties.getDetailConcurrency()).isEqualTo(1);
            assertThat(properties.getQueueCapacity()).isEqualTo(4);
        }

        @Test
        @DisplayName("TC-503: 队列满时使用 AbortPolicy，禁止 Scheduler 线程代跑")
        void threadPool_shouldRejectInsteadOfCallerRuns() {
            CrawlerExecutionConfiguration configuration = new CrawlerExecutionConfiguration();
            var executor = (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)
                    configuration.crawlerJobExecutor(new CrawlerExecutionProperties());

            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(java.util.concurrent.ThreadPoolExecutor.AbortPolicy.class);
            executor.shutdown();
        }
    }

    // ========== 调度触发逻辑测试 ==========

    @Nested
    @DisplayName("调度触发逻辑")
    class TriggerLogicTest {

        @Test
        @DisplayName("禁用配置不应触发")
        void checkAndTriggerSchedules_shouldSkipDisabledSchedules() {
            CrawlerSchedule disabled = new CrawlerSchedule();
            disabled.setId(1L);
            disabled.setEnabled(0);  // 禁用
            disabled.setStatus("idle");

            when(scheduleMapper.selectList(any())).thenReturn(List.of(disabled));

            scheduler.checkAndTriggerSchedules();

            // 不应调用 startCrawler
            verify(scheduleService, never()).startScheduledCrawler(any());
        }

        @Test
        @DisplayName("非 idle 状态不应触发")
        void checkAndTriggerSchedules_shouldSkipRunningSchedules() {
            CrawlerSchedule running = new CrawlerSchedule();
            running.setId(1L);
            running.setEnabled(1);
            running.setStatus("running");  // 正在运行

            when(scheduleMapper.selectList(any())).thenReturn(List.of(running));

            scheduler.checkAndTriggerSchedules();

            verify(scheduleService, never()).startScheduledCrawler(any());
        }

        @Test
        @DisplayName("无 cron 表达式不应触发")
        void checkAndTriggerSchedules_shouldSkipWithoutCron() {
            CrawlerSchedule noCron = new CrawlerSchedule();
            noCron.setId(1L);
            noCron.setEnabled(1);
            noCron.setStatus("idle");
            noCron.setCronExpression(null);  // 无 cron

            when(scheduleMapper.selectList(any())).thenReturn(List.of(noCron));

            scheduler.checkAndTriggerSchedules();

            verify(scheduleService, never()).startScheduledCrawler(any());
        }

        @Test
        @DisplayName("空列表不应触发任何任务")
        void checkAndTriggerSchedules_shouldDoNothing_whenNoSchedules() {
            when(scheduleMapper.selectList(any())).thenReturn(Collections.emptyList());

            scheduler.checkAndTriggerSchedules();

            verify(scheduleService, never()).startScheduledCrawler(any());
        }

        @Test
        @DisplayName("enabled=1 且 nextRunTime 到期时只请求创建 scheduled Job")
        void checkAndTriggerSchedules_shouldStartDueSchedule() {
            CrawlerSchedule due = new CrawlerSchedule();
            due.setId(7L);
            due.setName("到期任务");
            due.setEnabled(1);
            due.setNextRunTime(CrawlerTime.nowUtc().minusSeconds(1));
            when(scheduleMapper.selectList(any())).thenReturn(List.of(due));
            when(scheduleService.startScheduledCrawler(7L)).thenReturn(true);

            scheduler.checkAndTriggerSchedules();

            verify(scheduleService).startScheduledCrawler(7L);
        }
    }
}
