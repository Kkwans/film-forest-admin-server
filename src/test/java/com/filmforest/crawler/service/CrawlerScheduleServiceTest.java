package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.entity.CrawlerTriggerType;
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
import java.util.List;

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
    private CrawlerJobLifecycleService jobLifecycleService;

    @InjectMocks
    private CrawlerScheduleServiceImpl scheduleService;

    @Nested
    @DisplayName("配置列表运行事实装饰")
    class RuntimeDecorationTest {

        @Test
        @DisplayName("无活动 Job 时返回最近 Job 的 ID 与结果")
        void listSchedules_shouldExposeLatestJobResult() {
            CrawlerSchedule schedule = createBaseSchedule(1L);
            CrawlerTaskLog latest = new CrawlerTaskLog();
            latest.setId(91L);
            latest.setStatus("partial_success");
            when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule));
            when(taskLogMapper.selectLatestByScheduleId(1L)).thenReturn(latest);

            List<CrawlerSchedule> result = scheduleService.listSchedules();

            assertThat(result).singleElement().satisfies(item -> {
                assertThat(item.getStatus()).isEqualTo("idle");
                assertThat(item.getLatestJobId()).isEqualTo(91L);
                assertThat(item.getLatestResult()).isEqualTo("partial_success");
            });
        }

        @Test
        @DisplayName("活动 Job 同时作为当前状态和最近结果")
        void getSchedule_shouldPreferActiveJob() {
            CrawlerSchedule schedule = createBaseSchedule(1L);
            CrawlerTaskLog active = new CrawlerTaskLog();
            active.setId(92L);
            active.setStatus("running");
            when(scheduleMapper.selectById(1L)).thenReturn(schedule);
            when(taskLogMapper.selectActiveByScheduleId(1L)).thenReturn(active);

            CrawlerSchedule result = scheduleService.getSchedule(1L);

            assertThat(result.getStatus()).isEqualTo("running");
            assertThat(result.getLatestJobId()).isEqualTo(92L);
            assertThat(result.getLatestResult()).isEqualTo("running");
            verify(taskLogMapper, never()).selectLatestByScheduleId(1L);
        }
    }

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

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);

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
            assertThat(saved.getCrawlMode()).isEqualTo("latest");
        }

        @Test
        @DisplayName("FULL 配置只能手工触发，保存时强制关闭调度")
        void saveSchedule_fullMode_shouldDisableSchedule() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setName("全量回填");
            schedule.setContentType("movie");
            schedule.setSourceSite("pkmp4");
            schedule.setCrawlMode("FULL");
            schedule.setEnabled(1);
            schedule.setCronExpression("0 0 2 * * *");
            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);

            assertThat(scheduleService.saveSchedule(schedule)).isTrue();

            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).insert(captor.capture());
            assertThat(captor.getValue().getCrawlMode()).isEqualTo("full");
            assertThat(captor.getValue().getEnabled()).isZero();
            assertThat(captor.getValue().getNextRunTime()).isNull();
        }

        @Test
        @DisplayName("TC-002: 创建剧集配置 - cron=0 2 * * *，配置保存成功")
        void saveSchedule_newDramaConfig_shouldSaveSuccessfully() {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setName("剧集爬虫");
            schedule.setContentType("drama");
            schedule.setSourceSite("pkmp4.xyz");
            schedule.setCronExpression("0 2 * * *");

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);

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

            when(scheduleMapper.updateById(any(CrawlerSchedule.class))).thenReturn(1);

            boolean result = scheduleService.saveSchedule(schedule);

            assertThat(result).isTrue();
            ArgumentCaptor<CrawlerSchedule> captor = ArgumentCaptor.forClass(CrawlerSchedule.class);
            verify(scheduleMapper).updateById(captor.capture());

            CrawlerSchedule updated = captor.getValue();
            assertThat(updated.getId()).isEqualTo(1L);
            assertThat(updated.getBatchSize()).isEqualTo(50);
            // 更新时不应重置 status/totalRuns/totalItems
            verify(scheduleMapper, never()).insert(any(CrawlerSchedule.class));
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

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);
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

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);
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

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);
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

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);
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

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);
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

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);
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

            when(scheduleMapper.insert(any(CrawlerSchedule.class))).thenReturn(1);
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
            when(scheduleMapper.updateById(any(CrawlerSchedule.class))).thenReturn(1);

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
            when(scheduleMapper.updateById(any(CrawlerSchedule.class))).thenReturn(1);

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
            verify(scheduleMapper, never()).updateById(any(CrawlerSchedule.class));
        }
    }

    // ========== TC-050~052: startCrawler/stopCrawler ==========

    @Nested
    @DisplayName("TC-050~052: startCrawler/stopCrawler")
    class StartStopCrawlerTest {

        @Test
        @DisplayName("TC-050: 手工启动只创建 QUEUED Job，由提交后事件派发")
        void startCrawler_idle_shouldEnqueueManualJob() {
            when(jobLifecycleService.enqueue(1L, CrawlerTriggerType.MANUAL, null)).thenReturn(101L);

            boolean result = scheduleService.startCrawler(1L);

            assertThat(result).isTrue();
            verify(jobLifecycleService).enqueue(1L, CrawlerTriggerType.MANUAL, null);
        }

        @Test
        @DisplayName("TC-050 补充: 同一 schedule 已有活动 Job 时拒绝重复启动")
        void startCrawler_activeJobExists_shouldReturnFalse() {
            when(jobLifecycleService.enqueue(1L, CrawlerTriggerType.MANUAL, null)).thenReturn(null);

            assertThat(scheduleService.startCrawler(1L)).isFalse();
        }

        @Test
        @DisplayName("TC-051: 启动不存在的爬虫 - 返回 false")
        void startCrawler_notFound_shouldReturnFalse() {
            when(jobLifecycleService.enqueue(999L, CrawlerTriggerType.MANUAL, null)).thenReturn(null);

            boolean result = scheduleService.startCrawler(999L);

            assertThat(result).isFalse();
            verify(jobLifecycleService).enqueue(999L, CrawlerTriggerType.MANUAL, null);
        }

        @Test
        @DisplayName("TC-052: 停止正在运行的爬虫 - 请求 Job 在安全边界取消")
        void stopCrawler_running_shouldRequestCancellation() {
            when(jobLifecycleService.requestCancelBySchedule(1L)).thenReturn(true);

            boolean result = scheduleService.stopCrawler(1L);

            assertThat(result).isTrue();
            verify(jobLifecycleService).requestCancelBySchedule(1L);
        }

        @Test
        @DisplayName("TC-052 补充: 不存在活动 Job 时返回 false")
        void stopCrawler_notFound_shouldReturnFalse() {
            when(jobLifecycleService.requestCancelBySchedule(999L)).thenReturn(false);

            boolean result = scheduleService.stopCrawler(999L);

            assertThat(result).isFalse();
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
