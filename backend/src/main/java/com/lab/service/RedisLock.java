package com.lab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 的简易分布式锁。
 *
 * <p>加锁：SET key token NX PX ttl，保证原子性。
 * <p>解锁：Lua 比对 token 后删除，避免误删其他请求的锁。
 * <p>降级：Redis 未开启或连接异常时返回 NOOP 令牌，业务继续执行，
 * 最终仍由数据库唯一索引保证「同一设备同一时段只成交一单」。
 */
@Component
public class RedisLock {

    private static final Logger log = LoggerFactory.getLogger(RedisLock.class);

    /** 未启用 Redis / 连接失败时的占位令牌，解锁时直接忽略 */
    public static final String NOOP_TOKEN = "__noop__";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${app.redis.enabled:false}")
    private boolean enabled;

    @Value("${app.redis.lock-ttl-seconds:5}")
    private long ttlSeconds;

    public RedisLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return 加锁成功返回 token；锁被占用返回 null；Redis 不可用返回 NOOP_TOKEN
     */
    public String tryLock(String key) {
        if (!enabled) {
            return NOOP_TOKEN;
        }
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, Duration.ofSeconds(Math.max(1, ttlSeconds)));
            return Boolean.TRUE.equals(ok) ? token : null;
        } catch (Exception e) {
            log.warn("Redis 加锁失败，降级为数据库唯一索引兜底：key={}, reason={}", key, e.getMessage());
            return NOOP_TOKEN;
        }
    }

    /**
     * 在事务结束后释放锁：如果提前释放，另一个请求可能在当前事务提交前拿到锁，
     * 从而在数据库唯一索引上撞车。放在 afterCompletion 可以覆盖提交 / 回滚两种情况。
     */
    public void unlockAfterCompletion(String key, String token) {
        if (token == null || NOOP_TOKEN.equals(token)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    unlock(key, token);
                }
            });
        } else {
            unlock(key, token);
        }
    }

    private void unlock(String key, String token) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (Exception e) {
            log.warn("Redis 解锁失败（锁会按 TTL 自动过期）：key={}, reason={}", key, e.getMessage());
        }
    }
}
