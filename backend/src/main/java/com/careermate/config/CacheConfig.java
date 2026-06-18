package com.careermate.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.lang.Nullable;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final int MAX_CACHE_VALUE_BYTES = 10 * 1024 * 1024;

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisSerializer<Object> serializer = new MaxSizeRedisSerializer(
                new GenericJackson2JsonRedisSerializer(),
                MAX_CACHE_VALUE_BYTES
        );
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .disableKeyPrefix()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("opportunity:list", defaults.entryTtl(Duration.ofMinutes(30)));
        configs.put("market:salary", defaults.entryTtl(Duration.ofHours(6)));
        configs.put("market:skill-trends", defaults.entryTtl(Duration.ofHours(6)));
        configs.put("market:resume-gap", defaults.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(configs)
                .build();
    }

    private record MaxSizeRedisSerializer(
            RedisSerializer<Object> delegate,
            int maxBytes
    ) implements RedisSerializer<Object> {

        @Override
        public byte[] serialize(@Nullable Object value) {
            byte[] bytes = delegate.serialize(value);
            if (bytes != null && bytes.length > maxBytes) {
                throw new IllegalArgumentException("Cache value exceeds " + maxBytes + " bytes");
            }
            return bytes;
        }

        @Override
        @Nullable
        public Object deserialize(@Nullable byte[] bytes) {
            return delegate.deserialize(bytes);
        }
    }
}
