package com.filmforest.crawler.controller;

import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.entity.CrawlerJobItemFailure;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.dto.CrawlerJobStartResult;
import com.filmforest.common.dto.PageResult;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerScheduleDefinitionService;
import com.filmforest.crawler.service.CrawlerItemFailureService;
import com.filmforest.crawler.service.CrawlerOperationsQueryService;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerSourceCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
@DisplayName("Crawler Job API 契约")
class CrawlerControllerJobApiTest {

    @Mock private CrawlerScheduleService scheduleService;
    @Mock private CrawlerOperationsQueryService operationsQueryService;
    @Mock private CrawlerTaskLogMapper jobMapper;
    @Mock private CrawlerSourceCatalogService sourceCatalogService;
    @Mock private CrawlerScheduleDefinitionService scheduleDefinitionService;
    @Mock private CrawlerItemFailureService itemFailureService;

    private CrawlerController controller;

    @BeforeEach
    void setUp() {
        controller = new CrawlerController(
                scheduleService, operationsQueryService, jobMapper,
                sourceCatalogService, scheduleDefinitionService, itemFailureService);
    }

    @Test
    @DisplayName("按 Job ID 查询返回权威 Job")
    void getJob_shouldReturnJob() {
        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setId(9L);
        job.setStatus("running");
        when(jobMapper.selectById(9L)).thenReturn(job);

        var result = controller.getJob(9L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(job);
    }

    @Test
    @DisplayName("按 Job ID 取消只委托生命周期服务")
    void cancelJob_shouldDelegateByJobId() {
        when(scheduleService.cancelJob(9L)).thenReturn(true);

        var result = controller.cancelJob(9L);

        assertThat(result.getData()).isTrue();
        verify(scheduleService).cancelJob(9L);
    }

    @Test
    @DisplayName("活动 Job 查询由服务端状态过滤")
    void listActiveJobs_shouldUseServerSideQuery() {
        when(jobMapper.selectActiveJobs()).thenReturn(java.util.List.of());

        var result = controller.listActiveJobs();

        assertThat(result.getData()).isEmpty();
        verify(jobMapper).selectActiveJobs();
    }

    @Test
    @DisplayName("手工启动返回入库 Job 的 ID、queued 状态和权威排队时间")
    void startCrawler_shouldReturnAuthoritativeJobSummary() {
        CrawlerJobStartResult created = new CrawlerJobStartResult(
                101L, "queued", LocalDateTime.of(2026, 8, 11, 2, 3, 4));
        when(scheduleService.startCrawler(7L)).thenReturn(created);

        var result = controller.startCrawler(7L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(created);
        verify(scheduleService).startCrawler(7L);
    }

    @Test
    @DisplayName("活动 Job 冲突返回明确业务错误，不伪造 Job ID")
    void startCrawler_activeConflict_shouldReturnBusinessError() {
        when(scheduleService.startCrawler(7L)).thenThrow(
                new com.filmforest.common.exception.BusinessException(409, "该爬虫配置已有活动 Job，不能重复启动"));

        var result = controller.startCrawler(7L);

        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMessage()).contains("活动 Job");
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("重试返回新 Job ID，且不返回原 Job ID")
    void retryTask_shouldReturnNewJobSummary() {
        CrawlerTaskLog previous = new CrawlerTaskLog();
        previous.setId(88L);
        previous.setScheduleId(7L);
        previous.setStatus("failed");
        when(jobMapper.selectById(88L)).thenReturn(previous);
        when(scheduleService.getSchedule(7L)).thenReturn(new CrawlerSchedule());
        CrawlerJobStartResult retried = new CrawlerJobStartResult(
                101L, "queued", LocalDateTime.of(2026, 8, 11, 2, 3, 4));
        when(scheduleService.retryCrawler(88L)).thenReturn(retried);

        var result = controller.retryTask(88L);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().jobId()).isEqualTo(101L);
        assertThat(result.getData().jobId()).isNotEqualTo(previous.getId());
    }

    @Test
    @DisplayName("不可重试状态返回业务错误且不创建新 Job")
    void retryTask_nonRetryableStatus_shouldReject() {
        CrawlerTaskLog running = new CrawlerTaskLog();
        running.setId(88L);
        running.setScheduleId(7L);
        running.setStatus("running");
        when(jobMapper.selectById(88L)).thenReturn(running);

        var result = controller.retryTask(88L);

        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMessage()).contains("不支持重试");
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("批量重试只消费数据库返回的最新可重试 Job，并报告上限外与冲突项")
    void retryAll_shouldUseBoundedLatestAuthoritativeJobs() {
        CrawlerTaskLog first = new CrawlerTaskLog();
        first.setId(81L);
        first.setScheduleId(7L);
        first.setStatus("failed");
        CrawlerTaskLog second = new CrawlerTaskLog();
        second.setId(82L);
        second.setScheduleId(8L);
        second.setStatus("interrupted");
        when(jobMapper.countLatestRetryableJobs()).thenReturn(3L);
        when(jobMapper.selectLatestRetryableJobs(100)).thenReturn(List.of(first, second));
        when(scheduleService.retryCrawler(81L)).thenReturn(new CrawlerJobStartResult(
                181L, "queued", LocalDateTime.of(2026, 8, 11, 2, 3, 4)));
        when(scheduleService.retryCrawler(82L)).thenThrow(
                new com.filmforest.common.exception.BusinessException(409, "已有活动 Job"));

        var result = controller.retryAllFailed();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("total", 3L);
        assertThat(result.getData()).containsEntry("started", 1);
        assertThat(result.getData()).containsEntry("startedJobIds", List.of(181L));
        assertThat(result.getData()).containsEntry("skipped", 2L);
        verify(jobMapper).selectLatestRetryableJobs(100);
        verify(jobMapper, never()).selectLatestByScheduleId(anyLong());
        verify(jobMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("没有最新可重试 Job 时不加载历史日志")
    void retryAll_withoutCandidates_shouldReturnEmptySummary() {
        when(jobMapper.countLatestRetryableJobs()).thenReturn(0L);

        var result = controller.retryAllFailed();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("started", 0);
        verify(jobMapper, never()).selectLatestRetryableJobs(anyInt());
        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("失败明细查询先校验 Job，并返回服务端分页结果")
    void listJobFailures_shouldValidateJobAndReturnPage() {
        CrawlerTaskLog job = new CrawlerTaskLog();
        job.setId(9L);
        when(jobMapper.selectById(9L)).thenReturn(job);
        CrawlerJobItemFailure failure = new CrawlerJobItemFailure();
        failure.setJobId(9L);
        PageResult<CrawlerJobItemFailure> page = new PageResult<>(List.of(failure), 1, 20, 1, 1);
        when(itemFailureService.listFailures(9L, "fetch", "SERVER_ERROR", true, 1, 20))
                .thenReturn(page);

        var result = controller.listJobFailures(9L, "fetch", "SERVER_ERROR", true, 1, 20);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(page);
        verify(itemFailureService).listFailures(9L, "fetch", "SERVER_ERROR", true, 1, 20);
    }

    @Test
    @DisplayName("不存在 Job 不访问失败明细表")
    void listJobFailures_missingJob_shouldReturnNotFound() {
        when(jobMapper.selectById(404L)).thenReturn(null);

        var result = controller.listJobFailures(404L, null, null, null, 1, 20);

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMessage()).contains("Job 不存在");
        verifyNoInteractions(itemFailureService);
    }
}
