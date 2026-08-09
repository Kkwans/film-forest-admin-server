package com.filmforest.notification.service;

import com.filmforest.crawler.entity.CrawlerStatus;
import com.filmforest.crawler.service.CrawlerJobTerminalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class CrawlerJobNotificationListener {

    private final AdminNotificationService notificationService;

    public CrawlerJobNotificationListener(AdminNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterJobFinished(CrawlerJobTerminalEvent event) {
        try {
            notificationService.publishToAdmins(toNotification(event));
        } catch (RuntimeException error) {
            // Job 已提交完成；通知故障只能记录，绝不能改变爬虫结果。
            log.error("爬虫终态通知创建失败: jobId={}, status={}",
                    event.jobId(), event.status(), error);
        }
    }

    AdminNotificationService.NotificationEvent toNotification(CrawlerJobTerminalEvent event) {
        String scheduleName = displayName(event.scheduleName());
        String eventType;
        String severity;
        String title;
        if (event.status() == CrawlerStatus.CANCELLED) {
            eventType = "CRAWLER_INTERRUPTED";
            severity = "WARNING";
            title = scheduleName + " 已中断";
        } else if (event.status() == CrawlerStatus.FAILED) {
            eventType = "CRAWLER_FAILED";
            severity = "ERROR";
            title = scheduleName + " 爬取失败";
        } else if (event.status() == CrawlerStatus.PARTIAL_SUCCESS) {
            eventType = "CRAWLER_FAILED";
            severity = "WARNING";
            title = scheduleName + " 部分条目失败";
        } else if (event.status() == CrawlerStatus.SUCCESS && event.retryOfJobId() != null) {
            eventType = "CRAWLER_RECOVERED";
            severity = "SUCCESS";
            title = scheduleName + " 已恢复";
        } else {
            eventType = "CRAWLER_SUCCESS";
            severity = "SUCCESS";
            title = scheduleName + " 爬取完成";
        }

        String message = "发现 %d 条，新增 %d 条，更新 %d 条，失败 %d 条。"
                .formatted(event.discovered(), event.added(), event.updated(), event.failed());
        if (event.status() == CrawlerStatus.FAILED || event.status() == CrawlerStatus.PARTIAL_SUCCESS) {
            message += "请进入任务详情查看失败原因并决定是否重试。";
        }
        return new AdminNotificationService.NotificationEvent(
                eventType,
                severity,
                title,
                message,
                "/crawler?jobId=" + event.jobId(),
                "CRAWLER_JOB",
                event.jobId(),
                "crawler-job:" + event.jobId() + ":" + event.status().getCode());
    }

    private static String displayName(String scheduleName) {
        return scheduleName == null || scheduleName.isBlank() ? "爬虫任务" : scheduleName.trim();
    }
}
