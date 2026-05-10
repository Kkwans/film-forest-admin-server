package com.filmforest.crawler.scheduler;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爬虫定时调度器
 * 每分钟检查所有启用的调度配置，触发到期的爬虫任务
 */
@Slf4j
@Component
public class CrawlerScheduler {

    @Autowired
    private CrawlerScheduleMapper scheduleMapper;

    @Autowired
    private CrawlerScheduleService scheduleService;

    @Autowired
    private com.filmforest.crawler.core.CrawlerCore crawlerCore;

    /** 正在执行的任务（防止重复触发） */
    private final ConcurrentHashMap<Long, Boolean> runningJobs = new ConcurrentHashMap<>();

    /** 每分钟执行一次：检查所有启用的调度，触发到期的任务 */
    @Scheduled(fixedRate = 60000) // 每60秒
    public void checkAndTriggerSchedules() {
        List<CrawlerSchedule> schedules = scheduleMapper.selectList(null);
        for (CrawlerSchedule schedule : schedules) {
            if (schedule.getEnabled() != 1) continue;
            if (!"idle".equals(schedule.getStatus())) continue;
            if (runningJobs.get(schedule.getId()) != null) continue;

            // 计算是否应该执行
            if (shouldRunNow(schedule)) {
                triggerCrawl(schedule);
            }
        }
    }

    /** 根据 cron 表达式判断是否应该现在执行 */
    private boolean shouldRunNow(CrawlerSchedule schedule) {
        String cron = schedule.getCronExpression();
        if (cron == null || cron.isEmpty()) return false;

        LocalDateTime lastRun = schedule.getLastRunTime();
        LocalDateTime now = LocalDateTime.now();

        // 如果从未运行，直接执行
        if (lastRun == null) return true;

        try {
            // 支持 5 段标准 cron（如 "0 2 * * *"）和 Spring 6 段格式（如 "0 0 2 * * *"）
            String normalizedCron = normalizeCron(cron);
            CronExpression expression = CronExpression.parse(normalizedCron);

            // 计算上次运行后下一次应该触发的时间
            LocalDateTime nextRun = expression.next(lastRun);
            if (nextRun == null) return false;

            // 如果 now >= nextRun 则触发
            return !now.isBefore(nextRun);
        } catch (Exception e) {
            log.warn("无法解析cron表达式: {} for schedule {} — {}", cron, schedule.getId(), e.getMessage());
            return false;
        }
    }

    /** 将 5 段 cron 标准化为 Spring 6 段格式（前面补秒字段） */
    private String normalizeCron(String cron) {
        String[] parts = cron.trim().split("\\s+");
        if (parts.length == 5) {
            // 标准 5 段: 分 时 日 月 周 → 补秒字段 "0"
            return "0 " + cron.trim();
        }
        return cron.trim(); // 已经是 6 段
    }

    /** 触发爬虫任务（异步执行） */
    private void triggerCrawl(CrawlerSchedule schedule) {
        runningJobs.put(schedule.getId(), true);
        log.info("[SCHEDULER] 触发爬虫: id={} name={} cron={}", schedule.getId(), schedule.getName(), schedule.getCronExpression());

        // 异步执行，不阻塞主线程
        new Thread(() -> {
            try {
                scheduleService.startCrawler(schedule.getId());
            } catch (Exception e) {
                log.error("[SCHEDULER] 爬虫执行异常: id={} error={}", schedule.getId(), e.getMessage());
            } finally {
                runningJobs.remove(schedule.getId());
            }
        }).start();
    }
}