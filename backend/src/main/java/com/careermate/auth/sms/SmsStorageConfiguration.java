package com.careermate.auth.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SmsStorageConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public SmsCodeStore redisSmsCodeStore(StringRedisTemplate redisTemplate) {
        return new RedisSmsCodeStore(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(SmsCodeStore.class)
    public SmsCodeStore inMemorySmsCodeStore() {
        return new InMemorySmsCodeStore();
    }
}
