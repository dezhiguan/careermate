package com.careermate.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.lang.Nullable;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final int MAX_CACHE_VALUE_BYTES = 10 * 1024 * 1024;

    /**
     * 用 {@link RedisCacheManagerBuilderCustomizer} 而不是自己定义 CacheManager Bean。
     *
     * <p>原实现是 {@code @Bean @ConditionalOnBean(RedisConnectionFactory.class) CacheManager}。
     * {@code @ConditionalOnBean} 只在 auto-configuration 类里可靠——普通 {@code @Configuration}
     * 的 Bean 定义顺序没有保证，本类求值时 RedisConnectionFactory 往往尚未注册，条件判假，
     * 这个 Bean 被静默跳过，最终生效的是 Spring Boot 自动装配的默认 RedisCacheManager
     * （值用 JDK 序列化）。而各 VO 均未实现 Serializable，于是每一次缓存写入都抛
     * {@code Cannot serialize} 被吞进日志——线上六个缓存域实际全部失效，
     * 每个请求都在重算（interview:kb-questions 实测两次调用均 11~13s）。
     *
     * <p>Customizer 由 Redis 缓存自动装配负责应用，不存在顺序问题；没有 Redis 时
     * 自动装配本身不生效，Customizer 自然不会被调用，本地/测试环境照常退化为简单缓存。
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
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
        configs.put("interview:kb-questions", defaults.entryTtl(Duration.ofHours(12)));
        // 大厂面试风格短期内不变，且这条链路要走 RAG + LLM（实测 7~10s），缓存 24h
        configs.put("interview:company-prep", defaults.entryTtl(Duration.ofHours(24)));
        // 按 JD 出题要走两次 RAG + 生成 5 道详题的 LLM（实测 32s）。JD 与用户简历短期内不变，缓存 12h
        configs.put("interview:jd-aware-questions", defaults.entryTtl(Duration.ofHours(12)));

        return builder -> builder
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(configs);
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
