package com.filmforest.crawler.controller;

import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerScheduleDefinitionService;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("Crawler Job API 契约")
class CrawlerControllerJobApiTest {

    @Mock private CrawlerScheduleService scheduleService;
    @Mock private CrawlerOperationsQueryService operationsQueryService;
    @Mock private CrawlerTaskLogMapper jobMapper;
    @Mock private CrawlerSourceCatalogService sourceCatalogService;
    @Mock private CrawlerScheduleDefinitionService scheduleDefinitionService;

    private CrawlerController controller;

    @BeforeEach
    void setUp() {
        controller = new CrawlerController(
                scheduleService, operationsQueryService, jobMapper,
                sourceCatalogService, scheduleDefinitionService);
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
}
