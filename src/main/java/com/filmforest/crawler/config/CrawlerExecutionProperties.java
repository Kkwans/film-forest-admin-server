package com.filmforest.crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.crawler.execution")
public class CrawlerExecutionProperties {

    private int workerConcurrency = 1;
    private int detailConcurrency = 1;
    private int queueCapacity = 4;
    private long heartbeatIntervalMs = 15_000;
    private long staleHeartbeatMs = 120_000;

    public int getWorkerConcurrency() {
        return workerConcurrency;
    }

    public void setWorkerConcurrency(int workerConcurrency) {
        this.workerConcurrency = workerConcurrency;
    }

    public int getDetailConcurrency() {
        return detailConcurrency;
    }

    public void setDetailConcurrency(int detailConcurrency) {
        this.detailConcurrency = detailConcurrency;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getStaleHeartbeatMs() {
        return staleHeartbeatMs;
    }

    public void setStaleHeartbeatMs(long staleHeartbeatMs) {
        this.staleHeartbeatMs = staleHeartbeatMs;
    }
}
