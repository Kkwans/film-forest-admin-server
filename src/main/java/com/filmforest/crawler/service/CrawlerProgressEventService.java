package com.filmforest.crawler.service;

import com.filmforest.crawler.entity.CrawlerTaskLog;
import com.filmforest.crawler.mapper.CrawlerTaskLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 连接 SSE 与权威 Job 快照，所有事件发送前重新读取已落库状态。 */
@Service
public class CrawlerProgressEventService {

    private final CrawlerTaskLogMapper taskLogMapper;
    private final CrawlerProgressEventHub eventHub;

    public CrawlerProgressEventService(CrawlerTaskLogMapper taskLogMapper,
                                       CrawlerProgressEventHub eventHub) {
        this.taskLogMapper = taskLogMapper;
        this.eventHub = eventHub;
    }

    public SseEmitter subscribeJob(Long jobId) {
        SseEmitter emitter = eventHub.registerJob(jobId);
        eventHub.sendSnapshot(emitter, CrawlerProgressEvent.job("snapshot", taskLogMapper.selectById(jobId)));
        return emitter;
    }

    public SseEmitter subscribeAllJobs() {
        SseEmitter emitter = eventHub.registerAllJobs();
        List<CrawlerTaskLog> activeJobs = taskLogMapper.selectActiveJobs();
        eventHub.sendSnapshot(emitter, CrawlerProgressEvent.snapshot(activeJobs));
        return emitter;
    }

    public void publish(Long jobId, String type) {
        if (jobId == null) return;
        try {
            CrawlerTaskLog job = taskLogMapper.selectById(jobId);
            if (job != null) eventHub.publish(CrawlerProgressEvent.job(type, job));
        } catch (RuntimeException failure) {
            // 实时推送不能反向影响爬虫；数据库进度已经由调用方写入。
        }
    }
}
