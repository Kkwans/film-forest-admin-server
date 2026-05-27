package com.filmforest.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置
 * 使用 ConcurrentMapCacheManager（简单、无外部依赖）
 * 缓存项在 TTL 后由定时任务清理
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_STATS = "stats";
    public static final String CACHE_GENRES = "genres";
    public static final String CACHE_TAGS = "tags";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                CACHE_STATS,
                CACHE_GENRES,
                CACHE_TAGS
        );
    }
}
