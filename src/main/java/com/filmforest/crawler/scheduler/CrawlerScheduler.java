package com.filmforest.crawler.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.mapper.CrawlerScheduleMapper;
import com.filmforest.crawler.service.CrawlerScheduleService;
import com.filmforest.crawler.service.CrawlerTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 只负责发现到期 Schedule；唯一 Job 创建和异步派发由生命周期服务负责。
 */
@Slf4j
@Component
public class CrawlerScheduler {

    private final CrawlerScheduleMapper scheduleMapper;
    private final CrawlerScheduleService scheduleService;

    public CrawlerScheduler(CrawlerScheduleMapper scheduleMapper,
                            CrawlerScheduleService scheduleService) {
        this.scheduleMapper = scheduleMapper;
        this.scheduleService = scheduleService;
    }

    @Scheduled(fixedRateString = "${app.crawler.schedule-check-interval-ms:60000}")
    public void checkAndTriggerSchedules() {
        LocalDateTime now = CrawlerTime.nowUtc();
        List<CrawlerSchedule> dueSchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<CrawlerSchedule>()
                        .eq(CrawlerSchedule::getEnabled, 1)
                        .isNotNull(CrawlerSchedule::getNextRunTime)
                        .le(CrawlerSchedule::getNextRunTime, now)
                        .orderByAsc(CrawlerSchedule::getNextRunTime));

        for (CrawlerSchedule schedule : dueSchedules) {
            if (!Integer.valueOf(1).equals(schedule.getEnabled())
                    || schedule.getNextRunTime() == null
                    || schedule.getNextRunTime().isAfter(now)) {
                continue;
            }
            boolean accepted = scheduleService.startScheduledCrawler(schedule.getId());
            if (accepted) {
                log.info("定时调度已创建 Job: scheduleId={}, name={}, nextRun={}",
                        schedule.getId(), schedule.getName(), schedule.getNextRunTime());
            } else {
                log.debug("定时调度未创建重复 Job: scheduleId={}", schedule.getId());
            }
        }
    }
}
