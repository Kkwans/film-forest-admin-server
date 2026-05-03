package com.filmforest.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.content.entity.Movie;
import com.filmforest.content.service.MovieService;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.service.CrawlerService;
import com.filmforest.crawler.source.QiweiCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerServiceImpl implements CrawlerService {

    private final CrawlerScheduleMapper scheduleMapper;
    private final QiweiCrawler qiweiCrawler;
    private final MovieService movieService;

    @Override
    public List<CrawlerSchedule> listSchedules() {
        return scheduleMapper.selectList(new LambdaQueryWrapper<CrawlerSchedule>()
                .orderByDesc(CrawlerSchedule::getUpdatedAt));
    }

    @Override
    public CrawlerSchedule getSchedule(Long id) {
        return scheduleMapper.selectById(id);
    }

    @Override
    public boolean saveSchedule(CrawlerSchedule schedule) {
        if (schedule.getId() != null) {
            return scheduleMapper.updateById(schedule) > 0;
        } else {
            return scheduleMapper.insert(schedule) > 0;
        }
    }

    @Override
    public boolean deleteSchedule(Long id) {
        return scheduleMapper.deleteById(id) > 0;
    }

    @Override
    @Async
    public boolean startCrawler(Long id) {
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) return false;

        schedule.setStatus("running");
        schedule.setTotalRuns(schedule.getTotalRuns() == null ? 1 : schedule.getTotalRuns() + 1);
        schedule.setLastRunTime(java.time.LocalDateTime.now());
        scheduleMapper.updateById(schedule);

        try {
            log.info("[爬虫] 开始执行任务: {}", schedule.getName());
            String sourceSite = schedule.getSourceSite();

            if ("qiwei".equalsIgnoreCase(sourceSite) || "七味网".equals(sourceSite)) {
                qiweiCrawler.crawlMovies(schedule);
            } else {
                // 默认走七味网
                qiweiCrawler.crawlMovies(schedule);
            }

            schedule.setStatus("idle");
            schedule.setNextRunTime(calculateNextRunTime(schedule.getCronExpression()));
            scheduleMapper.updateById(schedule);
            log.info("[爬虫] 任务完成: {}", schedule.getName());
            return true;

        } catch (Exception e) {
            log.error("[爬虫] 任务执行异常: {}, error: {}", schedule.getName(), e.getMessage());
            schedule.setStatus("idle");
            scheduleMapper.updateById(schedule);
            return false;
        }
    }

    @Override
    public boolean stopCrawler(Long id) {
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) return false;
        schedule.setStatus("idle");
        return scheduleMapper.updateById(schedule) > 0;
    }

    @Override
    public boolean toggleEnabled(Long id, boolean enabled) {
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) return false;
        schedule.setEnabled(enabled ? 1 : 0);
        return scheduleMapper.updateById(schedule) > 0;
    }

    @Override
    public Object getStatus() {
        List<CrawlerSchedule> schedules = listSchedules();
        Map<String, Object> result = new HashMap<>();
        result.put("schedules", schedules);
        result.put("total", schedules.size());
        result.put("running", schedules.stream().filter(s -> "running".equals(s.getStatus())).count());
        result.put("idle", schedules.stream().filter(s -> "idle".equals(s.getStatus())).count());
        return result;
    }

    /**
     * 根据Cron表达式计算下次运行时间（简单版）
     */
    private java.time.LocalDateTime calculateNextRunTime(String cronExpression) {
        if (cronExpression == null || cronExpression.isEmpty()) {
            return java.time.LocalDateTime.now().plusHours(6);
        }
        try {
            // 简单解析：支持 "0 0 * * * *" 等标准cron
            String[] parts = cronExpression.trim().split("\\s+");
            java.time.LocalDateTime next = java.time.LocalDateTime.now();
            if (parts.length >= 2) {
                // 最少解析到分钟
                int minute = Integer.parseInt(parts[1]);
                int hour = parts.length >= 2 ? Integer.parseInt(parts[2]) : 0;
                next = next.plusHours(hour - next.getHour()).plusMinutes(minute - next.getMinute());
                if (next.isBefore(java.time.LocalDateTime.now())) {
                    next = next.plusDays(1);
                }
            }
            return next;
        } catch (Exception e) {
            return java.time.LocalDateTime.now().plusHours(6);
        }
    }
}
