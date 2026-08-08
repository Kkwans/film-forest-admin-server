package com.filmforest.crawler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(CrawlerExecutionProperties.class)
public class CrawlerExecutionConfiguration {

    @Bean(name = "crawlerJobExecutor")
    public Executor crawlerJobExecutor(CrawlerExecutionProperties properties) {
        int workers = Math.max(1, properties.getWorkerConcurrency());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(Math.max(1, properties.getQueueCapacity()));
        executor.setThreadNamePrefix("crawler-job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
