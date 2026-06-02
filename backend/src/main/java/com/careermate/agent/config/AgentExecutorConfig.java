package com.careermate.agent.config;

import com.careermate.common.exception.BizException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentExecutorConfig {

    @Bean(name = "agentExecutor")
    public TaskExecutor agentExecutor(AgentProperties agentProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(agentProperties.getExecutorCorePoolSize());
        executor.setMaxPoolSize(agentProperties.getExecutorMaxPoolSize());
        executor.setQueueCapacity(agentProperties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("agent-executor-");
        executor.setRejectedExecutionHandler((r, e) -> {
            throw new BizException(429, "Agent executor 队列已满");
        });
        executor.initialize();
        return executor;
    }
}

