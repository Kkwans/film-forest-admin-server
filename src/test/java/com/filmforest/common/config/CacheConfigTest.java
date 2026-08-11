package com.filmforest.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.SimpleCacheManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void cachesHaveBoundedExpiryInsteadOfPermanentConcurrentMaps() {
        var manager = new CacheConfig().cacheManager();
        ((SimpleCacheManager) manager).initializeCaches();

        assertCachePolicy(manager.getCache(CacheConfig.CACHE_STATS), CacheConfig.STATS_TTL, 256);
        assertCachePolicy(manager.getCache(CacheConfig.CACHE_GENRES), CacheConfig.CATALOG_TTL, 128);
        assertCachePolicy(manager.getCache(CacheConfig.CACHE_TAGS), CacheConfig.CATALOG_TTL, 256);
    }

    private static void assertCachePolicy(org.springframework.cache.Cache springCache,
                                          Duration expectedTtl,
                                          long expectedMaximumSize) {
        assertThat(springCache).isNotNull();
        @SuppressWarnings("unchecked")
        Cache<Object, Object> nativeCache = (Cache<Object, Object>) springCache.getNativeCache();
        assertThat(nativeCache.policy().expireAfterWrite()).isPresent();
        assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(expectedTtl);
        assertThat(nativeCache.policy().eviction()).isPresent();
        assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum())
                .isEqualTo(expectedMaximumSize);
    }
}
