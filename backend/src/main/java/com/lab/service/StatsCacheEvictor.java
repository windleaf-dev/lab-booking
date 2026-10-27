package com.lab.service;

import com.lab.common.CacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 预约状态变化后主动清理统计缓存，避免看板数字滞后。
 * 只有真正发生变更时才清理，避免「每次列表查询都清缓存」。
 */
@Component
public class StatsCacheEvictor {

    private final CacheManager cacheManager;

    public StatsCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictStats() {
        for (String name : CacheNames.STATS_ALL) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
