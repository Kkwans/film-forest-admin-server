package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerSchedule;
import com.filmforest.crawler.dto.CrawlerJobStartResult;
import java.util.List;

public interface CrawlerScheduleService {

    /**
     * 获取所有定时配置列表
     */
    List<CrawlerSchedule> listSchedules();

    /**
     * 获取单个配置
     */
    CrawlerSchedule getSchedule(Long id);

    /**
     * 新增/更新配置
     */
    boolean saveSchedule(CrawlerSchedule schedule);

    /**
     * 删除配置
     */
    boolean deleteSchedule(Long id);

    /**
     * 启动爬虫（手动触发一次）
     */
    CrawlerJobStartResult startCrawler(Long id);

    /** 由定时调度触发，和手工/重试共用唯一活动 Job 约束。 */
    boolean startScheduledCrawler(Long id);

    /** 基于终态 Job 的安全检查点创建重试 Job。 */
    CrawlerJobStartResult retryCrawler(Long jobId);

    /**
     * 停止爬虫
     */
    boolean stopCrawler(Long id);

    /** 按 Job ID 请求取消。 */
    boolean cancelJob(Long jobId);

    /**
     * 切换启用/禁用状态
     */
    boolean toggleEnabled(Long id, boolean enabled);
}
