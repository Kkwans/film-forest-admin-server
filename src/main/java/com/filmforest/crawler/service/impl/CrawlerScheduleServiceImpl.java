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
import com.filmforest.crawler.service.CrawlerScheduleDefinitionService;
import com.filmforest.crawler.service.CrawlerScheduleGenreService;
import com.filmforest.crawler.service.CrawlerSourceCatalogService;
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
    private final CrawlerScheduleDefinitionService scheduleDefinitionService;
    private final CrawlerScheduleGenreService scheduleGenreService;
    private final CrawlerSourceCatalogService sourceCatalogService;

    public CrawlerScheduleServiceImpl(CrawlerScheduleMapper scheduleMapper,
                                      CrawlerTaskLogMapper taskLogMapper,
                                      CrawlerJobLifecycleService jobLifecycleService,
                                      CrawlerScheduleDefinitionService scheduleDefinitionService,
                                      CrawlerScheduleGenreService scheduleGenreService,
                                      CrawlerSourceCatalogService sourceCatalogService) {
        this.scheduleMapper = scheduleMapper;
        this.taskLogMapper = taskLogMapper;
        this.jobLifecycleService = jobLifecycleService;
        this.scheduleDefinitionService = scheduleDefinitionService;
        this.scheduleGenreService = scheduleGenreService;
        this.sourceCatalogService = sourceCatalogService;
    }

    @Override
    public List<CrawlerSchedule> listSchedules() {
        List<CrawlerSchedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<CrawlerSchedule>()
                .orderByDesc(CrawlerSchedule::getCreatedAt));
        schedules.forEach(schedule -> {
            decorateRuntimeStatus(schedule);
            schedule.setGenreTagIds(scheduleGenreService.listTagIds(schedule.getId()));
        });
        return schedules;
    }

    @Override
    public CrawlerSchedule getSchedule(Long id) {
        CrawlerSchedule schedule = scheduleMapper.selectById(id);
        decorateRuntimeStatus(schedule);
        if (schedule != null) schedule.setGenreTagIds(scheduleGenreService.listTagIds(schedule.getId()));
        return schedule;
    }

    @Override
    @Transactional
    public boolean saveSchedule(CrawlerSchedule schedule) {
        if (schedule.getGenreTagIds() == null && schedule.getGenreFilter() != null
                && !schedule.getGenreFilter().isBlank()) {
            throw new IllegalArgumentException("不再接受自由文本题材，请提交 genreTagIds");
        }
        sourceCatalogService.validateAndNormalize(schedule);
        CrawlerScheduleGenreService.Selection genreSelection = scheduleGenreService.validate(
                schedule.getContentType(), schedule.getGenreTagIds());
        schedule.setGenreFilter(genreSelection.compatibilityJson());
        CrawlerScheduleDefinitionService.Definition definition = scheduleDefinitionService.normalize(
                schedule.getScheduleMode(), schedule.getScheduleConfig(), schedule.getCronExpression());
        schedule.setScheduleMode(definition.mode().name());
        schedule.setScheduleConfig(definition.config());
        schedule.setCronExpression(definition.cronExpression());
        schedule.setTimezone(scheduleDefinitionService.normalizeTimezone(schedule.getTimezone()));
        CrawlerCrawlMode crawlMode = CrawlerCrawlMode.fromCode(schedule.getCrawlMode());
        schedule.setCrawlMode(crawlMode.getCode());
        if (crawlMode == CrawlerCrawlMode.FULL || definition.cronExpression() == null) {
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
                schedule.setNextRunTime(computeNextRunTime(schedule));
            }
            boolean inserted = scheduleMapper.insert(schedule) > 0;
            if (inserted) scheduleGenreService.replace(schedule.getId(), genreSelection.tagIds());
            return inserted;
        } else {
            // 更新时重算 nextRunTime
            if (crawlMode == CrawlerCrawlMode.LATEST
                    && schedule.getEnabled() != null && schedule.getEnabled() == 1
                    && schedule.getCronExpression() != null && !schedule.getCronExpression().isEmpty()) {
                schedule.setNextRunTime(computeNextRunTime(schedule));
            } else {
                schedule.setNextRunTime(null);
            }
            boolean updated = scheduleMapper.updateById(schedule) > 0;
            if (updated) scheduleGenreService.replace(schedule.getId(), genreSelection.tagIds());
            return updated;
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
            schedule.setNextRunTime(computeNextRunTime(schedule));
        } else {
            schedule.setNextRunTime(null);
        }
        return scheduleMapper.updateById(schedule) > 0;
    }

    /** 计算下一次运行时间（供 UI 展示） */
    private LocalDateTime computeNextRunTime(CrawlerSchedule schedule) {
        try {
            String cronExpr = schedule.getCronExpression();
            String normalized = cronExpr.trim();
            String[] parts = normalized.split("\\s+");
            if (parts.length == 5) normalized = "0 " + normalized;
            return CrawlerTime.nextRunUtc(normalized, CrawlerTime.nowUtc(), schedule.getTimezone());
        } catch (Exception e) {
            log.warn("[Scheduler] 无法解析 cron 表达式: {}", schedule.getCronExpression());
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
