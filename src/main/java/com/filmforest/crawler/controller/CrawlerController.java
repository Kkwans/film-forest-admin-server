package com.filmforest.crawler.controller;

import com.filmforest.common.dto.Result;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.resource.entity.ResourceSource;
import com.filmforest.resource.mapper.ResourceSourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private static final Logger log = LoggerFactory.getLogger(CrawlerController.class);

    @Autowired
    private CrawlerScheduleService scheduleService;

    @Autowired
    private CrawlerTaskLogMapper taskLogMapper;

    @Autowired
    private ResourceSourceMapper resourceSourceMapper;

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

    /** 切换启用状态 */
    @PostMapping("/toggle/{id}")
    public Result<Boolean> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        log.info("切换爬虫状态: scheduleId={}, enabled={}", id, enabled);
        return Result.ok(scheduleService.toggleEnabled(id, enabled));
    }

    /** 获取任务日志 */
    @GetMapping("/logs")
    public Result<List<CrawlerTaskLog>> listLogs(@RequestParam(required = false) Long scheduleId) {
        LambdaQueryWrapper<CrawlerTaskLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(CrawlerTaskLog::getStartedAt).last("LIMIT 50");
        if (scheduleId != null) {
            wrapper.eq(CrawlerTaskLog::getScheduleId, scheduleId);
        }
        return Result.ok(taskLogMapper.selectList(wrapper));
    }

    /** 获取资源来源列表（爬虫配置用） */
    @GetMapping("/sources")
    public Result<List<ResourceSource>> listSources() {
        return Result.ok(resourceSourceMapper.selectList(
            new LambdaQueryWrapper<ResourceSource>()
                .eq(ResourceSource::getEnabled, 1)
                .orderByAsc(ResourceSource::getSort)
        ));
    }

    /** 获取爬虫每日运行趋势（近7天） */
    @GetMapping("/daily-stats")
    public Result<List<Map<String, Object>>> getDailyStats() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6); // 近7天

        LambdaQueryWrapper<CrawlerTaskLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(CrawlerTaskLog::getStartedAt, startDate.atStartOfDay())
               .le(CrawlerTaskLog::getStartedAt, today.plusDays(1).atStartOfDay())
               .orderByAsc(CrawlerTaskLog::getStartedAt);

        List<CrawlerTaskLog> logs = taskLogMapper.selectList(wrapper);

        // 按日期分组统计
        Map<LocalDate, List<CrawlerTaskLog>> byDate = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getStartedAt().toLocalDate(),
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