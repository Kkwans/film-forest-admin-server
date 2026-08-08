package com.filmforest.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.entity.CrawlerCrawlMode;
import com.filmforest.crawler.entity.CrawlerStatus;
import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.entity.CrawlerTriggerType;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import com.filmforest.crawler.service.CrawlerJobLifecycleService;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CrawlerScheduleServiceImpl implements CrawlerScheduleService {

    private final CrawlerScheduleMapper scheduleMapper;
    private final CrawlerTaskLogMapper taskLogMapper;
    private final CrawlerJobLifecycleService jobLifecycleService;

    public CrawlerScheduleServiceImpl(CrawlerScheduleMapper scheduleMapper,
                                      CrawlerTaskLogMapper taskLogMapper,
                                      CrawlerJobLifecycleService jobLifecycleService) {
        this.scheduleMapper = scheduleMapper;
        this.taskLogMapper = taskLogMapper;
        this.jobLifecycleService = jobLifecycleService;
    }

    @Override
    public List<CrawlerSchedule> listSchedules() {
        List<CrawlerSchedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<CrawlerSchedule>()
                .orderByDesc(CrawlerSchedule::getCreatedAt));
        schedules.forEach(this::decorateRuntimeStatus);
        return schedules;
    }

    @Override
    public CrawlerSchedule getSchedule(Long id) {
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        decorateRuntimeStatus(schedule);
        return schedule;
    }

    @Override
    @Transactional
    public boolean saveSchedule(CrawlerSchedule schedule) {
        // 修复 #6: genreFilter 是 JSON 列，空字符串/null/非法值统一转为 null
        // 合法格式: 逗号分隔的中文标签 "爱情,科幻" 或 JSON 数组 "[\"爱情\",\"科幻\"]"
        schedule.setGenreFilter(normalizeGenreFilter(schedule.getGenreFilter()));
        CrawlerCrawlMode crawlMode = CrawlerCrawlMode.fromCode(schedule.getCrawlMode());
        schedule.setCrawlMode(crawlMode.getCode());
        if (crawlMode == CrawlerCrawlMode.FULL) {
            schedule.setEnabled(0);
            schedule.setNextRunTime(null);
        }

        if (schedule.getId() == null) {
            schedule.setStatus("idle");
            if (schedule.getEnabled() == null) {
                schedule.setEnabled(0);
            }
            schedule.setTotalRuns(0);
            schedule.setTotalItems(0);
            // 计算 nextRunTime
            if (crawlMode == CrawlerCrawlMode.LATEST
                    && schedule.getEnabled() != null && schedule.getEnabled() == 1
                    && schedule.getCronExpression() != null && !schedule.getCronExpression().isEmpty()) {
                schedule.setNextRunTime(computeNextRunTime(schedule.getCronExpression()));
            }
            return scheduleMapper.insert(schedule) > 0;
        } else {
            // 更新时重算 nextRunTime
            if (crawlMode == CrawlerCrawlMode.LATEST
                    && schedule.getEnabled() != null && schedule.getEnabled() == 1
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
        if (taskLogMapper.selectActiveByScheduleId(id) != null) {
            return false;
        }
        return scheduleMapper.deleteById(id) > 0;
    }

    @Override
    public boolean startCrawler(Long id) {
        return enqueue(id, CrawlerTriggerType.MANUAL, null);
    }

    @Override
    public boolean startScheduledCrawler(Long id) {
        return enqueue(id, CrawlerTriggerType.SCHEDULED, null);
    }

    @Override
    public boolean retryCrawler(Long jobId) {
        CrawlerTaskLog previous = taskLogMapper.selectById(jobId);
        if (previous == null) {
            return false;
        }
        CrawlerStatus status = CrawlerStatus.fromCode(previous.getStatus());
        if (status == null || !status.isRetryable()) {
            return false;
        }
        return enqueue(previous.getScheduleId(), CrawlerTriggerType.RETRY, jobId);
    }

    @Override
    public boolean stopCrawler(Long id) {
        return jobLifecycleService.requestCancelBySchedule(id);
    }

    @Override
    public boolean cancelJob(Long jobId) {
        return jobLifecycleService.requestCancelByJob(jobId);
    }

    @Override
    public boolean toggleEnabled(Long id, boolean enabled) {
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) return false;
        if (enabled && CrawlerCrawlMode.fromCode(schedule.getCrawlMode()) == CrawlerCrawlMode.FULL) {
            return false;
        }
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
            return CrawlerTime.nextRunUtc(normalized, CrawlerTime.nowUtc());
        } catch (Exception e) {
            log.warn("[Scheduler] 无法解析 cron 表达式: {}", cronExpr);
            return null;
        }
    }

    private boolean enqueue(Long scheduleId, CrawlerTriggerType triggerType, Long retryOfJobId) {
        try {
            return jobLifecycleService.enqueue(scheduleId, triggerType, retryOfJobId) != null;
        } catch (DuplicateKeyException conflict) {
            log.info("同一 schedule 已有活动 Job，拒绝重复启动: scheduleId={}, trigger={}",
                    scheduleId, triggerType.getCode());
            return false;
        }
    }

    private void decorateRuntimeStatus(CrawlerSchedule schedule) {
        if (schedule == null || schedule.getId() == null) {
            return;
        }
        CrawlerTaskLog active = taskLogMapper.selectActiveByScheduleId(schedule.getId());
        schedule.setStatus(active == null ? "idle" : "running");
        CrawlerTaskLog latest = active == null
                ? taskLogMapper.selectLatestByScheduleId(schedule.getId())
                : active;
        schedule.setLatestJobId(latest == null ? null : latest.getId());
        schedule.setLatestResult(latest == null ? null : latest.getStatus());
    }
}
