package com.gate.pyq.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String QUESTIONS_CATALOG_CACHE = "questionsCatalog";
    public static final String USER_ATTEMPTED_IDS_CACHE = "userAttemptedIds";
    public static final String TOPIC_PERFORMANCE_CACHE = "topicPerformance";
    public static final String DB_STATUS_CACHE = "dbStatus";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                // Static question catalog: 1 hour TTL (also explicitly evicted on bulk upload)
                new TtlConcurrentMapCache(QUESTIONS_CATALOG_CACHE, 60 * 60 * 1000L),
                // Per-user attempted question IDs: 15 minutes TTL (evicted immediately on recordAttempt)
                new TtlConcurrentMapCache(USER_ATTEMPTED_IDS_CACHE, 15 * 60 * 1000L),
                // Per-user topic performance analytics: 15 minutes TTL (evicted immediately on recordAttempt)
                new TtlConcurrentMapCache(TOPIC_PERFORMANCE_CACHE, 15 * 60 * 1000L),
                // System DB status summary: 30 seconds TTL (evicted on writes)
                new TtlConcurrentMapCache(DB_STATUS_CACHE, 30 * 1000L)
        ));
        return manager;
    }

    /**
     * Thread-safe in-memory Cache implementation with automatic Time-To-Live (TTL) expiration.
     */
    public static class TtlConcurrentMapCache extends ConcurrentMapCache {
        private final long ttlMillis;
        private final ConcurrentMap<Object, Long> timestamps = new ConcurrentHashMap<>();

        public TtlConcurrentMapCache(String name, long ttlMillis) {
            super(name, new ConcurrentHashMap<>(), false);
            this.ttlMillis = ttlMillis;
        }

        private boolean isExpired(Object key) {
            Long created = timestamps.get(key);
            if (created == null) {
                return false;
            }
            if (System.currentTimeMillis() - created > ttlMillis) {
                super.evict(key);
                timestamps.remove(key);
                return true;
            }
            return false;
        }

        @Override
        public ValueWrapper get(Object key) {
            if (isExpired(key)) {
                return null;
            }
            return super.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            if (isExpired(key)) {
                return null;
            }
            return super.get(key, type);
        }

        @Override
        public void put(Object key, Object value) {
            timestamps.put(key, System.currentTimeMillis());
            super.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            isExpired(key);
            timestamps.putIfAbsent(key, System.currentTimeMillis());
            return super.putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            timestamps.remove(key);
            super.evict(key);
        }

        @Override
        public void clear() {
            timestamps.clear();
            super.clear();
        }
    }
}
