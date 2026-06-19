package com.careermate.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
public class SmsStorageConfiguration {

    @Bean
    public SmsCodeStore smsCodeStore(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${careermate.auth.sms.storage:redis}") String storage) {
        if ("memory".equalsIgnoreCase(storage)) {
            log.warn("Using InMemorySmsCodeStore for mobile auth state");
            return new InMemorySmsCodeStore();
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            log.info("Using RedisSmsCodeStore for mobile auth state");
            return new RedisSmsCodeStore(redisTemplate);
        }
        log.warn("StringRedisTemplate is not available; falling back to in-memory mobile auth state");
        return new InMemorySmsCodeStore();
    }
}
