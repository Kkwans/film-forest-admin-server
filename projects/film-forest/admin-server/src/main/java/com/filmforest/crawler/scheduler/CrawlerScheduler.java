package com.filmforest.crawler.scheduler;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
            long intervalSeconds = parseCronInterval(cron);
            if (intervalSeconds <= 0) return false;

            // 如果距离上次运行超过 interval 则触发
            long elapsedSeconds = java.time.Duration.between(lastRun, now).getSeconds();
            return elapsedSeconds >= intervalSeconds;
        } catch (Exception e) {
            log.warn("无法解析cron表达式: {} for schedule {}", cron, schedule.getId());
            return false;
        }
    }

    /** 解析 cron 表达式为间隔秒数（仅支持标准格式） */
    private long parseCronInterval(String cron) {
        // 格式: 秒 分 时 日 月 周
        // 例如: "0 */5 * * * *" = 每5分钟
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 6) return 0;

        // 检查各段并找最大周期
        // 秒: parts[0]
        // 分: parts[1]
        // 时: parts[2]
        // 日: parts[3]
        // 月: parts[4]
        // 周: parts[5]

        // 简化处理：找第一个非固定值（* 或固定数字以外的）作为周期
        // 只支持 */n 格式的简化 cron
        long seconds = 1;
        long minutes = 1;
        long hours = 1;

        // 分 (parts[1]): */5 -> 5分钟
        if (parts[1].startsWith("*/")) {
            minutes = Long.parseLong(parts[1].substring(2));
        } else if (!parts[1].equals("*")) {
            // 固定分钟，需要转换为小时周期
            minutes = 0;
        }

        // 时 (parts[2])
        if (parts[2].startsWith("*/")) {
            hours = Long.parseLong(parts[2].substring(2));
        }

        // 计算总秒数
        // 如果分钟是 */n，最小周期是 n * 60 秒
        // 如果小时是 */n，最小周期是 n * 3600 秒
        if (minutes > 0) {
            return minutes * 60;
        } else if (hours > 0) {
            return hours * 3600;
        }

        return 0;
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