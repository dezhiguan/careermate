package com.careermate.observability;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties({CareerMateTracingProperties.class, RagForgeProperties.class})
public class ObservabilityConfig {

    @Bean
    public TracingMdcFilter tracingMdcFilter(Tracer tracer, TraceHeaderPropagator traceHeaderPropagator) {
        return new TracingMdcFilter(tracer, traceHeaderPropagator);
    }

    @Bean
    public FilterRegistrationBean<TracingMdcFilter> tracingMdcFilterRegistration(TracingMdcFilter filter) {
        FilterRegistrationBean<TracingMdcFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
