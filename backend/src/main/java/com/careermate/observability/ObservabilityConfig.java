package com.careermate.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(CareerMateTracingProperties.class)
public class ObservabilityConfig {

    @Bean
    public TracingMdcFilter tracingMdcFilter(
            TraceIdResolver traceIdResolver,
            @Value("${spring.application.name:careermate-backend}") String serviceName
    ) {
        return new TracingMdcFilter(traceIdResolver, serviceName);
    }

    @Bean
    public FilterRegistrationBean<TracingMdcFilter> tracingMdcFilterRegistration(TracingMdcFilter filter) {
        FilterRegistrationBean<TracingMdcFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
