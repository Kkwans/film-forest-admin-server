package com.filmforest.crawler.service;

import com.filmforest.crawler.core.CrawlerCore;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.impl.CrawlerScheduleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CrawlerScheduleService 单元测试
 * 
 * 覆盖测试用例:
 * - TC-001~004: 基础配置 CRUD（字段校验、默认值、genreFilter 归一化）
 * - TC-040~041: enabled 启用/禁用
 * - TC-050~052: startCrawler/stopCrawler 状态转换
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CrawlerScheduleService 服务层测试")
class CrawlerScheduleServiceTest {

    @Mock
    private CrawlerScheduleMapper scheduleMapper;

    @Mock
    private CrawlerTaskLogMapper taskLogMapper;

    @Mock
    private CrawlerCore crawlerCore;

    @InjectMocks
    private CrawlerScheduleServiceImpl scheduleService;

    // ========== TC-001~004: 基础配置 CRUD ==========

    @Nested
    @DisplayName("TC-001~004: 基础配置 CRUD")
    class BasicCrudTest {

        @Test
        @DisplayName("TC-001: 创建电影配置 - batchSize=10，状态应为 idle")
        void saveSchedule_newMovieConfig_shouldSetIdleAndZeroCounters() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setName("电影爬虫");
            schedule.setContentType("movie");
            schedule.setSourceSite("pkmp4.xyz");
            schedule.setBatchSize(10);
            schedule.setCronExpression("0 2 * * *");

            when(scheduleMapper.insert(any())).thenReturn(1);

            boolean result = scheduleService.saveSchedule(schedule);

            assertThat(result).isTrue();
            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());

            CrawlerSchedule saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("idle");
            assertThat(saved.getTotalRuns()).isEqualTo(0);
            assertThat(saved.getTotalItems()).isEqualTo(0);
            assertThat(saved.getContentType()).isEqualTo("movie");
            assertThat(saved.getBatchSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("TC-002: 创建剧集配置 - cron=0 2 * * *，配置保存成功")
        void saveSchedule_newDramaConfig_shouldSaveSuccessfully() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setName("剧集爬虫");
            schedule.setContentType("drama");
            schedule.setSourceSite("pkmp4.xyz");
            schedule.setCronExpression("0 2 * * *");

            when(scheduleMapper.insert(any())).thenReturn(1);

            boolean result = scheduleService.saveSchedule(schedule);

            assertThat(result).isTrue();
            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());

            CrawlerSchedule saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo("idle");
            assertThat(saved.getCronExpression()).isEqualTo("0 2 * * *");
        }

        @Test
        @DisplayName("TC-003: 编辑已有配置 - 修改 batchSize=50")
        void saveSchedule_updateExisting_shouldUpdateBatchSize() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setId(1L);
            schedule.setName("电影爬虫");
            schedule.setContentType("movie");
            schedule.setSourceSite("pkmp4.xyz");
            schedule.setBatchSize(50);

            when(scheduleMapper.updateById(any())).thenReturn(1);

            boolean result = scheduleService.saveSchedule(schedule);

            assertThat(result).isTrue();
            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).updateById(captor.capture());

            CrawlerSchedule updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(1L);
            assertThat(updated.getBatchSize()).isEqualTo(50);
            // 更新时不应重置 status/totalRuns/totalItems
            verify(scheduleMapper, never()).insert(any());
        }

        @Test
        @DisplayName("TC-004: 删除已有配置 - 配置删除成功")
        void deleteSchedule_shouldDeleteAndCleanRunningTask() {
            when(scheduleMapper.deleteById(1L)).thenReturn(1);

            boolean result = scheduleService.deleteSchedule(1L);

            assertThat(result).isTrue();
            verify(scheduleMapper).deleteById(1L);
        }
    }

    // ========== genreFilter 归一化 ==========

    @Nested
    @DisplayName("genreFilter 归一化（saveSchedule 副作用）")
    class GenreFilterNormalizationTest {

        @Test
        @DisplayName("genreFilter=null → 保持 null")
        void saveSchedule_nullGenreFilter_shouldKeepNull() {
            CrawlerSchedule schedule = createBaseSchedule(null);
            schedule.setGenreFilter(null);

            when(scheduleMapper.insert(any())).thenReturn(1);
            scheduleService.saveSchedule(schedule);

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());
            assertThat(captor.getValue().getGenreFilter()).isNull();
        }

        @Test
        @DisplayName("genreFilter=\"\" → 归一化为 null")
        void saveSchedule_emptyGenreFilter_shouldNormalizeToNull() {
            CrawlerSchedule schedule = createBaseSchedule(null);
            schedule.setGenreFilter("  ");

            when(scheduleMapper.insert(any())).thenReturn(1);
            scheduleService.saveSchedule(schedule);

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());
            assertThat(captor.getValue().getGenreFilter()).isNull();
        }

        @Test
        @DisplayName("genreFilter=\"爱情,科幻\" → JSON 数组")
        void saveSchedule_commaSeparatedGenreFilter_shouldConvertToJsonArray() {
            CrawlerSchedule schedule = createBaseSchedule(null);
            schedule.setGenreFilter("爱情,科幻");

            when(scheduleMapper.insert(any())).thenReturn(1);
            scheduleService.saveSchedule(schedule);

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());
            assertThat(captor.getValue().getGenreFilter()).isEqualTo("[\"爱情\",\"科幻\"]");
        }

        @Test
        @DisplayName("genreFilter=\"[\\\"爱情\\\"]\" → 保持 JSON 数组")
        void saveSchedule_jsonArrayGenreFilter_shouldKeepAsIs() {
            CrawlerSchedule schedule = createBaseSchedule(null);
            schedule.setGenreFilter("[\"爱情\",\"科幻\"]");

            when(scheduleMapper.insert(any())).thenReturn(1);
            scheduleService.saveSchedule(schedule);

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());
            assertThat(captor.getValue().getGenreFilter()).isEqualTo("[\"爱情\",\"科幻\"]");
        }

        @Test
        @DisplayName("genreFilter=\"[]\" → 归一化为 null（空数组等于无筛选）")
        void saveSchedule_emptyJsonArrayGenreFilter_shouldNormalizeToNull() {
            CrawlerSchedule schedule = createBaseSchedule(null);
            schedule.setGenreFilter("[]");

            when(scheduleMapper.insert(any())).thenReturn(1);
            scheduleService.saveSchedule(schedule);

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());
            assertThat(captor.getValue().getGenreFilter()).isNull();
        }

        @Test
        @DisplayName("genreFilter=\"爱情，科幻\"（中文逗号）→ JSON 数组")
        void saveSchedule_chineseCommaGenreFilter_shouldConvertToJsonArray() {
            CrawlerSchedule schedule = createBaseSchedule(null);
            schedule.setGenreFilter("爱情，科幻");

            when(scheduleMapper.insert(any())).thenReturn(1);
            scheduleService.saveSchedule(schedule);

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());
            assertThat(captor.getValue().getGenreFilter()).isEqualTo("[\"爱情\",\"科幻\"]");
        }

        @Test
        @DisplayName("genreFilter=\"非法JSON\" → 当作逗号分隔处理")
        void saveSchedule_invalidJsonGenreFilter_shouldTreatAsCommaSeparated() {
            CrawlerSchedule schedule = createBaseSchedule(null);
            schedule.setGenreFilter("{invalid json}");

            when(scheduleMapper.insert(any())).thenReturn(1);
            scheduleService.saveSchedule(schedule);

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());
            assertThat(captor.getValue().getGenreFilter()).isEqualTo("[\"{invalid json}\"]");
        }
    }

    // ========== TC-040~041: enabled 启用/禁用 ==========

    @Nested
    @DisplayName("TC-040~041: enabled 启用/禁用")
    class ToggleEnabledTest {

        @Test
        @DisplayName("TC-040: 禁用配置 - enabled 设为 0")
        void toggleEnabled_disable_shouldSetEnabledToZero() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setId(1L);
            schedule.setEnabled(1);
            when(scheduleMapper.selectById(1L)).thenReturn(schedule);
            when(scheduleMapper.updateById(any())).thenReturn(1);

            boolean result = scheduleService.toggleEnabled(1L, false);

            assertThat(result).isTrue();
            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).updateById(captor.capture());
            assertThat(captor.getValue().getEnabled()).isEqualTo(0);
        }

        @Test
        @DisplayName("TC-041: 启用配置 - enabled 设为 1")
        void toggleEnabled_enable_shouldSetEnabledToOne() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setId(1L);
            schedule.setEnabled(0);
            when(scheduleMapper.selectById(1L)).thenReturn(schedule);
            when(scheduleMapper.updateById(any())).thenReturn(1);

            boolean result = scheduleService.toggleEnabled(1L, true);

            assertThat(result).isTrue();
            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).updateById(captor.capture());
            assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        }

        @Test
        @DisplayName("toggleEnabled - 配置不存在返回 false")
        void toggleEnabled_notFound_shouldReturnFalse() {
            when(scheduleMapper.selectById(999L)).thenReturn(null);

            boolean result = scheduleService.toggleEnabled(999L, true);

            assertThat(result).isFalse();
            verify(scheduleMapper, never()).updateById(any());
        }
    }

    // ========== TC-050~052: startCrawler/stopCrawler ==========

    @Nested
    @DisplayName("TC-050~052: startCrawler/stopCrawler")
    class StartStopCrawlerTest {

        @Test
        @DisplayName("TC-050: 启动已 idle 的爬虫 - 状态变为 running，任务日志创建")
        void startCrawler_idle_shouldSetRunningAndCreateLog() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setId(1L);
            schedule.setName("电影爬虫");
            schedule.setContentType("movie");
            schedule.setStatus("idle");

            when(scheduleMapper.selectById(1L)).thenReturn(schedule);
            when(scheduleMapper.updateById(any())).thenReturn(1);
            when(taskLogMapper.insert(any())).thenReturn(1);

            boolean result = scheduleService.startCrawler(1L);

            assertThat(result).isTrue();

            // 验证状态更新为 running
            ArgumentCaptor<CrawlerSchedule> scheduleCaptor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).updateById(scheduleCaptor.capture());
            assertThat(scheduleCaptor.getValue().getStatus()).isEqualTo("running");
            assertThat(scheduleCaptor.getValue().getLastRunTime()).isNotNull();

            // 验证任务日志创建
            ArgumentCaptor<CrawlerTaskLog> logCaptor = ArgumentCaptor.forClass(CrawlerTaskLog.class);
            verify(taskLogMapper).insert(logCaptor.capture());
            assertThat(logCaptor.getValue().getScheduleId()).isEqualTo(1L);
            assertThat(logCaptor.getValue().getStatus()).isEqualTo("running");

            // 验证爬虫核心被调用
            verify(crawlerCore).executeCrawl(eq(1L), any(), any(AtomicBoolean.class));
        }

        @Test
        @DisplayName("TC-050 补充: 启动爬虫 - lastRunTime 被设置")
        void startCrawler_shouldSetLastRunTime() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setId(1L);
            schedule.setName("测试");
            schedule.setContentType("movie");
            schedule.setStatus("idle");

            when(scheduleMapper.selectById(1L)).thenReturn(schedule);
            when(scheduleMapper.updateById(any())).thenReturn(1);
            when(taskLogMapper.insert(any())).thenReturn(1);

            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            scheduleService.startCrawler(1L);

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).updateById(captor.capture());
            assertThat(captor.getValue().getLastRunTime()).isAfter(before);
        }

        @Test
        @DisplayName("TC-051: 启动不存在的爬虫 - 返回 false")
        void startCrawler_notFound_shouldReturnFalse() {
            when(scheduleMapper.selectById(999L)).thenReturn(null);

            boolean result = scheduleService.startCrawler(999L);

            assertThat(result).isFalse();
            verify(taskLogMapper, never()).insert(any());
            verify(crawlerCore, never()).executeCrawl(any(), any(), any());
        }

        @Test
        @DisplayName("TC-052: 停止正在运行的爬虫 - 状态变回 idle")
        void stopCrawler_running_shouldSetIdle() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setId(1L);
            schedule.setStatus("running");
            when(scheduleMapper.selectById(1L)).thenReturn(schedule);
            when(scheduleMapper.updateById(any())).thenReturn(1);

            boolean result = scheduleService.stopCrawler(1L);

            assertThat(result).isTrue();
            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("idle");
        }

        @Test
        @DisplayName("TC-052 补充: 停止不存在的爬虫 - 仍返回 true（幂等）")
        void stopCrawler_notFound_shouldStillReturnTrue() {
            when(scheduleMapper.selectById(999L)).thenReturn(null);

            boolean result = scheduleService.stopCrawler(999L);

            // stopCrawler 是幂等操作，不存在也返回 true
            assertThat(result).isTrue();
        }
    }

    // ========== 辅助方法 ==========

    private CrawlerSchedule createBaseSchedule(Long id) {
        CrawlerSchedule s = new CrawlerSchedule();
        s.setId(id);
        s.setName("测试爬虫");
        s.setContentType("movie");
        s.setSourceSite("pkmp4.xyz");
        return s;
    }
}
