package com.filmforest.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * 缓存配置
 * 统计会频繁随内容、资源和爬虫变化，因此采用短 TTL；题材和标签变化较少，
 * 使用较长 TTL。所有缓存都设置容量上限，避免无界增长。
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_STATS = "stats";
    public static final String CACHE_GENRES = "genres";
    public static final String CACHE_TAGS = "tags";
    static final Duration STATS_TTL = Duration.ofSeconds(30);
    static final Duration CATALOG_TTL = Duration.ofMinutes(5);

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache(CACHE_STATS, STATS_TTL, 256),
                cache(CACHE_GENRES, CATALOG_TTL, 128),
                cache(CACHE_TAGS, CATALOG_TTL, 256)
        ));
        return manager;
    }

    private CaffeineCache cache(String name, Duration ttl, long maximumSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize)
                .build());
    }
}
