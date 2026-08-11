package com.careermate.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 短信发码/登录的状态存储。
 *
 * <p>空闲的 Redis 连接被对端关闭后，Lettuce 要到复用时才发现，首个命令抛
 * {@code SocketException: Connection reset}；Lettuce 随后自动重连，所以紧接着的请求就正常。
 * {@link com.careermate.config.RedisKeepAliveConfig} 开启 keepalive 降低发生率，
 * 这里再对传输级失败补一次重试，保证单次请求不会因为一条陈旧连接直接失败。
 */
@Slf4j
public class RedisSmsCodeStore implements SmsCodeStore {

    private final StringRedisTemplate redisTemplate;

    public RedisSmsCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void setValue(String key, String value, Duration ttl) {
        withReconnectRetry("setValue", () -> {
            redisTemplate.opsForValue().set(key, value, ttl);
            return null;
        });
    }

    @Override
    public Optional<String> getValue(String key) {
        return withReconnectRetry("getValue",
                () -> Optional.ofNullable(redisTemplate.opsForValue().get(key)));
    }

    @Override
    public boolean delete(String key) {
        return withReconnectRetry("delete",
                () -> Boolean.TRUE.equals(redisTemplate.delete(key)));
    }

    @Override
    public long increment(String key, Duration ttl) {
        return withReconnectRetry("increment", () -> {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return value == null ? 0L : value;
        });
    }

    @Override
    public long getCounter(String key) {
        String value = withReconnectRetry("getCounter", () -> redisTemplate.opsForValue().get(key));
        if (!org.springframework.util.StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override
    public Optional<Long> getRemainingTtlSeconds(String key) {
        Long ttl = withReconnectRetry("getRemainingTtlSeconds",
                () -> redisTemplate.getExpire(key, TimeUnit.SECONDS));
        if (ttl == null || ttl < 0) {
            return Optional.empty();
        }
        return Optional.of(ttl);
    }

    /**
     * 仅对传输级失败（连接被重置/关闭）重试一次；业务性错误按原样上抛，避免掩盖真实问题。
     */
    private <T> T withReconnectRetry(String op, Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException ex) {
            if (!isTransportFailure(ex)) {
                throw ex;
            }
            log.warn("Redis 连接已失效，重连后重试一次: op={}, cause={}", op, rootCause(ex).toString());
            return action.get();
        }
    }

    private boolean isTransportFailure(Throwable ex) {
        return rootCause(ex) instanceof IOException;
    }

    private Throwable rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
