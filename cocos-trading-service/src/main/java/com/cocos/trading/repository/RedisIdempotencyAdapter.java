package com.cocos.trading.repository;

import com.cocos.trading.application.port.out.IdempotencyRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed adapter implementing {@link IdempotencyRepositoryPort} using atomic {@code SET ... NX EX} operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisIdempotencyAdapter implements IdempotencyRepositoryPort {

    public static final String KEY_PREFIX = "idempotency:orders:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean tryAcquire(UUID idempotencyKey, Duration ttl) {
        String redisKey = buildRedisKey(idempotencyKey);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, Instant.now().toString(), ttl);
        boolean success = Boolean.TRUE.equals(acquired);
        log.debug("[idempotency] Redis SET NX EX for key={} acquired={}, ttl={}", redisKey, success, ttl);
        return success;
    }

    @Override
    public void release(UUID idempotencyKey) {
        String redisKey = buildRedisKey(idempotencyKey);
        stringRedisTemplate.delete(redisKey);
        log.debug("[idempotency] Released Redis key={}", redisKey);
    }

    public static String buildRedisKey(UUID idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }
}

