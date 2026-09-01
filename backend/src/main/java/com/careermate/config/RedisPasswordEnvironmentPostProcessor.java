package com.careermate.config;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Redis 密码在部署环境里存在两个 key：Spring 绑定的 {@code SPRING_DATA_REDIS_PASSWORD}，
 * 以及共享 env 里历史沿用的 {@code REDIS_PASSWORD}。任一被写成空串，Spring 就会安静地以
 * 「无密码」建连；而 Redis 开了 requirepass 之后不会踢掉已建立的老连接，故障要等到某次重启
 * 才引爆——2026-09-01 auth-gateway 的登录全站不可用即由此而来。
 *
 * <p>careermate 侧的引信还多一格：集群 secret 里两个 key 都有值，但
 * {@code /opt/shared/env/careermate.env} 里**一个都没有**，而
 * {@code create-careermate-k8s-secret.sh} 纯粹按 env 文件重建 secret——脚本跑一次就把有效密码
 * 抹掉，下次重启短信发码与短信登录一起 500（{@code SmsAuthRateLimiter} 直接透传 Redis 异常）。</p>
 *
 * <p>这里做两件事：密码为空时回落到 {@code REDIS_PASSWORD}；prod 下两者都为空则拒绝启动，
 * 把这类配置漂移挡在部署阶段，而不是等它以线上 500 的形式暴露。</p>
 */
public class RedisPasswordEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PASSWORD_PROPERTY = "spring.data.redis.password";
    static final String HOST_PROPERTY = "spring.data.redis.host";
    static final String FALLBACK_PROPERTY = "REDIS_PASSWORD";
    static final String SOURCE_NAME = "redisPasswordFallback";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!StringUtils.hasText(environment.getProperty(HOST_PROPERTY))) {
            return;
        }
        if (StringUtils.hasText(environment.getProperty(PASSWORD_PROPERTY))) {
            return;
        }
        String fallback = environment.getProperty(FALLBACK_PROPERTY);
        if (StringUtils.hasText(fallback)) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource(SOURCE_NAME, Map.of(PASSWORD_PROPERTY, fallback)));
            return;
        }
        // 注意：application.yml 给了 REDIS_HOST 默认值 localhost，所以"host 为空"这条逃生口在
        // careermate 永远不成立。真正的判据是**这个上下文到底会不会装配 Redis 客户端**。
        if (!environment.matchesProfiles("prod") || redisClientDisabled(environment)) {
            return;
        }
        throw new IllegalStateException(
                "Redis 密码缺失：" + PASSWORD_PROPERTY + " 与 " + FALLBACK_PROPERTY
                        + " 均为空，拒绝以无密码方式连接生产 Redis。请修正 /opt/shared/env/careermate.env "
                        + "后重建 careermate-backend-env secret 并重新滚动。");
    }

    /** 显式排除了 Redis 自动装配（部分 prod profile 测试如此）时不接 Redis，自然不需要密码。 */
    private boolean redisClientDisabled(ConfigurableEnvironment environment) {
        String[] excluded = environment.getProperty("spring.autoconfigure.exclude", String[].class);
        if (excluded == null) {
            return false;
        }
        for (String candidate : excluded) {
            if (candidate != null && candidate.contains("RedisAutoConfiguration")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // 需在 ConfigData 处理完（profile、application-*.yml 已就绪）之后再判断
        return Ordered.LOWEST_PRECEDENCE;
    }
}
