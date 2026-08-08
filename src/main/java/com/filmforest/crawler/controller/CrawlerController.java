package com.filmforest.crawler.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.common.dto.PageResult;
import com.filmforest.crawler.dto.CrawlerOperationsStats;
import com.filmforest.crawler.dto.CrawlerSourceDescriptor;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerStatus;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerOperationsQueryService;
import com.filmforest.crawler.service.CrawlerTime;
import com.filmforest.crawler.source.SourceAdapterRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private static final Logger log = LoggerFactory.getLogger(CrawlerController.class);

    private final CrawlerScheduleService scheduleService;
    private final CrawlerOperationsQueryService operationsQueryService;
    private final CrawlerTaskLogMapper taskLogMapper;
    private final SourceAdapterRegistry sourceAdapterRegistry;

    public CrawlerController(CrawlerScheduleService scheduleService,
                             CrawlerOperationsQueryService operationsQueryService,
                             CrawlerTaskLogMapper taskLogMapper,
                             SourceAdapterRegistry sourceAdapterRegistry) {
        this.scheduleService = scheduleService;
        this.operationsQueryService = operationsQueryService;
        this.taskLogMapper = taskLogMapper;
        this.sourceAdapterRegistry = sourceAdapterRegistry;
    }

    /** 获取所有定时配置 */
    @GetMapping("/schedules")
    public Result<List<CrawlerSchedule>> listSchedules() {
        return Result.ok(scheduleService.listSchedules());
    }

    /** 获取单个配置 */
    @GetMapping("/schedule/{id}")
    public Result<CrawlerSchedule> getSchedule(@PathVariable Long id) {
        return Result.ok(scheduleService.getSchedule(id));
    }

    /** 保存/更新配置 */
    @PostMapping("/schedule")
    public Result<Boolean> saveSchedule(@Valid @RequestBody CrawlerSchedule schedule) {
        boolean saved = scheduleService.saveSchedule(schedule);
        log.info("保存爬虫配置: id={}, name={}", schedule.getId(), schedule.getName());
        return Result.ok(saved);
    }

    /** 删除配置 */
    @DeleteMapping("/schedule/{id}")
    public Result<Boolean> deleteSchedule(@PathVariable Long id) {
        log.info("删除爬虫配置: id={}", id);
        return Result.ok(scheduleService.deleteSchedule(id));
    }

    /** 启动爬虫 */
    @PostMapping("/start/{id}")
    public Result<Boolean> startCrawler(@PathVariable Long id) {
        log.info("启动爬虫: scheduleId={}", id);
        return Result.ok(scheduleService.startCrawler(id));
    }

    /** 停止爬虫 */
    @PostMapping("/stop/{id}")
    public Result<Boolean> stopCrawler(@PathVariable Long id) {
        log.info("停止爬虫: scheduleId={}", id);
        return Result.ok(scheduleService.stopCrawler(id));
    }

    /** 获取单个权威 Job。 */
    @GetMapping("/jobs/{jobId}")
    public Result<CrawlerTaskLog> getJob(@PathVariable Long jobId) {
        CrawlerTaskLog job = taskLogMapper.selectById(jobId);
        return job == null ? Result.fail("爬虫 Job 不存在") : Result.ok(job);
    }

    /** 获取全部活动 Job。 */
    @GetMapping("/jobs/active")
    public Result<List<CrawlerTaskLog>> listActiveJobs() {
        return Result.ok(taskLogMapper.selectActiveJobs());
    }

    /** 按 Job ID 请求取消；运行中 Job 会完成当前内容项后退出。 */
    @PostMapping("/jobs/{jobId}/cancel")
    public Result<Boolean> cancelJob(@PathVariable Long jobId) {
        log.info("请求取消爬虫 Job: jobId={}", jobId);
        return Result.ok(scheduleService.cancelJob(jobId));
    }

    /** 切换启用状态 */
    @PostMapping("/toggle/{id}")
    public Result<Boolean> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        log.info("切换爬虫状态: scheduleId={}, enabled={}", id, enabled);
        return Result.ok(scheduleService.toggleEnabled(id, enabled));
    }

    /** 权威 Job/日志的服务端真分页与筛选。 */
    @GetMapping("/logs")
    public Result<PageResult<CrawlerTaskLog>> listLogs(
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(operationsQueryService.listJobs(
                status, scheduleId, source, type, triggerType, from, to, keyword, page, size));
    }

    /** 7/30 天 Job 汇总、每日趋势和来源健康度，全部由 SQL 聚合。 */
    @GetMapping("/operations-stats")
    public Result<CrawlerOperationsStats> getOperationsStats(
            @RequestParam(required = false, defaultValue = "7") int days) {
        return Result.ok(operationsQueryService.getOperationsStats(days));
    }

    /** 获取资源来源列表（爬虫配置用） */
    @GetMapping("/sources")
    public Result<List<CrawlerSourceDescriptor>> listSources() {
        return Result.ok(sourceAdapterRegistry.availableAdapters().stream()
                .map(adapter -> new CrawlerSourceDescriptor(adapter.sourceCode(), adapter.displayName()))
                .toList());
    }

    /** 获取爬虫每日运行趋势（近7天） */
    @GetMapping("/daily-stats")
    public Result<List<Map<String, Object>>> getDailyStats() {
        LocalDate today = CrawlerTime.todayInScheduleZone();
        LocalDate startDate = today.minusDays(6); // 近7天

        LambdaQueryWrapper<CrawlerTaskLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(CrawlerTaskLog::getStartedAt, CrawlerTime.startOfScheduleDayUtc(startDate))
               .lt(CrawlerTaskLog::getStartedAt, CrawlerTime.startOfScheduleDayUtc(today.plusDays(1)))
               .orderByAsc(CrawlerTaskLog::getStartedAt);

        List<CrawlerTaskLog> logs = taskLogMapper.selectList(wrapper);

        // 按日期分组统计
        Map<LocalDate, List<CrawlerTaskLog>> byDate = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> CrawlerTime.toScheduleDate(log.getStartedAt()),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<CrawlerTaskLog> dayLogs = byDate.getOrDefault(date, Collections.emptyList());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", date.toString());
            entry.put("dateLabel", date.getMonthValue() + "/" + date.getDayOfMonth());
            entry.put("runs", dayLogs.size());
            entry.put("items", dayLogs.stream().mapToInt(l -> l.getItemsCrawled() != null ? l.getItemsCrawled() : 0).sum());
            entry.put("added", dayLogs.stream().mapToInt(l -> l.getItemsAdded() != null ? l.getItemsAdded() : 0).sum());
            entry.put("updated", dayLogs.stream().mapToInt(l -> l.getItemsUpdated() != null ? l.getItemsUpdated() : 0).sum());
            result.add(entry);
        }
        return Result.ok(result);
    }

    /** 重试失败/停止的任务 */
    @PostMapping("/retry/{logId}")
    public Result<String> retryTask(@PathVariable Long logId) {
        CrawlerTaskLog taskLog = taskLogMapper.selectById(logId);
        if (taskLog == null) {
            return Result.fail("任务日志不存在");
        }
        CrawlerStatus status = CrawlerStatus.fromCode(taskLog.getStatus());
        if (status == null || !status.isRetryable()) {
            return Result.fail("当前状态不支持重试: " + taskLog.getStatus());
        }
        CrawlerSchedule schedule = scheduleService.getSchedule(taskLog.getScheduleId());
        if (schedule == null) {
            return Result.fail("关联的爬虫配置已不存在");
        }
        log.info("重试爬虫任务: logId={}, scheduleId={}, scheduleName={}",
                logId, taskLog.getScheduleId(), schedule.getName());
        boolean started = scheduleService.retryCrawler(logId);
        if (started) {
            return Result.ok("重试任务已启动");
        } else {
            return Result.fail("重试启动失败");
        }
    }

    /** 批量重试所有失败任务 */
    @PostMapping("/retry-all")
    public Result<Map<String, Object>> retryAllFailed() {
        LambdaQueryWrapper<CrawlerTaskLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CrawlerTaskLog::getStatus,
                CrawlerStatus.FAILED.getCode(),
                CrawlerStatus.PARTIAL_SUCCESS.getCode(),
                CrawlerStatus.CANCELLED.getCode(),
                CrawlerStatus.INTERRUPTED.getCode());
        List<CrawlerTaskLog> failedLogs = taskLogMapper.selectList(wrapper);

        if (failedLogs.isEmpty()) {
            return Result.ok(Map.of("total", 0, "started", 0, "skipped", 0, "message", "没有需要重试的任务"));
        }

        // 按 scheduleId 去重（同一个配置只重试一次）
        Set<Long> scheduleIds = failedLogs.stream()
                .map(CrawlerTaskLog::getScheduleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int started = 0;
        int skipped = 0;
        for (Long scheduleId : scheduleIds) {
            CrawlerSchedule schedule = scheduleService.getSchedule(scheduleId);
            if (schedule == null) {
                skipped++;
                continue;
            }
            CrawlerTaskLog latest = failedLogs.stream()
                    .filter(job -> scheduleId.equals(job.getScheduleId()))
                    .max(Comparator.comparing(CrawlerTaskLog::getId))
                    .orElse(null);
            boolean ok = latest != null && scheduleService.retryCrawler(latest.getId());
            if (ok) started++;
            else skipped++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", failedLogs.size());
        result.put("schedules", scheduleIds.size());
        result.put("started", started);
        result.put("skipped", skipped);
        return Result.ok(result);
    }

    /** 获取任务日志统计（各状态数量） */
    @GetMapping("/logs/stats")
    public Result<Map<String, Object>> getLogStats() {
        List<CrawlerTaskLog> allLogs = taskLogMapper.selectList(
            new LambdaQueryWrapper<CrawlerTaskLog>()
                .ge(CrawlerTaskLog::getStartedAt, CrawlerTime.nowUtc().minusDays(30))
        );
        Map<String, Long> statusCounts = allLogs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getStatus() != null ? l.getStatus() : "unknown",
                        Collectors.counting()
                ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", allLogs.size());
        result.put("byStatus", statusCounts);
        result.put("recentFailed", allLogs.stream()
                .filter(l -> "failed".equals(l.getStatus()))
                .limit(5)
                .collect(Collectors.toList()));
        return Result.ok(result);
    }

    /** 获取爬虫状态概览 */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        List<CrawlerSchedule> schedules = scheduleService.listSchedules();
        Map<String, Object> status = new HashMap<>();
        status.put("schedules", schedules);
        status.put("total", schedules.size());
        status.put("running", schedules.stream().filter(s -> "running".equals(s.getStatus())).count());
        status.put("idle", schedules.stream().filter(s -> "idle".equals(s.getStatus())).count());
        return Result.ok(status);
    }
}
