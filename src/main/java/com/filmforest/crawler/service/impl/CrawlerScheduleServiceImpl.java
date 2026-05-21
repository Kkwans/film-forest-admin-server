package com.filmforest.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.crawler.core.CrawlerCore;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerStatus;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class CrawlerScheduleServiceImpl implements CrawlerScheduleService {

    @Autowired
    private CrawlerScheduleMapper scheduleMapper;

    @Autowired
    private CrawlerTaskLogMapper taskLogMapper;

    @Lazy @Autowired
    private CrawlerCore crawlerCore;

    /** 正在运行的爬虫任务 */
    private final ConcurrentHashMap<Long, AtomicBoolean> runningTasks = new ConcurrentHashMap<>();

    @Override
    public List<CrawlerSchedule> listSchedules() {
        return scheduleMapper.selectList(new LambdaQueryWrapper<CrawlerSchedule>()
                .orderByDesc(CrawlerSchedule::getCreatedAt));
    }

    @Override
    public CrawlerSchedule getSchedule(Long id) {
        return scheduleMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean saveSchedule(CrawlerSchedule schedule) {
        // 修复 #6: genreFilter 是 JSON 列，空字符串/null/非法值统一转为 null
        // 合法格式: 逗号分隔的中文标签 "爱情,科幻" 或 JSON 数组 "[\"爱情\",\"科幻\"]"
        schedule.setGenreFilter(normalizeGenreFilter(schedule.getGenreFilter()));

        if (schedule.getId() == null) {
            schedule.setStatus(CrawlerStatus.IDLE.getCode());
            schedule.setTotalRuns(0);
            schedule.setTotalItems(0);
            // 计算 nextRunTime
            if (schedule.getEnabled() != null && schedule.getEnabled() == 1
                    && schedule.getCronExpression() != null && !schedule.getCronExpression().isEmpty()) {
                schedule.setNextRunTime(computeNextRunTime(schedule.getCronExpression()));
            }
            return scheduleMapper.insert(schedule) > 0;
        } else {
            // 更新时重算 nextRunTime
            if (schedule.getEnabled() != null && schedule.getEnabled() == 1
                    && schedule.getCronExpression() != null && !schedule.getCronExpression().isEmpty()) {
                schedule.setNextRunTime(computeNextRunTime(schedule.getCronExpression()));
            } else {
                schedule.setNextRunTime(null);
            }
            return scheduleMapper.updateById(schedule) > 0;
        }
    }

    /**
     * 将 genreFilter 统一转为 JSON 数组字符串或 null。
     * 输入: null / "" / "爱情,科幻" / "[\"爱情\",\"科幻\"]"
     * 输出: null / "[\"爱情\",\"科幻\"]"
     */
    private String normalizeGenreFilter(String genreFilter) {
        if (genreFilter == null || genreFilter.trim().isEmpty()) {
            return null;
        }
        genreFilter = genreFilter.trim();
        // 已经是 JSON 数组格式
        if (genreFilter.startsWith("[") && genreFilter.endsWith("]")) {
            // 验证是否合法 JSON
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.readTree(genreFilter);
                // 空数组也返回 null
                if ("[]".equals(genreFilter)) return null;
                return genreFilter;
            } catch (Exception e) {
                // 非法 JSON，当作逗号分隔处理
            }
        }
        // 逗号分隔格式 -> JSON 数组
        String[] parts = genreFilter.split("[，,]");
        java.util.List<String> genres = new java.util.ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                genres.add(trimmed);
            }
        }
        if (genres.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(genres);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean deleteSchedule(Long id) {
        runningTasks.remove(id);
        return scheduleMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional
    public boolean startCrawler(Long id) {
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) return false;

        // 标记为运行中
        schedule.setStatus(CrawlerStatus.RUNNING.getCode());
        schedule.setLastRunTime(LocalDateTime.now());
        scheduleMapper.updateById(schedule);

        // 记录任务日志
        CrawlerTaskLog log = new CrawlerTaskLog();
        log.setScheduleId(id);
        log.setScheduleName(schedule.getName());
        log.setContentType(schedule.getContentType());
        log.setStatus(CrawlerStatus.RUNNING.getCode());
        log.setStartedAt(LocalDateTime.now());
        taskLogMapper.insert(log);

        // 将停止标志注入爬虫核心
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        runningTasks.put(id, stopFlag);

        // 传递 stopFlag 给爬虫核心
        crawlerCore.executeCrawl(id, log.getId(), stopFlag);

        return true;
    }

    @Override
    public boolean stopCrawler(Long id) {
        AtomicBoolean running = runningTasks.get(id);
        if (running != null) {
            running.set(false);
        }
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        if (schedule != null) {
            schedule.setStatus(CrawlerStatus.IDLE.getCode());
            scheduleMapper.updateById(schedule);
        }
        return true;
    }

    @Override
    public boolean toggleEnabled(Long id, boolean enabled) {
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) return false;
        schedule.setEnabled(enabled ? 1 : 0);
        if (enabled && schedule.getCronExpression() != null && !schedule.getCronExpression().isEmpty()) {
            schedule.setNextRunTime(computeNextRunTime(schedule.getCronExpression()));
        } else {
            schedule.setNextRunTime(null);
        }
        return scheduleMapper.updateById(schedule) > 0;
    }

    /** 计算下一次运行时间（供 UI 展示） */
    private LocalDateTime computeNextRunTime(String cronExpr) {
        try {
            String normalized = cronExpr.trim();
            String[] parts = normalized.split("\\s+");
            if (parts.length == 5) normalized = "0 " + normalized;
            CronExpression cron = CronExpression.parse(normalized);
            return cron.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[Scheduler] 无法解析 cron 表达式: {}", cronExpr);
            return null;
        }
    }
}