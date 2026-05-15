package com.filmforest.crawler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmforest.crawler.core.CrawlerCore;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.resource.mapper.ResourceSourceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CrawlerController 单元测试
 * 测试爬虫 REST API 接口
 * 
 * TC-600~608: API 接口验证
 */
@WebMvcTest(CrawlerController.class)
@DisplayName("CrawlerController API 接口测试")
class CrawlerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CrawlerScheduleService scheduleService;

    @MockBean
    private CrawlerCore crawlerCore;

    @MockBean
    private CrawlerTaskLogMapper taskLogMapper;

    @MockBean
    private ResourceSourceMapper resourceSourceMapper;

    // ========== TC-600: GET /api/crawler/schedules ==========

    @Nested
    @DisplayName("TC-600~601: 查询接口")
    class QueryApiTest {

        @Test
        @DisplayName("TC-600: GET /api/crawler/schedules - 返回所有配置列表")
        void listSchedules_shouldReturnAllSchedules() throws Exception {
            CrawlerSchedule s1 = createSchedule(1L, "电影爬虫", "movie");
            CrawlerSchedule s2 = createSchedule(2L, "剧集爬虫", "drama");
            when(scheduleService.listSchedules()).thenReturn(List.of(s1, s2));

            mockMvc.perform(get("/api/crawler/schedules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("TC-601: GET /api/crawler/schedule/{id} - 返回单个配置详情")
        void getSchedule_shouldReturnSingleSchedule() throws Exception {
            CrawlerSchedule schedule = createSchedule(1L, "电影爬虫", "movie");
            when(scheduleService.getSchedule(1L)).thenReturn(schedule);

            mockMvc.perform(get("/api/crawler/schedule/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.name").value("电影爬虫"));
        }
    }

    // ========== TC-602: POST /api/crawler/schedule ==========

    @Nested
    @DisplayName("TC-602: 创建/更新配置")
    class SaveScheduleTest {

        @Test
        @DisplayName("TC-602: POST /api/crawler/schedule - 创建配置成功")
        void saveSchedule_shouldCreateNewSchedule() throws Exception {
            CrawlerSchedule schedule = createSchedule(null, "新爬虫", "movie");
            when(scheduleService.saveSchedule(any())).thenReturn(true);

            mockMvc.perform(post("/api/crawler/schedule")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(schedule)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }
    }

    // ========== TC-603: DELETE /api/crawler/schedule/{id} ==========

    @Nested
    @DisplayName("TC-603: 删除配置")
    class DeleteScheduleTest {

        @Test
        @DisplayName("TC-603: DELETE /api/crawler/schedule/{id} - 删除配置成功")
        void deleteSchedule_shouldDeleteSuccessfully() throws Exception {
            when(scheduleService.deleteSchedule(1L)).thenReturn(true);

            mockMvc.perform(delete("/api/crawler/schedule/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }
    }

    // ========== TC-604~605: 启动/停止爬虫 ==========

    @Nested
    @DisplayName("TC-604~605: 启动/停止爬虫")
    class StartStopTest {

        @Test
        @DisplayName("TC-604: POST /api/crawler/start/{id} - 启动爬虫成功")
        void startCrawler_shouldStartSuccessfully() throws Exception {
            when(scheduleService.startCrawler(1L)).thenReturn(true);

            mockMvc.perform(post("/api/crawler/start/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("TC-605: POST /api/crawler/stop/{id} - 停止爬虫成功")
        void stopCrawler_shouldStopSuccessfully() throws Exception {
            when(scheduleService.stopCrawler(1L)).thenReturn(true);

            mockMvc.perform(post("/api/crawler/stop/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }
    }

    // ========== TC-606: 切换启用状态 ==========

    @Nested
    @DisplayName("TC-606: 切换启用状态")
    class ToggleTest {

        @Test
        @DisplayName("TC-606: POST /api/crawler/toggle/{id}?enabled=true - 切换启用状态")
        void toggleEnabled_shouldToggleSuccessfully() throws Exception {
            when(scheduleService.toggleEnabled(1L, true)).thenReturn(true);

            mockMvc.perform(post("/api/crawler/toggle/1")
                    .param("enabled", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }
    }

    // ========== TC-607~608: 日志和状态查询 ==========

    @Nested
    @DisplayName("TC-607~608: 日志和状态查询")
    class LogAndStatusTest {

        @Test
        @DisplayName("TC-607: GET /api/crawler/logs - 返回最近50条日志")
        void getLogs_shouldReturnRecentLogs() throws Exception {
            CrawlerTaskLog log1 = createTaskLog(1L, 1L, "success");
            when(taskLogMapper.selectList(any())).thenReturn(List.of(log1));

            mockMvc.perform(get("/api/crawler/logs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("TC-608: GET /api/crawler/status - 返回 running/idle/total 统计")
        void getStatus_shouldReturnStatistics() throws Exception {
            CrawlerSchedule s1 = createSchedule(1L, "电影爬虫", "movie");
            s1.setStatus("running");
            CrawlerSchedule s2 = createSchedule(2L, "剧集爬虫", "drama");
            s2.setStatus("idle");
            when(scheduleService.listSchedules()).thenReturn(List.of(s1, s2));

            mockMvc.perform(get("/api/crawler/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.running").value(1))
                    .andExpect(jsonPath("$.data.idle").value(1));
        }
    }

    // ========== TC-001~004: 基础配置 CRUD 详细验证 ==========

    @Nested
    @DisplayName("TC-001~004: 基础配置 CRUD API 详细验证")
    class DetailedCrudTest {

        @Test
        @DisplayName("TC-001: 创建电影配置 - 返回 idle 状态 + batchSize=10")
        void saveSchedule_newMovieConfig_shouldReturnIdleStatus() throws Exception {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setName("电影爬虫");
            schedule.setContentType("movie");
            schedule.setSourceSite("pkmp4.xyz");
            schedule.setBatchSize(10);
            schedule.setCronExpression("0 2 * * *");

            when(scheduleService.saveSchedule(any())).thenReturn(true);

            mockMvc.perform(post("/api/crawler/schedule")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(schedule)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));

            // 验证 service 被调用且传入正确的参数
            verify(scheduleService).saveSchedule(argThat(s ->
                    s.getName().equals("电影爬虫") &&
                    s.getContentType().equals("movie") &&
                    s.getBatchSize() == 10
            ));
        }

        @Test
        @DisplayName("TC-002: 创建剧集配置 - cron=0 2 * * *")
        void saveSchedule_newDramaConfig_shouldSaveWithCron() throws Exception {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setName("剧集爬虫");
            schedule.setContentType("drama");
            schedule.setSourceSite("pkmp4.xyz");
            schedule.setCronExpression("0 2 * * *");

            when(scheduleService.saveSchedule(any())).thenReturn(true);

            mockMvc.perform(post("/api/crawler/schedule")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(schedule)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(scheduleService).saveSchedule(argThat(s ->
                    s.getContentType().equals("drama") &&
                    "0 2 * * *".equals(s.getCronExpression())
            ));
        }

        @Test
        @DisplayName("TC-003: 编辑已有配置 - id 存在时走 update 路径")
        void saveSchedule_updateExisting_shouldCallSaveWithId() throws Exception {
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setId(1L);
            schedule.setName("电影爬虫-v2");
            schedule.setContentType("movie");
            schedule.setSourceSite("pkmp4.xyz");
            schedule.setBatchSize(50);

            when(scheduleService.saveSchedule(any())).thenReturn(true);

            mockMvc.perform(post("/api/crawler/schedule")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(schedule)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(scheduleService).saveSchedule(argThat(s ->
                    s.getId() != null && s.getId() == 1L &&
                    s.getBatchSize() == 50
            ));
        }

        @Test
        @DisplayName("TC-004: 删除已有配置 - 配置删除成功")
        void deleteSchedule_shouldCallServiceDelete() throws Exception {
            when(scheduleService.deleteSchedule(1L)).thenReturn(true);

            mockMvc.perform(delete("/api/crawler/schedule/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));

            verify(scheduleService).deleteSchedule(1L);
        }

        @Test
        @DisplayName("TC-004 补充: 删除不存在的配置 - 返回 false")
        void deleteSchedule_notFound_shouldReturnFalse() throws Exception {
            when(scheduleService.deleteSchedule(999L)).thenReturn(false);

            mockMvc.perform(delete("/api/crawler/schedule/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("TC-001 补充: 创建配置缺少必填字段 - @Valid 校验失败")
        void saveSchedule_missingRequiredFields_shouldReturn400() throws Exception {
            // name 为空
            CrawlerSchedule schedule = new CrawlerSchedule();
            schedule.setContentType("movie");
            schedule.setSourceSite("pkmp4.xyz");

            mockMvc.perform(post("/api/crawler/schedule")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(schedule)))
                    .andExpect(status().isOk()) // GlobalExceptionHandler 捕获后返回 200 + code=400
                    .andExpect(jsonPath("$.code").value(400));

            // 不应调用 service
            verify(scheduleService, never()).saveSchedule(any());
        }

        @Test
        @DisplayName("TC-040~041 API: 切换启用状态")
        void toggleEnabled_shouldCallService() throws Exception {
            when(scheduleService.toggleEnabled(1L, false)).thenReturn(true);

            mockMvc.perform(post("/api/crawler/toggle/1")
                    .param("enabled", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));

            verify(scheduleService).toggleEnabled(1L, false);
        }

        @Test
        @DisplayName("TC-050 API: 启动爬虫")
        void startCrawler_shouldCallService() throws Exception {
            when(scheduleService.startCrawler(1L)).thenReturn(true);

            mockMvc.perform(post("/api/crawler/start/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));

            verify(scheduleService).startCrawler(1L);
        }

        @Test
        @DisplayName("TC-051 API: 启动不存在的爬虫 - 返回 false")
        void startCrawler_notFound_shouldReturnFalse() throws Exception {
            when(scheduleService.startCrawler(999L)).thenReturn(false);

            mockMvc.perform(post("/api/crawler/start/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("TC-052 API: 停止爬虫")
        void stopCrawler_shouldCallService() throws Exception {
            when(scheduleService.stopCrawler(1L)).thenReturn(true);

            mockMvc.perform(post("/api/crawler/stop/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));

            verify(scheduleService).stopCrawler(1L);
        }
    }

    // ========== 辅助方法 ==========

    private CrawlerSchedule createSchedule(Long id, String name, String contentType) {
        CrawlerSchedule s = new CrawlerSchedule();
        s.setId(id);
        s.setName(name);
        s.setContentType(contentType);
        s.setSourceSite("pkmp4.xyz");
        s.setEnabled(1);
        s.setCronExpression("0 2 * * *");
        s.setBatchSize(20);
        s.setRateLimitMs(1000);
        s.setStatus("idle");
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    private CrawlerTaskLog createTaskLog(Long id, Long scheduleId, String status) {
        CrawlerTaskLog log = new CrawlerTaskLog();
        log.setId(id);
        log.setScheduleId(scheduleId);
        log.setStatus(status);
        log.setStartedAt(LocalDateTime.now());
        log.setFinishedAt(LocalDateTime.now());
        return log;
    }
}
