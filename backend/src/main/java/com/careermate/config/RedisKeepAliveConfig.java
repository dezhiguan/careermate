package com.careermate.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Lettuce 默认不开 TCP keepalive，到 Redis 的连接在长时间空闲后会被对端或中间网络设备静默关闭。
 * 连接被复用时才暴露为 {@code SocketException: Connection reset}，表现为「久未访问后第一次请求必失败、
 * 重试即成功」——短信发码链路上曾因此在 storePendingChallenge 处抛 500。
 *
 * <p>这里开启 SO_KEEPALIVE 并把探测周期压到分钟级，让空闲连接保持活性、失活连接被及时发现并重连。
 * idle/interval/count 三项需要 netty 原生传输（epoll）才完全生效，缺失时 Lettuce 自动退化为
 * 系统默认的基础 keepalive，不会启动失败。
 */
@Configuration
public class RedisKeepAliveConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer redisKeepAliveCustomizer() {
        SocketOptions socketOptions = SocketOptions.builder()
                .keepAlive(SocketOptions.KeepAliveOptions.builder()
                        .enable()
                        .idle(Duration.ofSeconds(60))
                        .interval(Duration.ofSeconds(15))
                        .count(3)
                        .build())
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .autoReconnect(true)
                .build();

        return builder -> builder.clientOptions(clientOptions);
    }
}
