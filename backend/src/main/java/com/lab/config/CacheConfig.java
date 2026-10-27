package com.lab.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lab.common.CacheNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存配置：
 * - 默认（REDIS_ENABLED=false）：Spring Boot 使用 Caffeine 本地缓存，单机开发无需 Redis
 * - 开启 Redis：使用 RedisCacheManager，缓存跨实例共享
 * - Redis 故障时由 CacheErrorHandler 兜底，降级为直接查库，不影响业务
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 必须开启 defaultTyping：否则反序列化只能得到 LinkedHashMap，
        // 代理返回时会因为无法转换成 PageResult / List 而抛 ClassCastException。
        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(mapper)
                .defaultTyping(true)
                .registerNullValueSerializer(true)
                .build();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues()
                // 统一前缀，方便在 redis-cli / 监控里区分业务缓存
                .computePrefixWith(cacheName -> "lab:cache:" + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(CacheNames.DEVICE_PAGE, base.entryTtl(Duration.ofSeconds(60)));
        configs.put(CacheNames.CATEGORIES, base.entryTtl(Duration.ofMinutes(5)));
        configs.put(CacheNames.STATS_OVERVIEW, base.entryTtl(Duration.ofSeconds(30)));
        configs.put(CacheNames.STATS_STATUS, base.entryTtl(Duration.ofSeconds(60)));
        configs.put(CacheNames.STATS_TOP, base.entryTtl(Duration.ofSeconds(60)));
        configs.put(CacheNames.STATS_CATEGORY, base.entryTtl(Duration.ofMinutes(5)));
        configs.put(CacheNames.STATS_TREND, base.entryTtl(Duration.ofSeconds(60)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(configs)
                // 事务提交后再写入 / 清理缓存，避免回滚后缓存脏数据
                .transactionAware()
                .build();
    }

    /**
     * Redis 抖动时只记录日志，让方法继续走数据库，保证可用性优先。
     * 日志做 30 秒限流，避免故障期间刷屏。
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private final AtomicLong lastWarn = new AtomicLong();

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                warn("读取", cache, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                warn("写入", cache, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                warn("清理", cache, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                warn("清空", cache, exception);
            }

            private void warn(String action, Cache cache, RuntimeException e) {
                long now = System.currentTimeMillis();
                long last = lastWarn.get();
                if (now - last > 30_000 && lastWarn.compareAndSet(last, now)) {
                    log.warn("缓存{}失败，已降级为直接查库：cache={}, reason={}",
                            action, cache.getName(), e.getMessage());
                }
            }
        };
    }
}
