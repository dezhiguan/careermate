package com.careermate.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.tracing")
public class CareerMateTracingProperties {

    private String serviceName = "careermate-backend";
}
